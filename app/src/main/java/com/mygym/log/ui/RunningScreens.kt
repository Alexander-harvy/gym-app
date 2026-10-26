@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mygym.log.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mygym.log.data.*
import com.mygym.log.domain.RunMath
import com.mygym.log.running.RunTrackingService as Tracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

fun runDuration(ms: Long): String {
    val seconds=ms.coerceAtLeast(0)/1000
    return if(seconds>=3600)String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
        else String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
}
fun runPace(meters: Double, ms: Long): String {
    val pace=RunMath.paceSecondsPerKm(meters,ms)?.toLong() ?: return "—"
    return String.format(Locale.US,"%d:%02d",pace/60,pace%60)
}

@Composable fun RunStats(run: Run, elapsedNow: Long) {
    val duration=RunMath.elapsed(run,elapsedNow)
    Panel {
        Eyebrow(if(run.state=="FINISHED")"Saved run" else run.state.lowercase().replaceFirstChar(Char::uppercase))
        Text("${number(run.distanceMeters/1000)} km",style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StatTile(runDuration(duration),"Active duration",Modifier.weight(1f))
            StatTile(runPace(run.distanceMeters,duration),"Avg. min/km",Modifier.weight(1f))
        }
        Text("Pace = active duration ÷ recorded distance. Pauses are excluded; GPS signal gaps remain in active time.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun RunningScreen(vm: GymViewModel,data: Snapshot,detailId: String?=null,open: (String)->Unit) {
    val context=LocalContext.current
    val pending by vm.runCommandPending.collectAsStateWithLifecycle()
    val saving by vm.busy.collectAsStateWithLifecycle()
    val error by vm.runError.collectAsStateWithLifecycle()
    val busy=pending || saving
    val active=data.runs.firstOrNull{it.activeSlot==1}
    val run=if(detailId==null)active else data.runs.find{it.id==detailId}
    val pointFlow=remember(run?.id){run?.let{vm.repo.dao.pointsForRun(it.id)} ?: flowOf(emptyList())}
    val points by pointFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    var elapsed by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(run?.state){while(true){elapsed=SystemClock.elapsedRealtime();delay(1000)}}
    var finish by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<Run?>(null)}
    var requestedAction by remember{mutableStateOf(Tracker.START)}
    var requestedId by remember{mutableStateOf<String?>(null)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.runAction(requestedAction,requestedId)
    }
    fun begin() {
        if(!Tracker.gpsEnabled(context)){vm.runError.value="GPS is off. Enable location in your phone settings, then try again.";return}
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.runAction(requestedAction,requestedId)
    }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(Tracker.hasPreciseLocation(context))begin()
        else vm.runError.value="Precise location is needed to measure your route. Allow precise location while using the app. You can change this in app permissions."
    }
    fun request(action: String,id: String?=null) {
        requestedAction=action;requestedId=id
        if(Tracker.hasPreciseLocation(context))begin()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    Page(if(detailId==null)"Go for a run" else "Run details","GPS routes and running records stay on this device.") {
        error?.let { Panel { Text(it,color=MaterialTheme.colorScheme.error) } }
        if(run!=null) {
            Text(date(run.startedAt),color=MaterialTheme.colorScheme.onSurfaceVariant)
            RunStats(run,elapsed)
            if(run.activeSlot==1) {
                Panel {
                    Text(run.status,style=MaterialTheme.typography.bodyMedium)
                    if(run.state=="RUNNING")Action("Pause run",!busy){vm.runAction(Tracker.PAUSE,run.id)}
                    else Action("Resume run",!busy){request(Tracker.RESUME,run.id)}
                    Quiet("Finish and save run",!busy){finish=true}
                    TextButton({deleting=run},enabled=!busy){Text("Discard run",color=MaterialTheme.colorScheme.error)}
                }
            }
            SectionTitle("Your route")
            RouteView(points)
            Text("${points.size} saved GPS points · route breaks show pauses or signal gaps.\nOffline route view; no street map or map downloads.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(run.state=="FINISHED")TextButton({deleting=run},enabled=!busy){Text("Delete saved run",color=MaterialTheme.colorScheme.error)}
        } else if(detailId==null) {
            EmptyState("Take your training outdoors","Track distance, active time and average pace. Start outdoors and allow precise location for an accurate GPS fix.")
            Action("Start run",!busy){request(Tracker.START)}
        } else EmptyState("Run no longer available","This record may have been deleted or replaced by a backup restore.")
        if(detailId==null || run?.activeSlot==1) {
            Quiet("Location settings",!busy){context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}
            TextButton({context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("App permissions")}
            Text("Keep precise location enabled while using the app. During recording, a foreground notification offers Pause and reopens this screen. If notification permission is denied, controls remain in the app. No background-location permission is requested.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(detailId==null) {
            SectionTitle("Run history")
            val finished=data.runs.filter{it.state=="FINISHED"}.sortedByDescending{it.finishedAt}
            if(finished.isEmpty())EmptyState("Your next finish line","Finish and save a run to see its route and stats here.")
            finished.forEach { saved -> Panel {
                Row {
                    Column(Modifier.weight(1f)){Eyebrow(date(saved.startedAt));Text("${number(saved.distanceMeters/1000)} km",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
                    MoreMenu("Run options for ${date(saved.startedAt)}",busy,listOf(MenuAction("Delete run",true){deleting=saved}))
                }
                Text("${runDuration(saved.durationMs)} active · ${runPace(saved.distanceMeters,saved.durationMs)} min/km average")
                Quiet("View route and details",!busy){open(saved.id)}
            } }
        }
    }
    if(finish && run!=null)Confirm("Finish and save run?",if(points.isEmpty())"No GPS points have been recorded. Only active duration will be saved; distance and pace are unavailable." else "The route, distance, active duration and average pace will be kept locally. Nothing is uploaded.",busy,{finish=false},{vm.runAction(Tracker.FINISH,run.id);finish=false})
    deleting?.let { chosen -> Confirm(if(chosen.activeSlot==1)"Discard this run?" else "Delete saved run?","This permanently deletes the run and its GPS route points.",busy,{deleting=null},{
        if(chosen.activeSlot==1)vm.runAction(Tracker.DISCARD,chosen.id) else vm.act{vm.repo.runs.delete(chosen.id)}
        deleting=null
    }) }
}
