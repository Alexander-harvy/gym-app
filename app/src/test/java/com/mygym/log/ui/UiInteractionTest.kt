package com.mygym.log.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mygym.log.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w390dp-h844dp")
@LooperMode(LooperMode.Mode.PAUSED)
class UiInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeOffersStartAndResumeForTheCorrectWorkoutState() {
        var snapshot by mutableStateOf(Snapshot())
        var started=false
        var resumed=false
        compose.setContent { GymTheme { Surface { Home(snapshot,false,{started=true},{resumed=true},{},{}) } } }
        compose.onNodeWithText("Start workout").performScrollTo().performClick()
        assertTrue(started)
        compose.runOnIdle { snapshot=Snapshot(workouts=listOf(Workout(name="Leg day"))) }
        compose.onNodeWithText("Resume workout").performScrollTo().performClick()
        assertTrue(resumed)
        compose.onNodeWithText("Start workout").assertDoesNotExist()
    }

    @Test fun setCompletionAndEditingHaveIndependentControls() {
        var set by mutableStateOf(WorkoutSet(workoutExerciseId="e",position=0,weightKg=60.0,reps=8))
        var edited=false
        var busy by mutableStateOf(false)
        compose.setContent { GymTheme { Surface { SetRow(1,set,Preferences(),busy,{edited=true}){set=set.copy(completed=it)} } } }
        compose.onNodeWithContentDescription("Complete set 1").performClick()
        compose.runOnIdle { assertTrue(set.completed); assertFalse(edited) }
        compose.onNodeWithContentDescription("Edit set 1, 60 kg, 8 reps, working").performClick()
        assertTrue(edited)
        compose.onNodeWithContentDescription("Mark set 1 incomplete").performClick()
        compose.runOnIdle { assertFalse(set.completed) }
        compose.runOnIdle { busy=true }
        compose.onNodeWithContentDescription("Complete set 1").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit set 1, 60 kg, 8 reps, working").assertIsNotEnabled()
    }

    @Test fun secondaryActionsStayHiddenUntilOptionsAreOpened() {
        var deleted=false
        compose.setContent { GymTheme { Surface { MoreMenu("Workout options",false,listOf(MenuAction("Delete workout",true){deleted=true})) } } }
        compose.onNodeWithText("Delete workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Workout options").performClick()
        compose.onNodeWithText("Delete workout").performClick()
        assertTrue(deleted)
    }
}
