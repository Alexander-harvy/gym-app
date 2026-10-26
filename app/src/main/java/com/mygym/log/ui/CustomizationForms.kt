package com.mygym.log.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.mygym.log.data.*

@Composable fun NewWorkoutForm(busy: Boolean, dismiss: ()->Unit, create: (String,String)->Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Form("Create your workout",busy,dismiss,{create(name.trim(),notes)}) {
        Text("Give your session any name. Add your own exercises next, then save it as a reusable plan if you like.")
        Input("Workout name",name,{name=it})
        Input("Notes (optional)",notes,{notes=it},multiline=true)
    }
}

@Composable fun SavePlanForm(initialName: String,busy: Boolean,dismiss: ()->Unit,save: (String)->Unit) {
    var name by remember { mutableStateOf(initialName) }
    Form("Save as workout plan",busy,dismiss,{save(name.trim())}) {
        Input("Plan name",name,{name=it})
        Text("Copies exercise order, rest times, working-set counts, and the first working set's reps as each exercise's target. Warm-ups, weights and completion flags aren't copied. Exercises without working sets get 1 set × 8 reps. The current session stays unchanged.")
    }
}

@Composable fun ExerciseSetupForm(existing: Exercise?,initialName: String,defaultRest: Int,busy: Boolean,
    dismiss: ()->Unit,save: (Exercise,Int,Int,Int)->Unit) {
    val draftId=remember { newId() }
    var name by remember { mutableStateOf(existing?.name ?: initialName) }
    var muscle by remember { mutableStateOf(existing?.muscle ?: "") }
    var equipment by remember { mutableStateOf(existing?.equipment ?: "") }
    var tracking by remember { mutableStateOf(existing?.tracking ?: "WEIGHTED") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("8") }
    var rest by remember { mutableStateOf(defaultRest.toString()) }
    Form(if(existing==null)"Add custom exercise" else "Add ${existing.name}",busy,dismiss,{
        val exercise=existing ?: Exercise(draftId,name.trim(),muscle.trim().ifBlank{"General"},equipment.trim().ifBlank{"None"},tracking)
        save(exercise,sets.toIntOrNull() ?: 0,reps.toIntOrNull() ?: 0,rest.toIntOrNull() ?: 0)
    }) {
        if(existing==null) {
            Text("Create any exercise. It will be saved in your library and added here together.")
            Input("Exercise name",name,{name=it})
            Input("Muscle group (optional)",muscle,{muscle=it})
            Input("Equipment (optional)",equipment,{equipment=it})
            Choices(listOf("WEIGHTED","BODYWEIGHT"),tracking,{tracking=it})
        }
        Text("Choose your own starting targets. You can edit individual sets during the workout.")
        Input("Sets (1–50)",sets,{sets=it},true)
        Input("Reps per set (1–1,000)",reps,{reps=it},true)
        Input("Rest seconds (1–3,600)",rest,{rest=it},true)
    }
}

@Composable fun ExerciseRestForm(initial: Int,busy: Boolean,dismiss: ()->Unit,save: (Int)->Unit) {
    var rest by remember { mutableStateOf(initial.toString()) }
    Form("Customize exercise rest",busy,dismiss,{save(rest.toIntOrNull() ?: 0)}) {
        Input("Rest seconds (1–3,600)",rest,{rest=it},true)
        Text("Used when completing this exercise's next set with automatic rest enabled. A timer already running is unchanged.")
    }
}
