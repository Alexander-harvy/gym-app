@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mygym.log.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mygym.log.BuildConfig
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.mygym.log.data.*
import com.mygym.log.domain.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun date(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm"))
fun number(value: Double): String = String.format(Locale.US,"%.2f",value).trimEnd('0').trimEnd('.')
val LocalSaveError = staticCompositionLocalOf<String?> { null }

@Composable
fun GymApp(vm: GymViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backup by vm.pendingBackup.collectAsStateWithLifecycle()
    val dark=when(prefs.theme) { "LIGHT"->false; "SYSTEM"->isSystemInDarkTheme(); else->true }
    val view=LocalView.current
    val activity=LocalActivity.current
    SideEffect { activity?.window?.let { window ->
        WindowCompat.getInsetsController(window,view).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark }
    } }
    GymTheme(dark) {
        val nav=rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val snackbar=remember { SnackbarHostState() }
        val route=entry?.destination?.route ?: "home"
        val detail=route in listOf("active", "routine/{id}", "workout/{id}")
        val activeWorkout=data.workouts.firstOrNull{it.activeSlot==1}
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
        CompositionLocalProvider(LocalSaveError provides message) {
        Scaffold(snackbarHost={SnackbarHost(snackbar)}, topBar={
            TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                navigationIcon={if(detail || route in listOf("settings","exercises")) IconButton({nav.popBackStack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Go back")}},
                title={Column { Text("MY GYM LOG",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black);Text("YOUR TRAINING JOURNAL",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }},
                actions={
                    if(busy) CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp),strokeWidth=2.dp)
                    IconButton({nav.navigate("exercises"){launchSingleTop=true}}){Icon(Icons.AutoMirrored.Filled.List,"Exercise library",tint=if(route=="exercises")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton({nav.navigate("settings"){launchSingleTop=true}}){Icon(Icons.Default.Settings,"Settings",tint=if(route=="settings")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                })
        },bottomBar={Column(if(detail)Modifier.navigationBarsPadding() else Modifier) {
            if(activeWorkout!=null && route!="active") Surface(color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text("WORKOUT IN PROGRESS",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer);Text(activeWorkout.name,style=MaterialTheme.typography.titleSmall,maxLines=1)}
                    TextButton({nav.navigate("active"){launchSingleTop=true}}){Text("Resume")}
                }
            }
            if(!detail) NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
                listOf(Triple("home","Home",Icons.Default.Home),Triple("routines","Routines",Icons.AutoMirrored.Filled.List),Triple("history","History",Icons.Default.DateRange),Triple("progress","Progress",Icons.Default.Star)).forEach { (destination,label,icon) ->
                    NavigationBarItem(selected=route==destination,onClick={nav.navigate(destination){launchSingleTop=true;restoreState=true;popUpTo("home"){saveState=true}}},
                        icon={Icon(icon,null)},label={Text(label)},colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }}) { padding ->
            NavHost(nav,"home",Modifier.padding(padding)) {
                composable("home") { Home(data,busy,{vm.act { vm.repo.start() }; nav.navigate("active")},{nav.navigate("active")},{nav.navigate("routines")},{nav.navigate("history")}) }
                composable("exercises") { Exercises(vm,data,busy) }
                composable("routines") { Routines(vm,data,prefs,busy,{nav.navigate("routine/$it")},{nav.navigate("active")}) }
                composable("routine/{id}") { back -> RoutineEditor(vm,data,prefs,back.arguments?.getString("id")!!,busy) }
                composable("active") { WorkoutScreen(vm,data,prefs,data.workouts.firstOrNull{it.activeSlot==1}?.id,busy,{nav.navigate("home"){popUpTo("home"){inclusive=true}}}) }
                composable("history") { History(vm,data,busy,{nav.navigate("workout/$it")}) }
                composable("workout/{id}") { back -> WorkoutScreen(vm,data,prefs,back.arguments?.getString("id"),busy,{nav.popBackStack()}) }
                composable("progress") { Progress(data,prefs) }
                composable("settings") { Settings(vm,prefs,busy) }
            }
        }
        backup?.let { b -> Confirm("Replace all local records?", "This backup contains ${b.data.workouts.size} workouts and ${b.data.exercises.size} exercises. Restore replaces all current records and settings, including an unfinished workout. Export a backup first if you want to keep them.", busy,
            {vm.pendingBackup.value=null}, {vm.restore()}) }
        }
    }
}

@Composable
fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp).widthIn(max=720.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        subtitle?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium) }
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable fun Panel(content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) } }
@Composable fun Action(label: String, enabled: Boolean=true, onClick: ()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=16.dp)) { Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold) } }
@Composable fun Quiet(label: String, enabled: Boolean=true, onClick: ()->Unit) { OutlinedButton(onClick,Modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(14.dp)) { Text(label) } }
@Composable fun Confirm(title: String, text: String, busy: Boolean, dismiss: ()->Unit, accept: ()->Unit) {
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text(title)},text={Text(text)},confirmButton={TextButton(accept,enabled=!busy){Text("Confirm")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Cancel")}})
}
@Composable fun Form(title: String, busy: Boolean, dismiss: ()->Unit, save: ()->Unit, content: @Composable ColumnScope.()->Unit) {
    val error=LocalSaveError.current
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        content()
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},
        confirmButton={TextButton(save,enabled=!busy){Text("Save")}},dismissButton={TextButton(dismiss,enabled=!busy){Text("Cancel")}})
}
@Composable fun Input(label: String, value: String, onChange: (String)->Unit, numeric: Boolean=false, multiline: Boolean=false) {
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=!multiline,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable fun Choices(values: List<String>, selected: String, select: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { FilterChip(selected==it,{select(it)},label={Text(it.lowercase().replaceFirstChar(Char::uppercase))}) } }
}
@Composable fun Toggle(label: String, checked: Boolean, enabled: Boolean=true, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,enabled=enabled,role=Role.Checkbox,onValueChange=change),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked,onCheckedChange=null,enabled=enabled)
        Text(label,Modifier.padding(start=12.dp))
    }
}
@Composable fun OrderActions(up: ()->Unit, down: ()->Unit, busy: Boolean) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Quiet("Move up",!busy,up); Quiet("Move down",!busy,down) } }

@Composable
fun Home(data: Snapshot,busy: Boolean,start: ()->Unit,resume: ()->Unit,routines: ()->Unit,history: ()->Unit) {
    val active=data.workouts.firstOrNull{it.activeSlot==1}
    val completed=data.workouts.filter{it.finishedAt!=null}.sortedByDescending{it.finishedAt}
    val monday=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Page("Let's get stronger.",LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))) {
        Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.primaryContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.3f))) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Eyebrow(if(active==null) "Your next session" else "Pick up where you left off")
                Text(active?.name ?: "Show up.\nGet stronger.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)
                Text(active?.let{"Started ${date(it.startedAt)}"} ?: "One session at a time. Start fresh or follow your plan.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Action(if(active==null) "Start workout" else "Resume workout",!busy,if(active==null)start else resume)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StatTile((Metrics.weekly(data)[monday] ?: 0).toString(),"This week",Modifier.weight(1f))
            StatTile(completed.size.toString(),"Total workouts",Modifier.weight(1f))
        }
        SectionTitle("Your routines","View all",routines)
        if(data.routines.isEmpty()) {
            EmptyState("Make your next session easier","Build a routine with your go-to exercises, sets, and reps.")
            Quiet("Create a routine",onClick=routines)
        } else data.routines.take(2).forEach { r ->
            Card(onClick=routines,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.List,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(r.name,fontWeight=FontWeight.Bold);Text("${data.routineExercises.count{it.routineId==r.id}} exercises",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null)
                }
            }
        }
        SectionTitle("Last session","History",history)
        completed.firstOrNull()?.let { w -> Panel {
            Eyebrow(date(w.finishedAt!!))
            Text(w.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            val ids=data.workoutExercises.filter{it.workoutId==w.id}.map{it.id}.toSet()
            Text("${ids.size} exercises · ${data.sets.count{it.workoutExerciseId in ids && it.completed}} completed sets",color=MaterialTheme.colorScheme.onSurfaceVariant)
        } } ?: EmptyState("Your story starts here","Finish a workout to see your latest session.",Icons.Default.DateRange)
        Text("Saved on your device. Back up your records in Settings.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Exercises(vm: GymViewModel,data: Snapshot,busy: Boolean) {
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var show by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Page("Exercise library","Your movements, all in one place.") {
        Input("Search name, muscle, or equipment",query,{query=it})
        Action("Add exercise",!busy){editing=null;show=true}
        Toggle("Show archived exercises",showArchived){showArchived=it}
        val visible=data.exercises.filter{(showArchived || !it.archived) && listOf(it.name,it.muscle,it.equipment).any{text->text.contains(query,true)}}
        visible.forEach { e -> Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text(e.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("${e.muscle} · ${e.equipment}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                MoreMenu("Options for ${e.name}",busy,listOf(MenuAction("Edit exercise"){editing=e;show=true},MenuAction(if(e.archived)"Unarchive" else "Archive"){vm.act{vm.repo.exercise(e.copy(archived=!e.archived))}}))
            }
            Badge(if(e.archived)"Archived" else if(e.tracking=="BODYWEIGHT")"Bodyweight" else "Weighted")
        } }
        if(visible.isEmpty()) EmptyState("No exercises found",if(query.isBlank())"Add your first exercise to start logging sets." else "Try a different name, muscle group, or piece of equipment.")
    }
    if(show) ExerciseForm(editing,busy,{show=false}) { e -> vm.act { vm.repo.exercise(e); show=false } }
}
@Composable
fun ExerciseForm(e: Exercise?,busy: Boolean,dismiss: ()->Unit,save: (Exercise)->Unit) {
    var name by remember { mutableStateOf(e?.name ?: "") };var muscle by remember { mutableStateOf(e?.muscle ?: "") };var equipment by remember { mutableStateOf(e?.equipment ?: "") };var tracking by remember{mutableStateOf(e?.tracking ?: "WEIGHTED")}
    Form(if(e==null) "New exercise" else "Edit exercise",busy,dismiss,{save(Exercise(e?.id ?: newId(),name.trim(),muscle.trim(),equipment.trim(),tracking,e?.archived ?: false))}) {
        Input("Name",name,{name=it});Input("Muscle group",muscle,{muscle=it});Input("Equipment",equipment,{equipment=it});Choices(listOf("WEIGHTED","BODYWEIGHT"),tracking,{tracking=it})
        Text("Bodyweight sets accept 0 external weight. Added load is recorded separately from your body weight.")
    }
}
@Composable
fun ExercisePicker(data: Snapshot,dismiss: ()->Unit,pick: (Exercise)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest=dismiss,title={Text("Choose exercise")},text={Column {
        Input("Search exercises",query,{query=it})
        LazyColumn(Modifier.heightIn(max=350.dp)) { items(data.exercises.filter{!it.archived && it.name.contains(query,true)},key={it.id}) { e -> TextButton({pick(e)},Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("${e.name} · ${e.equipment}") } } }
        if(data.exercises.none{!it.archived}) Text("Add an exercise in the Exercises tab first.")
    }},confirmButton={TextButton(dismiss){Text("Close")}})
}

@Composable
fun Routines(vm: GymViewModel,data: Snapshot,prefs: Preferences,busy: Boolean,edit: (String)->Unit,active: ()->Unit) {
    var add by remember { mutableStateOf(false) };var name by rememberSaveable{mutableStateOf("")};var deleting by remember{mutableStateOf<Routine?>(null)}
    Page("Your routines","Less planning. More training.") {
        Action("Create routine",!busy){name="";add=true}
        if(data.routines.isEmpty()) EmptyState("Build your first plan","Add your favorite exercises and set your targets. Your workout history stays independent.")
        data.routines.forEach { r -> Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(r.name,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                MoreMenu("Options for ${r.name}",busy,listOf(MenuAction("Edit routine"){edit(r.id)},MenuAction("Move up"){vm.act{vm.repo.move("routine",r.id,-1)}},MenuAction("Move down"){vm.act{vm.repo.move("routine",r.id,1)}},MenuAction("Delete routine",true){deleting=r}))
            }
            val targets=data.routineExercises.filter{it.routineId==r.id}
            Badge("${targets.size} exercises · ${targets.sumOf{it.targetSets}} sets")
            if(targets.isNotEmpty()) Text(targets.take(3).joinToString(" · "){row->data.exercises.find{it.id==row.exerciseId}?.name ?: "Exercise"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Action(if(data.workouts.any{it.activeSlot==1}) "Resume active workout" else "Start routine",!busy){vm.act{vm.repo.start(r.id)};active()}
            Quiet("Edit routine",!busy){edit(r.id)}
        } }
    }
    if(add) Form("Create routine",busy,{add=false},{vm.act{vm.repo.routine(Routine(name=name.trim(),position=(data.routines.maxOfOrNull{it.position} ?: -1)+1));add=false}}){Input("Routine name",name,{name=it})}
    deleting?.let { r -> Confirm("Delete ${r.name}?","Existing workouts will be kept.",busy,{deleting=null},{vm.act{vm.repo.dao.deleteRoutine(r.id);deleting=null}}) }
}

@Composable
fun RoutineEditor(vm: GymViewModel,data: Snapshot,prefs: Preferences,id: String,busy: Boolean) {
    val r=data.routines.find{it.id==id} ?: return
    var rename by remember{mutableStateOf(false)};var name by remember(r.name){mutableStateOf(r.name)};var picker by remember{mutableStateOf(false)};var target by remember{mutableStateOf<RoutineExercise?>(null)}
    Page(r.name,"Targets are copied when you start a workout.") {
        Quiet("Rename routine",!busy){rename=true}
        Action("Add exercise",!busy){picker=true}
        val rows=data.routineExercises.filter{it.routineId==id}
        if(rows.isEmpty()) Text("Choose an exercise to set up your routine.")
        rows.forEach { row -> Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(data.exercises.find{it.id==row.exerciseId}?.name ?: "Exercise",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                MoreMenu("Routine exercise options",busy,listOf(MenuAction("Move up"){vm.act{vm.repo.move("target",row.id,-1)}},MenuAction("Move down"){vm.act{vm.repo.move("target",row.id,1)}},MenuAction("Remove from routine",true){vm.act{vm.repo.dao.deleteRoutineExercise(row.id)}}))
            }
            Badge("${row.targetSets} sets × ${row.targetReps} reps · ${row.restSeconds}s rest")
            Quiet("Edit targets",!busy){target=row}
        } }
    }
    if(rename) Form("Rename routine",busy,{rename=false},{vm.act{vm.repo.routine(r.copy(name=name.trim()));rename=false}}){Input("Name",name,{name=it})}
    if(picker) ExercisePicker(data,{picker=false}) { e -> vm.act{vm.repo.target(RoutineExercise(routineId=id,exerciseId=e.id,position=(data.routineExercises.filter{it.routineId==id}.maxOfOrNull{it.position} ?: -1)+1,restSeconds=prefs.restSeconds));picker=false} }
    target?.let { row -> TargetForm(row,busy,{target=null}){updated->vm.act{vm.repo.target(updated);target=null}} }
}
@Composable
fun TargetForm(row: RoutineExercise,busy: Boolean,dismiss: ()->Unit,save: (RoutineExercise)->Unit) {
    var sets by remember{mutableStateOf(row.targetSets.toString())};var reps by remember{mutableStateOf(row.targetReps.toString())};var rest by remember{mutableStateOf(row.restSeconds.toString())}
    Form("Routine targets",busy,dismiss,{save(row.copy(targetSets=sets.toIntOrNull() ?: 0,targetReps=reps.toIntOrNull() ?: 0,restSeconds=rest.toIntOrNull() ?: 0))}) {
        Input("Sets (1–50)",sets,{sets=it},true);Input("Reps (1–1,000)",reps,{reps=it},true);Input("Rest seconds (1–3,600)",rest,{rest=it},true)
    }
}

@Composable
fun WorkoutScreen(vm: GymViewModel,data: Snapshot,prefs: Preferences,id: String?,busy: Boolean,exit: ()->Unit) {
    val w=data.workouts.find{it.id==id}
    if(w==null) { Page("No workout open") { Text("Start a workout from Home or select a finished workout from History.");Quiet("Back",onClick=exit) };return }
    var picker by remember{mutableStateOf(false)};var editSet by remember{mutableStateOf<WorkoutSet?>(null)};var editWorkout by remember{mutableStateOf(false)}
    var noteExercise by remember{mutableStateOf<WorkoutExercise?>(null)};var finish by remember{mutableStateOf(false)};var discard by remember{mutableStateOf(false)};var removeExercise by remember{mutableStateOf<WorkoutExercise?>(null)}
    Page(w.name,if(w.finishedAt==null) "In progress · ${date(w.startedAt)}" else "Finished ${date(w.finishedAt)} · edits update progress") {
        val exercises=data.workoutExercises.filter{it.workoutId==w.id}
        val exerciseIds=exercises.map{it.id}.toSet()
        val sets=data.sets.filter{it.workoutExerciseId in exerciseIds}
        val done=sets.count{it.completed}
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Eyebrow(if(w.activeSlot==1)"Current session" else "Session summary");Text("$done of ${sets.size} sets complete",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
                MoreMenu("Workout options",busy,buildList {
                    add(MenuAction("Edit name and workout notes"){editWorkout=true})
                    if(w.activeSlot==1) add(MenuAction("Discard workout",true){discard=true})
                })
            }
            LinearProgressIndicator(progress={if(sets.isEmpty())0f else done.toFloat()/sets.size},modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),trackColor=MaterialTheme.colorScheme.background)
            Text("${exercises.size} exercises · tap a set to edit, check to complete",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(w.notes.isNotBlank()) Text(w.notes)
        if(w.activeSlot==1) Timer(w,busy,{action->vm.act{vm.repo.timer(w.id,action)}})
        if(exercises.isEmpty()) EmptyState("Your session starts here","Add an exercise, then enter the weight and reps for each set.")
        exercises.forEach { e -> Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(e.nameSnapshot,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                MoreMenu("Options for ${e.nameSnapshot}",busy,listOf(MenuAction("Exercise notes"){noteExercise=e},MenuAction("Move up"){vm.act{vm.repo.move("exercise",e.id,-1)}},MenuAction("Move down"){vm.act{vm.repo.move("exercise",e.id,1)}},MenuAction("Remove exercise",true){removeExercise=e}))
            }
            val previous=data.workouts.filter{it.finishedAt!=null && it.id!=w.id && it.finishedAt<=w.startedAt}.sortedByDescending{it.finishedAt}.firstOrNull { old ->
                data.workoutExercises.any{it.workoutId==old.id && it.exerciseId==e.exerciseId && data.sets.any{s->s.workoutExerciseId==it.id && s.completed}}
            }
            val prevExercises=data.workoutExercises.filter{it.workoutId==previous?.id && it.exerciseId==e.exerciseId}.map{it.id}.toSet()
            val prevSets=data.sets.filter{it.workoutExerciseId in prevExercises && it.completed}
            Text(if(previous==null) "First session with this exercise" else "LAST SESSION · ${date(previous.finishedAt!!)}\n"+prevSets.joinToString(" · "){"${number(Weight.display(it.weightKg,prefs.unit))} ${prefs.unit} × ${it.reps}${if(it.warmup) " warm-up" else ""}"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(e.trackingSnapshot=="BODYWEIGHT") Text("External load only; 0 = bodyweight",style=MaterialTheme.typography.bodySmall)
            data.sets.filter{it.workoutExerciseId==e.id}.forEachIndexed { index,s ->
                SetRow(index+1,s,prefs,busy,{editSet=s}){complete->vm.act{vm.repo.saveSet(s.copy(completed=complete))}}
            }
            Quiet("Add set",!busy){vm.act{vm.repo.addSet(e)}}
            if(e.notes.isNotBlank()) Text(e.notes)
        } }
        Action("Add exercise",!busy){picker=true}
        if(w.activeSlot==1) { Action("Finish workout",!busy){finish=true} }
    }
    if(picker) ExercisePicker(data,{picker=false}){e->vm.act{vm.repo.addExercise(w.id,e);picker=false}}
    editSet?.let { s -> SetForm(s,prefs,busy,{editSet=null},{updated->vm.act{vm.repo.saveSet(updated);editSet=null}},{vm.act{vm.repo.dao.deleteSet(s.id);editSet=null}}) }
    if(editWorkout) WorkoutForm(w,busy,{editWorkout=false}){name,notes->vm.act{vm.repo.workoutText(w.id,name,notes);editWorkout=false}}
    noteExercise?.let { e -> NotesForm(e.notes,busy,{noteExercise=null}){notes->vm.act{vm.repo.exerciseNotes(e.id,notes);noteExercise=null}} }
    if(finish) Confirm("Finish workout?","Completed sets will appear in progress. Incomplete and warm-up sets are excluded from progress metrics.",busy,{finish=false},{vm.act{vm.repo.finish(w.id);finish=false}})
    if(discard) Confirm("Discard this workout?","This permanently deletes this workout and all its sets.",busy,{discard=false},{vm.act{vm.repo.dao.deleteWorkout(w.id);discard=false}})
    removeExercise?.let { e -> Confirm("Remove ${e.nameSnapshot}?","All sets and notes for this exercise in this workout will be deleted.",busy,{removeExercise=null},{vm.act{vm.repo.dao.deleteWorkoutExercise(e.id);removeExercise=null}}) }
}

@Composable
fun SetForm(s: WorkoutSet,prefs: Preferences,busy: Boolean,dismiss: ()->Unit,save: (WorkoutSet)->Unit,delete: ()->Unit) {
    var weight by remember{mutableStateOf(number(Weight.display(s.weightKg,prefs.unit)))};var reps by remember{mutableStateOf(s.reps.toString())};var warmup by remember{mutableStateOf(s.warmup)};var complete by remember{mutableStateOf(s.completed)};var deleting by remember{mutableStateOf(false)}
    Form("Edit set",busy,dismiss,{val kg=if(weight==number(Weight.display(s.weightKg,prefs.unit)))s.weightKg else Weight.canonical(weight.toDoubleOrNull() ?: Double.NaN,prefs.unit);save(s.copy(weightKg=kg,reps=reps.toIntOrNull() ?: 0,warmup=warmup,completed=complete))}) {
        Input("External weight (${prefs.unit})",weight,{weight=it},true);Input("Repetitions",reps,{reps=it},true)
        Toggle("Warm-up set",warmup){warmup=it}
        Toggle("Completed",complete){complete=it}
        Text("Tap Save to persist this set.",style=MaterialTheme.typography.bodySmall)
        Quiet("Delete set",!busy){deleting=true}
    }
    if(deleting) Confirm("Delete set?","This permanently removes this set.",busy,{deleting=false},delete)
}
@Composable
fun WorkoutForm(w: Workout,busy: Boolean,dismiss: ()->Unit,save: (String,String)->Unit) {
    var name by remember{mutableStateOf(w.name)};var notes by remember{mutableStateOf(w.notes)}
    Form("Workout details",busy,dismiss,{save(name.trim(),notes)}){Input("Workout name",name,{name=it});Input("Notes",notes,{notes=it},multiline=true)}
}
@Composable fun NotesForm(initial: String,busy: Boolean,dismiss: ()->Unit,save: (String)->Unit) {
    var notes by remember{mutableStateOf(initial)};Form("Exercise notes",busy,dismiss,{save(notes)}){Input("Notes",notes,{notes=it},multiline=true)}
}
@Composable
fun Timer(w: Workout,busy: Boolean,action: (String)->Unit) {
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(w.timerEnd) { while(true){now=System.currentTimeMillis();delay(250)} }
    val seconds=(Metrics.remaining(w,now)+999)/1000
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Rest timer")
                Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                Text("${w.timerDuration/1000}s · ${if(w.timerEnd!=null && seconds>0) "Running" else if(seconds>0) "Ready / paused" else "Ready for your next set"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val running=w.timerEnd!=null && seconds>0
            FilledTonalButton({action(if(running)"pause" else if(seconds>0)"resume" else "start")},enabled=!busy,shape=RoundedCornerShape(14.dp)) { Text(if(running)"Pause" else if(seconds>0)"Resume" else "Start") }
        }
        LinearProgressIndicator(progress={(Metrics.remaining(w,now).toFloat()/w.timerDuration).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp),trackColor=MaterialTheme.colorScheme.background)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton({action("start")},enabled=!busy){Text("Restart")}
            TextButton({action("reset")},enabled=!busy){Text("Reset")}
            TextButton({action("skip")},enabled=!busy){Text("Skip rest")}
        }
        Text("Timer stays accurate in the background. No sound or notifications.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun History(vm: GymViewModel,data: Snapshot,busy: Boolean,open: (String)->Unit) {
    var deleting by remember{mutableStateOf<Workout?>(null)}
    Page("Your history","Every session is a step forward.") {
        val rows=data.workouts.filter{it.finishedAt!=null}.sortedByDescending{it.finishedAt}
        if(rows.isEmpty()) EmptyState("A fresh page","Finish your first workout and it will appear here.",Icons.Default.DateRange)
        rows.forEach { w -> Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Eyebrow(date(w.finishedAt!!));Text(w.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
                MoreMenu("Options for ${w.name}",busy,listOf(MenuAction("Delete workout",true){deleting=w}))
            }
            val ids=data.workoutExercises.filter{it.workoutId==w.id}.map{it.id}.toSet()
            Text("${ids.size} exercises · ${data.sets.count{it.workoutExerciseId in ids && it.completed}} completed sets",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Quiet("View session",!busy){open(w.id)}
        } }
    }
    deleting?.let { w -> Confirm("Delete ${w.name}?","All exercises, notes, and sets in this workout will be permanently deleted.",busy,{deleting=null},{vm.act{vm.repo.dao.deleteWorkout(w.id);deleting=null}}) }
}

@Composable
fun Progress(data: Snapshot,prefs: Preferences) {
    var selected by rememberSaveable{mutableStateOf<String?>(null)};var picker by remember{mutableStateOf(false)}
    val exercise=data.exercises.find{it.id==selected}
    Page("See your progress","Small steps. Measurable strength.") {
        Panel {
            Eyebrow("Consistency")
            Text("Your last 6 weeks",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            val weeks=Metrics.weekly(data)
            WeekBars(weeks)
            Text("Completed workouts · weeks start Monday in your device timezone.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            var allWeeks by rememberSaveable{mutableStateOf(false)}
            if(weeks.isNotEmpty()) TextButton({allWeeks=!allWeeks}){Text(if(allWeeks)"Hide weekly totals" else "All weekly totals")}
            if(allWeeks) weeks.forEach{(week,count)->Text("Week of $week · $count workout${if(count==1) "" else "s"}")}
        }
        SectionTitle("Exercise insights")
        Action(exercise?.name ?: "Select an exercise"){picker=true}
        if(exercise==null) EmptyState("Track your strength","Choose an exercise to see your best working set and external load history.",Icons.Default.Star)
        exercise?.let { e ->
            val rows=Metrics.completed(data,e.id).sortedByDescending{it.workout.finishedAt}
            val best=Metrics.heaviest(data,e.id)
            Panel {
                Eyebrow("Personal best · external load")
                Text(best?.let{"${number(Weight.display(it.set.weightKg,prefs.unit))} ${prefs.unit}"} ?: "—",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                Text(best?.let{"${it.set.reps} reps · ${date(it.workout.finishedAt!!)}"} ?: "No completed working sets yet.")
                Text("Completed working sets only; warm-ups excluded.",style=MaterialTheme.typography.bodySmall)
            }
            Panel {
                Eyebrow("External-weight volume")
                Text("${number(Weight.display(Metrics.volume(data,e.id),prefs.unit))} ${prefs.unit}·reps",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Sum of external weight × reps for weighted exercise snapshots only. Bodyweight sets are excluded; this is not bodyweight training volume.",style=MaterialTheme.typography.bodySmall)
            }
            Text("Weight history",style=MaterialTheme.typography.titleLarge)
            rows.groupBy{it.workout.id}.values.forEach { sets -> Panel {
                Text(date(sets.first().workout.finishedAt!!))
                sets.forEach { Text("${number(Weight.display(it.set.weightKg,prefs.unit))} ${prefs.unit} external × ${it.set.reps} reps") }
            } }
            if(rows.isEmpty()) Text("Complete a working set for this exercise to start its history.")
        }
    }
    if(picker) {
        // Archived exercises remain selectable for their historical progress.
        ExercisePicker(data.copy(exercises=data.exercises.map{it.copy(archived=false)}),{picker=false}){selected=it.id;picker=false}
    }
}

@Composable
fun Settings(vm: GymViewModel,prefs: Preferences,busy: Boolean) {
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::prepareRestore)}
    var rest by remember(prefs.restSeconds){mutableStateOf(prefs.restSeconds.toString())}
    Page("Make it yours","Your preferences. Your data. Always on your device.") {
        Panel {
            Text("Weight unit",style=MaterialTheme.typography.titleMedium)
            Choices(listOf("kg","lb"),prefs.unit){unit->vm.act{vm.repo.preferences.save(prefs.copy(unit=unit))}}
            Text("Weights are stored in kilograms and converted for display.",style=MaterialTheme.typography.bodySmall)
        }
        Panel { Text("Appearance",style=MaterialTheme.typography.titleMedium);Choices(listOf("DARK","LIGHT","SYSTEM"),prefs.theme){theme->vm.act{vm.repo.preferences.save(prefs.copy(theme=theme))}} }
        Panel {
            Text("Rest preferences",style=MaterialTheme.typography.titleMedium)
            Input("Default rest (seconds, 1–3,600)",rest,{rest=it},true)
            Quiet("Save rest duration",!busy){vm.act{vm.repo.preferences.save(prefs.copy(restSeconds=rest.toIntOrNull() ?: 0))}}
            Toggle("Start rest after completing a set",prefs.autoRest,!busy){value->vm.act{vm.repo.preferences.save(prefs.copy(autoRest=value))}}
            Text("The default applies to newly added exercises and new empty workout timers. Routine exercises use their own rest targets.",style=MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text("Backup and restore",style=MaterialTheme.typography.titleLarge)
            Text("Export records and preferences to a versioned JSON file. Restore replaces current records after validation and confirmation. Maximum file size: 10 MiB.")
            Action("Export JSON backup",!busy){export.launch("my-gym-log-${LocalDate.now()}.json")}
            Quiet("Restore JSON backup",!busy){restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
            Text("Uninstalling or clearing app data removes all local records. Keep an exported backup somewhere safe. The system file picker may offer cloud providers; choose device storage to keep your backup local.",style=MaterialTheme.typography.bodySmall)
        }
        Text("My Gym Log · version ${BuildConfig.VERSION_NAME}",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
    }
}

@Preview(name="Home • dark",showBackground=true,widthDp=390,heightDp=844)
@Composable fun HomeDarkPreview() { GymTheme { Surface { Home(Snapshot(),false,{},{},{},{}) } } }

@Preview(name="Set entry • dark",showBackground=true,widthDp=390)
@Composable fun SetRowsPreview() { GymTheme { Surface { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    SetRow(1,WorkoutSet(id="preview-1",workoutExerciseId="preview",position=0,weightKg=60.0,reps=8,completed=true),Preferences(),false,{},{})
    SetRow(2,WorkoutSet(id="preview-2",workoutExerciseId="preview",position=1,weightKg=60.0,reps=8),Preferences(),false,{},{})
} } } }
