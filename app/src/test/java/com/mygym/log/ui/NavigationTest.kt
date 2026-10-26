package com.mygym.log.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mygym.log.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationTest {
    @get:Rule val compose=createComposeRule()
    private fun show(map: Boolean=false) {
        compose.setContent { GymTheme { AppShell(Snapshot(),false,remember{SnackbarHostState()},
            primaryContent={page,open->Column(Modifier.fillMaxSize()) {
                Text("page-$page")
                if(page=="home")Button({open("progress")}){Text("Go to Progress")}
                if(map && page=="running")RouteView(listOf(
                    RunPoint(runId="r",sequence=0,segment=0,latitude=14.0,longitude=121.0,accuracyMeters=5.0,timestamp=1000,elapsedRealtimeMs=100),
                    RunPoint(runId="r",sequence=1,segment=0,latitude=14.0001,longitude=121.0,accuracyMeters=5.0,timestamp=6000,elapsedRealtimeMs=5100)))
            }},detailContent={entry,_,_->Text("detail-${entry.destination.route}")}) } }
    }
    @Test fun tapsAndSwipesKeepVisiblePageAndSelectedTabTogether() {
        show()
        compose.onNodeWithTag("tab-running").performClick()
        compose.onNodeWithText("page-running").assertIsDisplayed();compose.onNodeWithTag("tab-running").assertIsSelected()
        compose.onNodeWithTag("primary-pager").performTouchInput{swipeLeft()}
        compose.onNodeWithText("page-history").assertIsDisplayed();compose.onNodeWithTag("tab-history").assertIsSelected()
        compose.onNodeWithTag("primary-pager").performTouchInput{swipeRight()}
        compose.onNodeWithTag("tab-running").assertIsSelected()
    }
    @Test fun rapidTabTapsCancelPriorAnimationAndSettleOnLastChoice() {
        show();compose.mainClock.autoAdvance=false
        try {
            compose.onNodeWithTag("tab-routines").performClick()
            compose.onNodeWithTag("tab-progress").performClick()
            compose.onNodeWithTag("tab-home").performClick()
            compose.mainClock.advanceTimeBy(1500)
        } finally {compose.mainClock.autoAdvance=true}
        compose.onNodeWithTag("tab-home").assertIsSelected();compose.onNodeWithText("page-home").assertIsDisplayed()
    }
    @Test fun pageLinksAndRepeatedLibraryOpenReturnToTheSameTab() {
        show();compose.onNodeWithText("Go to Progress").performClick()
        compose.onNodeWithTag("tab-progress").assertIsSelected();compose.onNodeWithText("page-progress").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exercise library").performClick()
        compose.onNodeWithContentDescription("Exercise library").performClick()
        compose.onNodeWithText("detail-exercises").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithTag("tab-progress").assertIsSelected();compose.onNodeWithText("page-progress").assertIsDisplayed()
    }
    @Test fun draggingTheRoutePansTheMapInsteadOfSwitchingTabs() {
        show(map=true);compose.onNodeWithTag("tab-running").performClick()
        compose.onNodeWithContentDescription("Offline route with 2 recorded GPS points. North is up. Pinch to zoom and drag to pan. No street map.")
            .performTouchInput{swipeLeft()}
        compose.onNodeWithTag("tab-running").assertIsSelected();compose.onNodeWithText("page-running").assertIsDisplayed()
    }
}
