package com.mygym.log.running

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.hardware.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mygym.log.GymApplication
import com.mygym.log.MainActivity
import com.mygym.log.R
import com.mygym.log.data.RunPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.Locale

class RunTrackingService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val events=Channel<Event>(Channel.UNLIMITED)
    private val app get()=application as GymApplication
    private val repo get()=app.repository.runs
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private val stepCounter=StepAccumulator()
    private var sensorStarted=0L
    private val stepListener=object: SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int)=Unit
        override fun onSensorChanged(event: SensorEvent) {
            if(event.timestamp/1_000_000 < sensorStarted || event.values.isEmpty() || !event.values[0].isFinite() || event.values[0]<0)return
            val delta=if(event.sensor.type==Sensor.TYPE_STEP_COUNTER)stepCounter.count(event.values[0].toLong()) else 1L
            if(delta>0)events.trySend(Event("steps",id=currentId,steps=delta))
        }
    }
    private var currentId: String?=null
    private var ticker: Job?=null
    private var lastFixElapsed=0L
    private data class Event(val action: String,val id: String?=null,val location: Location?=null,val mode: String="RUN",val steps: Long=0)
    private val listener=object: LocationListener {
        override fun onLocationChanged(location: Location) { events.trySend(Event("fix",location=Location(location))) }
        override fun onProviderDisabled(provider: String) { events.trySend(Event("disabled")) }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    override fun onCreate() {
        super.onCreate()
        locations=getSystemService(LocationManager::class.java)
        sensors=getSystemService(SensorManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Run tracking",NotificationManager.IMPORTANCE_LOW).apply { description="Controls for a run you started";setSound(null,null) })
        scope.launch {
            app.ready.await()
            for(event in events) {
                try { handle(event) }
                catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    app.runError.value=e.message ?: "Unable to record the run. Saved points have been kept."
                    val id=currentId
                    if(id!=null) withContext(Dispatchers.IO){repo.pause(id,SystemClock.elapsedRealtime(),"Tracking stopped. Resume when ready.")}
                    shutdown()
                }
                finally { if(event.action !in listOf("tick","fix","disabled","steps"))app.runCommandPending.value=false }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action=intent?.action ?: return START_NOT_STICKY
        if(action==START || action==RESUME) {
            if(!hasPreciseLocation(this) || !gpsEnabled(this)) {
                app.runError.value="Enable GPS and allow precise location while using the app before starting a run."
                app.runCommandPending.value=false
                stopSelf();return START_NOT_STICKY
            }
            try {
                val notification=notification("Starting GPS…")
                var types=if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                if(Build.VERSION.SDK_INT>=34 && intent.getStringExtra("activityType")=="WALK" && ContextCompat.checkSelfPermission(this,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED)types=types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification,types)
                else startForeground(NOTIFICATION,notification)
            } catch(e: Exception) { app.runError.value="Android could not start location tracking: ${e.message}";app.runCommandPending.value=false;stopSelf();return START_NOT_STICKY }
        }
        events.trySend(Event(action,intent.getStringExtra("runId"),mode=intent.getStringExtra("activityType") ?: "RUN"))
        return START_NOT_STICKY
    }
    private suspend fun handle(event: Event) {
        val now=SystemClock.elapsedRealtime()
        when(event.action) {
            START,RESUME -> {
                val id=withContext(Dispatchers.IO) {
                    val chosen=if(event.action==START)repo.start(System.currentTimeMillis(),now,event.mode) else event.id ?: error("Missing run.")
                    repo.resume(chosen,now);chosen
                }
                currentId=id
                startSteps(id,now)
                app.runError.value=null;lastFixElapsed=now
                locations.removeUpdates(listener)
                @Suppress("MissingPermission")
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,0f,listener,Looper.getMainLooper())
                ticker?.cancel();ticker=scope.launch { while(isActive){delay(5000);events.send(Event("tick"))} }
                refreshNotification()
            }
            "steps" -> {
                if(event.id!=null && event.id==currentId)withContext(Dispatchers.IO){repo.steps(event.id,event.steps,"COUNTING")}
            }
            "fix" -> {
                val id=currentId ?: return
                val fix=event.location ?: return
                if(!fix.hasAccuracy()) return
                val point=RunPoint(runId=id,sequence=0,segment=0,latitude=fix.latitude,longitude=fix.longitude,
                    accuracyMeters=fix.accuracy.toDouble(),timestamp=fix.time,elapsedRealtimeMs=fix.elapsedRealtimeNanos/1_000_000)
                val accepted=withContext(Dispatchers.IO){repo.record(id,point,now)}
                if(accepted)lastFixElapsed=now
            }
            "tick" -> {
                val id=currentId ?: return
                withContext(Dispatchers.IO) {
                    repo.checkpoint(id,now)
                    if(now-lastFixElapsed>20000) repo.status(id,"Waiting for GPS or movement. Poor fixes are ignored; time is still counting.")
                }
                refreshNotification()
            }
            PAUSE,"disabled" -> {
                val id=currentId ?: event.id
                if(id!=null) withContext(Dispatchers.IO){repo.pause(id,now,if(event.action=="disabled")"GPS disabled. Enable location, then resume." else "Paused")}
                shutdown()
            }
            FINISH -> {
                val id=currentId ?: event.id ?: return
                withContext(Dispatchers.IO){repo.finish(id,System.currentTimeMillis(),now)}
                shutdown()
            }
            DISCARD -> {
                val id=currentId ?: event.id ?: return
                withContext(Dispatchers.IO){repo.delete(id)}
                shutdown()
            }
        }
    }
    private suspend fun refreshNotification() {
        val run=withContext(Dispatchers.IO){currentId?.let{app.repository.dao.run(it)}} ?: return
        if(run.state!="RUNNING") { shutdown();return }
        val minutes=run.durationMs/60000
        val text=String.format(Locale.US,"%.2f km · %d min · %s",run.distanceMeters/1000,minutes,run.status)
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(text))
    }
    private fun notification(text: String): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("open_running",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause=PendingIntent.getService(this,1,Intent(this,RunTrackingService::class.java).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_run).setContentTitle("Gym · Recording activity")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0,"Pause",pause).build()
    }
    private suspend fun startSteps(id: String,now: Long) {
        sensors.unregisterListener(stepListener);stepCounter.reset();sensorStarted=now
        val run=withContext(Dispatchers.IO){app.repository.dao.run(id)} ?: return
        if(run.activityType!="WALK")return
        val allowed=Build.VERSION.SDK_INT<29 || ContextCompat.checkSelfPermission(this,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val status=if(!allowed)"DENIED" else if(sensor==null)"UNAVAILABLE" else {
            try {if(sensors.registerListener(stepListener,sensor,SensorManager.SENSOR_DELAY_NORMAL))"COUNTING" else "UNAVAILABLE"} catch(_: SecurityException) {"DENIED"}
        }
        withContext(Dispatchers.IO){repo.steps(id,0,status)}
    }
    private fun shutdown() {
        currentId=null;ticker?.cancel();locations.removeUpdates(listener);sensors.unregisterListener(stepListener);stepCounter.reset()
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() {
        val interrupted=currentId
        currentId=null;ticker?.cancel();locations.removeUpdates(listener);sensors.unregisterListener(stepListener);stepCounter.reset();scope.cancel()
        app.runCommandPending.value=false
        if(interrupted!=null)app.applicationScope.launch{repo.pause(interrupted,SystemClock.elapsedRealtime(),"Tracking interrupted. Resume when ready.")}
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder?=null
    companion object {
        const val START="com.mygym.log.START_RUN";const val RESUME="com.mygym.log.RESUME_RUN"
        const val PAUSE="com.mygym.log.PAUSE_RUN";const val FINISH="com.mygym.log.FINISH_RUN";const val DISCARD="com.mygym.log.DISCARD_RUN"
        private const val CHANNEL="run-tracking";private const val NOTIFICATION=42
        fun hasPreciseLocation(context: Context)=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        fun gpsEnabled(context: Context)=context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)
        fun send(context: Context, action: String, id: String?=null,activityType: String="RUN") {
            val app=context.applicationContext as GymApplication
            if(!app.runCommandPending.compareAndSet(false,true))return
            val intent=Intent(context,RunTrackingService::class.java).setAction(action).putExtra("runId",id).putExtra("activityType",activityType)
            try { if(action==START || action==RESUME)ContextCompat.startForegroundService(context,intent) else context.startService(intent) }
            catch(e: Exception){app.runCommandPending.value=false;throw e}
        }
    }
}
