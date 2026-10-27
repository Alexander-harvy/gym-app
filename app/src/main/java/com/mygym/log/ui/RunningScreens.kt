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
        Eyebrow(if(run.state=="FINISHED")if(run.activityType=="WALK")"Saved walk" else "Saved run" else run.state.lowercase().replaceFirstChar(Char::uppercase))
        Text("${number(run.distanceMeters/1000)} km",style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        if(run.activityType=="WALK") {
            Text("${run.steps} steps",style=MaterialTheme.typography.headlineSmall)
            Text(when(run.stepStatus){"COUNTING"->"Session steps · phone sensor";"DENIED"->"Steps unavailable · allow Physical activity, then resume";else->"Step sensor unavailable on this phone"},style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)){Text(runDuration(duration),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Active duration",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Column(Modifier.weight(1f)){Text(runPace(run.distanceMeters,duration),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Avg. min/km",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
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
    val activityLabel=if(run?.activityType=="WALK")"walk" else "run"
    val pointFlow=remember(run?.id){run?.let{vm.repo.dao.pointsForRun(it.id)} ?: flowOf(emptyList())}
    val points by pointFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    var elapsed by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(run?.state){while(true){elapsed=SystemClock.elapsedRealtime();delay(1000)}}
    var finish by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<Run?>(null)}
    var activityType by remember {mutableStateOf("RUN")}
    var requestedAction by remember{mutableStateOf(Tracker.START)}
    var requestedId by remember{mutableStateOf<String?>(null)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.runAction(requestedAction,requestedId,activityType)
    }
    val activityPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.runAction(requestedAction,requestedId,activityType)
    }
    fun begin() {
        if(!Tracker.gpsEnabled(context)){vm.runError.value="GPS is off. Enable location in your phone settings, then try again.";return}
        if((run?.activityType ?: activityType)=="WALK" && Build.VERSION.SDK_INT>=29 && ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
            activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        else if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.runAction(requestedAction,requestedId,activityType)
    }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(Tracker.hasPreciseLocation(context))begin()
        else vm.runError.value="Precise location is needed to measure your route. Allow precise location while using the app. You can change this in app permissions."
    }
    fun request(action: String,id: String?=null) {
        requestedAction=action;requestedId=id
        activityType=run?.activityType ?: activityType
        if(Tracker.hasPreciseLocation(context))begin()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    Page(if(detailId==null)"Run & walk" else if(run?.activityType=="WALK")"Walk details" else "Run details") {
        error?.let { Panel {
            Text(it,color=MaterialTheme.colorScheme.error)
            TextButton({context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}){Text("Location settings")}
            TextButton({context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("App permissions")}
        } }
        if(run!=null) {
            Text(date(run.startedAt),color=MaterialTheme.colorScheme.onSurfaceVariant)
            RunStats(run,elapsed)
            if(run.activeSlot==1) {
                Panel {
                    Text(run.status,style=MaterialTheme.typography.bodyMedium)
                    if(run.state=="RUNNING")Action("Pause $activityLabel",!busy){vm.runAction(Tracker.PAUSE,run.id)}
                    else Action("Resume $activityLabel",!busy){request(Tracker.RESUME,run.id)}
                    Quiet("Finish and save $activityLabel",!busy){finish=true}
                    TextButton({deleting=run},enabled=!busy){Text("Discard $activityLabel",color=MaterialTheme.colorScheme.error)}
                }
            }
            SectionTitle("Your route")
            StreetRouteView(points)
            Text("${points.size} GPS points · stored locally",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(run.state=="FINISHED")TextButton({deleting=run},enabled=!busy){Text("Delete saved $activityLabel",color=MaterialTheme.colorScheme.error)}
        } else if(detailId==null) {
            EmptyState("Ready when you are","Distance, time and pace. Stored on this device.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(activityType=="RUN",{activityType="RUN"},label={Text("Run")})
                FilterChip(activityType=="WALK",{activityType="WALK"},label={Text("Walk")})
            }
            Action(if(activityType=="WALK")"Start walk" else "Start run",!busy){request(Tracker.START)}
            StreetRouteView(emptyList())
        } else EmptyState("Run no longer available","This record may have been deleted or replaced by a backup restore.")
        if(detailId==null)OfflineMapControls(points)
        Disclosure("Run tips & permissions") {
            Text("Use precise location outdoors. A recording notification offers Pause. If notifications are blocked, use the app's controls.",style=MaterialTheme.typography.bodySmall)
            Quiet("Location settings",!busy){context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}
            TextButton({context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("App permissions")}
            Text("Average pace = unpaused time ÷ recorded distance. GPS waiting time counts. Pauses and signal gaps are not joined on the offline route; street maps work inside downloaded areas. Walking steps use your phone sensor during active sessions; carry your phone. Steps are not a daily total. Pinch to zoom or drag to pan the route. No background-location permission is requested.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(detailId==null) {
            SectionTitle("Run & walk history")
            val finished=data.runs.filter{it.state=="FINISHED"}.sortedByDescending{it.finishedAt}
            if(finished.isEmpty())EmptyState("Your next finish line","Finish and save a run to see its route and stats here.")
            var allRuns by remember { mutableStateOf(false) }
            (if(allRuns)finished else finished.take(3)).forEach { saved -> Panel {
                Row {
                    Column(Modifier.weight(1f)){Eyebrow("${if(saved.activityType=="WALK")"Walk" else "Run"} · ${date(saved.startedAt)}");Text("${number(saved.distanceMeters/1000)} km",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
                    MoreMenu("Run options for ${date(saved.startedAt)}",busy,listOf(MenuAction("Delete run",true){deleting=saved}))
                }
                if(saved.activityType=="WALK")Text("${saved.steps} steps")
                Text("${runDuration(saved.durationMs)} active · ${runPace(saved.distanceMeters,saved.durationMs)} min/km average")
                Quiet("View route and details",!busy){open(saved.id)}
            } }
            if(finished.size>3)TextButton({allRuns=!allRuns}){Text(if(allRuns)"Show recent runs" else "Show all ${finished.size} runs")}
        }
    }
    if(finish && run!=null)Confirm("Finish and save $activityLabel?",if(points.isEmpty())"No GPS points have been recorded. Active duration and available steps will be saved; distance and pace are unavailable." else "The route, distance, active duration and average pace will be kept locally. Nothing is uploaded.",busy,{finish=false},{vm.runAction(Tracker.FINISH,run.id);finish=false})
    deleting?.let { chosen -> Confirm(if(chosen.activeSlot==1)"Discard this run?" else "Delete saved run?","This permanently deletes the run and its GPS route points.",busy,{deleting=null},{
        if(chosen.activeSlot==1)vm.runAction(Tracker.DISCARD,chosen.id) else vm.act{vm.repo.runs.delete(chosen.id)}
        deleting=null
    }) }
}
