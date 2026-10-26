package com.mygym.log.ui

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        var load=0.0
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { ExerciseSetupForm(null,"My personal movement",90,false,{}) { e,sets,reps,rest,weight ->
            exercise=e;targets=Triple(sets,reps,rest);load=weight
        } } } }
        compose.onNodeWithText("Starting weight (kg)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Starting weight (kg)").performTextInput("23.5")
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
        assertEquals(23.5,load,0.0)
    }
    @Test fun predefinedExerciseAcceptsPoundsAndReturnsCanonicalKg() {
        val e=Exercise(name="Bench press",muscle="Chest",equipment="Barbell")
        var load=0.0
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { ExerciseSetupForm(e,"",90,false,{},unit="lb") { _,_,_,_,kg->load=kg } } } }
        compose.onNodeWithText("Starting weight (lb)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Starting weight (lb)").performTextInput("44.0924524")
        compose.onNodeWithText("Save").performClick()
        assertEquals(20.0,load,.000001)
    }
    @Test fun bodyFormRecordsWeightAndHeightWithExplicitUnits() {
        var saved: BodyMeasurement?=null
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { BodyMeasurementForm(null,"lb",false,{}){saved=it} } } }
        compose.onNodeWithText("Body weight (lb, optional)").performTextInput("165.3466966")
        compose.onNodeWithText("Height (cm, optional)").performTextInput("178.5")
        compose.onNodeWithText("Save").performClick()
        assertEquals(75.0,saved!!.weightKg!!,.000001);assertEquals(178.5,saved!!.heightCm!!,0.0)
    }
    @Test fun invalidBodyEntryShowsInlineErrorAndDoesNotSave() {
        var saved=false
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { BodyMeasurementForm(null,"kg",false,{}){saved=true} } } }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter a body weight, height, or both.").assertExists()
        assertFalse(saved)
        compose.onNodeWithText("Height (cm, optional)").performTextInput("0")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Height must be between 10 and 300 cm.").assertExists()
        assertFalse(saved)
    }
    @Test fun editingBodyNotesPreservesFullPrecisionMeasurements() {
        val original=BodyMeasurement(recordedAt=946684800000,weightKg=75.123456,heightCm=178.123456)
        var saved: BodyMeasurement?=null
        compose.setContent { GymTheme { Surface(Modifier.fillMaxSize()) { BodyMeasurementForm(original,"lb",false,{}){saved=it} } } }
        compose.onNodeWithText("Notes").performScrollTo().performClick()
        compose.onNodeWithText("Measurement notes").performScrollTo().performTextInput("Updated note")
        compose.onNodeWithText("Save").performClick()
        assertEquals(original.weightKg,saved!!.weightKg);assertEquals(original.heightCm,saved!!.heightCm)
        assertEquals("Updated note",saved!!.notes);assertEquals(original.recordedAt,saved!!.recordedAt)
    }
    @Test fun runHelpDisclosureStartsCollapsedAndCanBeHiddenAgain() {
        compose.setContent { GymTheme { Surface { Disclosure("Run tips & permissions"){Text("Detailed run instructions")} } } }
        compose.onNodeWithText("Detailed run instructions").assertDoesNotExist()
        compose.onNodeWithText("Run tips & permissions").performClick()
        compose.onNodeWithText("Detailed run instructions").assertExists()
        compose.onNodeWithText("Run tips & permissions").performClick()
        compose.onNodeWithText("Detailed run instructions").assertDoesNotExist()
    }
}
