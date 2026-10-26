@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mygym.log.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    val colors=if(dark) darkColorScheme(primary=Color(0xFF8FC4FF),background=Color(0xFF0C131C),surface=Color(0xFF141F2D),secondary=Color(0xFFA6BCD5))
        else lightColorScheme(primary=Color(0xFF155DA3),background=Color(0xFFF5F8FC),surface=Color(0xFFEAF0F8))
    MaterialTheme(colorScheme=colors) {
        val nav=rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val snackbar=remember { SnackbarHostState() }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
        CompositionLocalProvider(LocalSaveError provides message) {
        Scaffold(snackbarHost={SnackbarHost(snackbar)}, topBar={ Column {
            TopAppBar(title={Text("My Gym Log",fontWeight=FontWeight.Bold)}, actions={if(busy) CircularProgressIndicator(Modifier.size(24.dp))})
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("home" to "Home", "routines" to "Routines", "history" to "History", "progress" to "Progress", "exercises" to "Exercises", "settings" to "Settings").forEach { (route,label) ->
                    FilterChip(selected=entry?.destination?.route==route, onClick={nav.navigate(route){launchSingleTop=true;popUpTo("home")}},label={Text(label)})
                }
            }
        } }) { padding ->
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        subtitle?.let { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable fun Panel(content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) } }
@Composable fun Action(label: String, enabled: Boolean=true, onClick: ()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled) { Text(label) } }
@Composable fun Quiet(label: String, enabled: Boolean=true, onClick: ()->Unit) { OutlinedButton(onClick,Modifier.heightIn(min=48.dp),enabled=enabled) { Text(label) } }
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
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=!multiline,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
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
    Page("Your next strong day", "Personal records. Stored on this device.") {
        Panel {
            Text(if(active==null) "Ready when you are" else "Workout in progress",style=MaterialTheme.typography.titleLarge)
            Text(active?.let{"${it.name} · started ${date(it.startedAt)}"} ?: "Start fresh or follow one of your routines.")
            Action(if(active==null) "Start empty workout" else "Resume workout",!busy,if(active==null)start else resume)
        }
        Panel { Text("${data.workouts.count{it.finishedAt!=null}} completed workouts",style=MaterialTheme.typography.titleLarge); Text("${data.exercises.count{!it.archived}} exercises · ${data.routines.size} routines") }
        Action("Browse routines",onClick=routines)
        Quiet("View workout history",onClick=history)
        Text("Your changes are saved on this device. Export a JSON backup in Settings to keep a separate copy.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Exercises(vm: GymViewModel,data: Snapshot,busy: Boolean) {
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var show by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    Page("Exercise library","Archive an exercise to hide it from pickers. Existing workouts keep their original names and tracking types.") {
        Action("Add exercise",!busy){editing=null;show=true}
        Toggle("Show archived exercises",showArchived){showArchived=it}
        data.exercises.filter{showArchived || !it.archived}.forEach { e -> Panel {
            Text(e.name,style=MaterialTheme.typography.titleMedium)
            Text("${e.muscle} · ${e.equipment} · ${e.tracking.lowercase()}")
            Quiet("Edit",!busy){editing=e;show=true}
            Quiet(if(e.archived) "Unarchive" else "Archive",!busy){vm.act{vm.repo.exercise(e.copy(archived=!e.archived))}}
        } }
        if(data.exercises.isEmpty()) Text("Add your first exercise to start logging sets.")
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
    Page("Routines","Build a repeatable plan. Starting it creates an independent workout.") {
        Action("Create routine",!busy){name="";add=true}
        if(data.routines.isEmpty()) Text("No routines yet. Create one and add exercises with target sets, reps, and rest.")
        data.routines.forEach { r -> Panel {
            Text(r.name,style=MaterialTheme.typography.titleLarge)
            Text("${data.routineExercises.count{it.routineId==r.id}} exercises")
            Action(if(data.workouts.any{it.activeSlot==1}) "Resume active workout" else "Start routine",!busy){vm.act{vm.repo.start(r.id)};active()}
            Quiet("Edit routine",!busy){edit(r.id)}
            OrderActions({vm.act{vm.repo.move("routine",r.id,-1)}},{vm.act{vm.repo.move("routine",r.id,1)}},busy)
            Quiet("Delete routine",!busy){deleting=r}
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
            Text(data.exercises.find{it.id==row.exerciseId}?.name ?: "Exercise",style=MaterialTheme.typography.titleMedium)
            Text("${row.targetSets} sets × ${row.targetReps} reps · ${row.restSeconds}s rest")
            Quiet("Edit targets",!busy){target=row}
            OrderActions({vm.act{vm.repo.move("target",row.id,-1)}},{vm.act{vm.repo.move("target",row.id,1)}},busy)
            Quiet("Remove from routine",!busy){vm.act{vm.repo.dao.deleteRoutineExercise(row.id)}}
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
        Quiet("Edit name and workout notes",!busy){editWorkout=true}
        if(w.notes.isNotBlank()) Text(w.notes)
        if(w.activeSlot==1) Timer(w,busy,{action->vm.act{vm.repo.timer(w.id,action)}})
        val exercises=data.workoutExercises.filter{it.workoutId==w.id}
        if(exercises.isEmpty()) Text("Add an exercise, then tap a set to enter weight and reps.")
        exercises.forEach { e -> Panel {
            Text(e.nameSnapshot,style=MaterialTheme.typography.titleLarge)
            val previous=data.workouts.filter{it.finishedAt!=null && it.id!=w.id && it.finishedAt<=w.startedAt}.sortedByDescending{it.finishedAt}.firstOrNull { old ->
                data.workoutExercises.any{it.workoutId==old.id && it.exerciseId==e.exerciseId && data.sets.any{s->s.workoutExerciseId==it.id && s.completed}}
            }
            val prevExercises=data.workoutExercises.filter{it.workoutId==previous?.id && it.exerciseId==e.exerciseId}.map{it.id}.toSet()
            val prevSets=data.sets.filter{it.workoutExerciseId in prevExercises && it.completed}
            Text(if(previous==null) "No earlier completed performance" else "Last · ${date(previous.finishedAt!!)}\n"+prevSets.joinToString(" · "){"${number(Weight.display(it.weightKg,prefs.unit))} ${prefs.unit} × ${it.reps}${if(it.warmup) " warm-up" else ""}"},style=MaterialTheme.typography.bodySmall)
            if(e.trackingSnapshot=="BODYWEIGHT") Text("External load only; 0 = bodyweight",style=MaterialTheme.typography.bodySmall)
            data.sets.filter{it.workoutExerciseId==e.id}.forEachIndexed { index,s ->
                OutlinedButton({editSet=s},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=!busy) {
                    Text("${index+1}.  ${number(Weight.display(s.weightKg,prefs.unit))} ${prefs.unit} × ${s.reps}  · ${if(s.warmup) "Warm-up" else "Working"} · ${if(s.completed) "Done" else "Open"}")
                }
            }
            Quiet("Add set",!busy){vm.act{vm.repo.addSet(e)}}
            if(e.notes.isNotBlank()) Text(e.notes)
            Quiet("Exercise notes",!busy){noteExercise=e}
            OrderActions({vm.act{vm.repo.move("exercise",e.id,-1)}},{vm.act{vm.repo.move("exercise",e.id,1)}},busy)
            Quiet("Remove exercise",!busy){removeExercise=e}
        } }
        Action("Add exercise",!busy){picker=true}
        if(w.activeSlot==1) { Action("Finish workout",!busy){finish=true};Quiet("Discard workout",!busy){discard=true} }
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
        Text("Rest timer",style=MaterialTheme.typography.titleMedium)
        Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.displaySmall)
        Text("${w.timerDuration/1000}s · ${if(w.timerEnd!=null && seconds>0) "Running" else if(seconds>0) "Ready / paused" else "Rest complete"}")
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Quiet("Start",!busy){action("start")}
            Quiet(if(w.timerEnd!=null) "Pause" else "Resume",!busy && seconds>0){action(if(w.timerEnd!=null)"pause" else "resume")}
            Quiet("Reset",!busy){action("reset")};Quiet("Skip",!busy){action("skip")}
        }
        Text("The timer stays accurate when you return. Background sound and notifications are not enabled.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun History(vm: GymViewModel,data: Snapshot,busy: Boolean,open: (String)->Unit) {
    var deleting by remember{mutableStateOf<Workout?>(null)}
    Page("Workout history","Finished workouts, newest first. Open a workout to edit its saved sets.") {
        val rows=data.workouts.filter{it.finishedAt!=null}.sortedByDescending{it.finishedAt}
        if(rows.isEmpty()) Text("Your finished workouts will appear here.")
        rows.forEach { w -> Panel {
            Text(w.name,style=MaterialTheme.typography.titleLarge);Text(date(w.finishedAt!!))
            val ids=data.workoutExercises.filter{it.workoutId==w.id}.map{it.id}.toSet()
            Text("${data.sets.count{it.workoutExerciseId in ids && it.completed}} completed sets")
            Quiet("Open / edit",!busy){open(w.id)};Quiet("Delete workout",!busy){deleting=w}
        } }
    }
    deleting?.let { w -> Confirm("Delete ${w.name}?","All exercises, notes, and sets in this workout will be permanently deleted.",busy,{deleting=null},{vm.act{vm.repo.dao.deleteWorkout(w.id);deleting=null}}) }
}

@Composable
fun Progress(data: Snapshot,prefs: Preferences) {
    var selected by rememberSaveable{mutableStateOf<String?>(null)};var picker by remember{mutableStateOf(false)}
    val exercise=data.exercises.find{it.id==selected}
    Page("Your progress","Only completed working sets from finished workouts count below.") {
        Panel {
            Text("Workouts by week",style=MaterialTheme.typography.titleLarge)
            Text("Weeks begin Monday in your device timezone.",style=MaterialTheme.typography.bodySmall)
            val weeks=Metrics.weekly(data)
            if(weeks.isEmpty()) Text("Finish your first workout to see weekly counts.")
            weeks.forEach{(week,count)->Text("Week of $week · $count workout${if(count==1) "" else "s"}")}
        }
        Action(exercise?.name ?: "Select an exercise"){picker=true}
        exercise?.let { e ->
            val rows=Metrics.completed(data,e.id).sortedByDescending{it.workout.finishedAt}
            val best=Metrics.heaviest(data,e.id)
            Panel {
                Text("Heaviest external load",style=MaterialTheme.typography.titleLarge)
                Text(best?.let{"${number(Weight.display(it.set.weightKg,prefs.unit))} ${prefs.unit} × ${it.set.reps} reps\n${date(it.workout.finishedAt!!)}"} ?: "No completed working sets yet.")
                Text("Completed working sets only; warm-ups excluded.",style=MaterialTheme.typography.bodySmall)
            }
            Panel {
                Text("External-weight volume",style=MaterialTheme.typography.titleLarge)
                Text("${number(Weight.display(Metrics.volume(data,e.id),prefs.unit))} ${prefs.unit}·reps")
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
    Page("Settings","Offline by design. No account, ads, analytics, or network permission.") {
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
        Text("My Gym Log · version 1.0")
    }
}
