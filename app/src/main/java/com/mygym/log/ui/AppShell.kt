@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mygym.log.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.*
import com.mygym.log.R
import com.mygym.log.data.Snapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class MainTab(val route: String,val label: String,val icon: ImageVector)
private val MainTabs=listOf(MainTab("home","Home",Icons.Default.Home),MainTab("routines","Plans",Icons.AutoMirrored.Filled.List),
    MainTab("running","Run",Icons.Default.PlayArrow),MainTab("history","History",Icons.Default.DateRange),MainTab("progress","Progress",Icons.Default.Star))
private class TabMotion { var job: Job?=null }

/** Primary pages have one PagerState, not separate back-stack entries and selection states. */
@Composable fun AppShell(data: Snapshot,busy: Boolean,snackbar: SnackbarHostState,
    openRunning: Boolean=false,onRunningHandled: ()->Unit={},
    primaryContent: @Composable (String,(String)->Unit)->Unit,
    detailContent: @Composable (NavBackStackEntry,(String)->Unit,()->Unit)->Unit,
    overlay: @Composable ((String)->Unit)->Unit={}) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "tabs"
    val primary=route=="tabs"
    val pager=rememberPagerState { MainTabs.size }
    val scope=rememberCoroutineScope()
    val motion=remember{TabMotion()}
    fun select(index: Int) {
        if(!primary)nav.popBackStack("tabs",false)
        motion.job?.cancel()
        motion.job=scope.launch { pager.animateScrollToPage(index) }
    }
    fun open(destination: String) {
        val tab=MainTabs.indexOfFirst{it.route==destination}
        if(tab>=0)select(tab) else if(route!=destination)nav.navigate(destination){launchSingleTop=true}
    }
    fun back() { if(!nav.popBackStack())select(0) }
    LaunchedEffect(openRunning) { if(openRunning){open("running");onRunningHandled()} }
    DisposableEffect(Unit) { onDispose { motion.job?.cancel() } }
    BackHandler(primary && pager.currentPage!=0){select(0)}
    val visible=if(primary)MainTabs[pager.currentPage].route else route
    Scaffold(snackbarHost={SnackbarHost(snackbar)},topBar={
        TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
            navigationIcon={if(!primary)IconButton({back()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Go back")}},
            title={Text("GYM",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1)},
            actions={
                Box(Modifier.size(32.dp),contentAlignment=Alignment.Center){if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)}
                IconButton({open("exercises")}){Icon(painterResource(R.drawable.ic_exercises),"Exercise library",tint=if(route=="exercises")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton({open("settings")}){Icon(Icons.Default.Settings,"Settings",tint=if(route=="settings")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            })
    },bottomBar={Column(if(primary)Modifier else Modifier.navigationBarsPadding()) {
        val workout=data.workouts.firstOrNull{it.activeSlot==1}
        val run=data.runs.firstOrNull{it.activeSlot==1}
        if((workout!=null && visible!="active") || (run!=null && visible!="running" && visible!="run/{id}"))Surface(color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(workout!=null && visible!="active")TextButton({open("active")},Modifier.weight(1f).heightIn(min=48.dp)){Text("Resume workout",maxLines=1,overflow=TextOverflow.Ellipsis)}
                if(run!=null && visible!="running" && visible!="run/{id}")TextButton({open("running")},Modifier.weight(1f).heightIn(min=48.dp)){Text(if(run.state=="RUNNING")"Activity recording" else "Activity paused",maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
        }
        if(primary)NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
            MainTabs.forEachIndexed { index,tab ->
                NavigationBarItem(modifier=Modifier.testTag("tab-${tab.route}"),selected=pager.targetPage==index,onClick={select(index)},
                    icon={Icon(tab.icon,null)},label={Text(tab.label,maxLines=1,overflow=TextOverflow.Ellipsis)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer))
            }
        }
    }}) { padding ->
        NavHost(nav,startDestination="tabs",modifier=Modifier.padding(padding),enterTransition={EnterTransition.None},exitTransition={ExitTransition.None},popEnterTransition={EnterTransition.None},popExitTransition={ExitTransition.None}) {
            composable("tabs") {
                HorizontalPager(pager,key={MainTabs[it].route},modifier=Modifier.fillMaxSize().testTag("primary-pager")) { index -> primaryContent(MainTabs[index].route,::open) }
            }
            listOf("exercises","settings","body","routine/{id}","active","workout/{id}","run/{id}").forEach { destination ->
                composable(destination){backStack->detailContent(backStack,::open,::back)}
            }
        }
    }
    overlay(::open)
}
