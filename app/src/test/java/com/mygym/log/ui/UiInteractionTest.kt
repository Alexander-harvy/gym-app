package com.mygym.log.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
// Explicit w/h qualifiers can hang dialog TextFields in Robolectric (#8460).
@Config(sdk=[28])
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
    @Test fun offlineRouteNeedsRealPointsAndHasAnAccessibleFitControl() {
        var points by mutableStateOf(emptyList<RunPoint>())
        compose.setContent { GymTheme { Surface { RouteView(points) } } }
        compose.onNodeWithText("Waiting for a route").assertExists()
        compose.onNodeWithText("Offline GPS route").assertDoesNotExist()
        compose.runOnIdle {
            points=listOf(RunPoint(runId="r",sequence=0,segment=0,latitude=14.0,longitude=121.0,accuracyMeters=5.0,timestamp=1000,elapsedRealtimeMs=100),
                RunPoint(runId="r",sequence=1,segment=0,latitude=14.0001,longitude=121.0,accuracyMeters=5.0,timestamp=6000,elapsedRealtimeMs=5100))
        }
        compose.onNodeWithText("Offline GPS route").assertExists()
        compose.onNodeWithText("Fit route").performClick()
        compose.onNodeWithContentDescription("Offline route with 2 recorded GPS points. North is up. Pinch to zoom and drag to pan. No street map.").assertExists()
    }
    @Test fun newWorkoutFormAcceptsAUserNameAndNotes() {
        var saved: Pair<String,String>?=null
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { NewWorkoutForm(false,{}) { name,notes -> saved=name to notes } } } }
        compose.onNodeWithText("Workout name").performTextInput("My own training day")
        compose.onNodeWithText("Notes (optional)").performTextInput("Use whatever equipment is available")
        compose.onNodeWithText("Save").performClick()
        assertEquals("My own training day" to "Use whatever equipment is available",saved)
    }
    @Test fun emptyPickerCanCreateTheTypedExerciseWithoutLeavingWorkout() {
        var typed: String?=null
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { ExercisePicker(Snapshot(),{},create={typed=it}){} } } }
        compose.onNodeWithText("Search exercises").performTextInput("My suitcase squat")
        compose.onNodeWithText("Create custom exercise").performClick()
        assertEquals("My suitcase squat",typed)
    }
    @Test fun customExerciseSetupUsesUserTargetsAndOptionalMetadata() {
        var exercise: Exercise?=null
        var targets: Triple<Int,Int,Int>?=null
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { ExerciseSetupForm(null,"My personal movement",90,false,{}) { e,sets,reps,rest ->
            exercise=e;targets=Triple(sets,reps,rest)
        } } } }
        compose.onNodeWithText("Sets (1–50)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Sets (1–50)").performTextInput("4")
        compose.onNodeWithText("Reps per set (1–1,000)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Reps per set (1–1,000)").performTextInput("12")
        compose.onNodeWithText("Rest seconds (1–3,600)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Rest seconds (1–3,600)").performTextInput("75")
        compose.onNodeWithText("Save").performClick()
        assertEquals("My personal movement",exercise!!.name)
        assertEquals("General",exercise!!.muscle);assertEquals("None",exercise!!.equipment)
        assertEquals(Triple(4,12,75),targets)
    }
}
