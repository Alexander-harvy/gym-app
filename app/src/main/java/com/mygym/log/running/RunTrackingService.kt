package com.mygym.log.running

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
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
    private var currentId: String?=null
    private var ticker: Job?=null
    private var lastFixElapsed=0L
    private data class Event(val action: String,val id: String?=null,val location: Location?=null)
    private val listener=object: LocationListener {
        override fun onLocationChanged(location: Location) { events.trySend(Event("fix",location=Location(location))) }
        override fun onProviderDisabled(provider: String) { events.trySend(Event("disabled")) }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    override fun onCreate() {
        super.onCreate()
        locations=getSystemService(LocationManager::class.java)
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
                finally { if(event.action !in listOf("tick","fix","disabled"))app.runCommandPending.value=false }
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
                if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                else startForeground(NOTIFICATION,notification)
            } catch(e: Exception) { app.runError.value="Android could not start location tracking: ${e.message}";app.runCommandPending.value=false;stopSelf();return START_NOT_STICKY }
        }
        events.trySend(Event(action,intent.getStringExtra("runId")))
        return START_NOT_STICKY
    }
    private suspend fun handle(event: Event) {
        val now=SystemClock.elapsedRealtime()
        when(event.action) {
            START,RESUME -> {
                val id=withContext(Dispatchers.IO) {
                    val chosen=if(event.action==START)repo.start(System.currentTimeMillis(),now) else event.id ?: error("Missing run.")
                    repo.resume(chosen,now);chosen
                }
                currentId=id;app.runError.value=null;lastFixElapsed=now
                locations.removeUpdates(listener)
                @Suppress("MissingPermission")
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000L,0f,listener,Looper.getMainLooper())
                ticker?.cancel();ticker=scope.launch { while(isActive){delay(5000);events.send(Event("tick"))} }
                refreshNotification()
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
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_run).setContentTitle("My Gym Log · Recording run")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0,"Pause",pause).build()
    }
    private fun shutdown() {
        currentId=null;ticker?.cancel();locations.removeUpdates(listener)
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy() {
        val interrupted=currentId
        currentId=null;ticker?.cancel();locations.removeUpdates(listener);scope.cancel()
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
        fun send(context: Context, action: String, id: String?=null) {
            val app=context.applicationContext as GymApplication
            if(!app.runCommandPending.compareAndSet(false,true))return
            val intent=Intent(context,RunTrackingService::class.java).setAction(action).putExtra("runId",id)
            try { if(action==START || action==RESUME)ContextCompat.startForegroundService(context,intent) else context.startService(intent) }
            catch(e: Exception){app.runCommandPending.value=false;throw e}
        }
    }
}
