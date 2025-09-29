package com.rds.mews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.ui.theme.MewsTheme


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MainScreen()
        }
    }
}

sealed class TabScreen(@StringRes val titleResId: Int, val icon: ImageVector) {
    data object Sources: TabScreen(titleResId = R.string.tabscreen_sources, Icons.Default.Favorite)
    data object Titles: TabScreen(titleResId = R.string.tabscreen_titles, Icons.Rounded.Menu)
    data object Settings: TabScreen(titleResId = R.string.tabscreen_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen() {

    val settingsManager = SettingsManager(LocalContext.current.applicationContext)
    val factory = SettingsViewModelFactory(settingsManager)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    // отладочное
//    settingsViewModel.setTitlesNum(3)

    MewsTheme(settingsTheme = settingsViewModel.isDarkMode.value, monetTheme = settingsViewModel.isMonetColors.value) {
        var selectedTab by remember { mutableStateOf<TabScreen>(TabScreen.Sources) }

        val compactTab by settingsViewModel.compactTabBar

        val db = DbHelper(LocalContext.current.applicationContext)
//    db.titlesTimeKill(0)

        val sourcesList: SnapshotStateList<RSS> = remember { mutableStateListOf() }
        val updateSources: () -> Unit = {
            sourcesList.clear()
            sourcesList.addAll(db.getRSS())
        }

        val titlesList: SnapshotStateList<Title> = remember { mutableStateListOf() }
        val scope = rememberCoroutineScope()
        var isTitlesRefreshing by remember { mutableStateOf(false) }
        val titlesRefreshed = {
            settingsViewModel.setUpdatingState("update")
            settingsViewModel.setUpdatingTitles(false)
            isTitlesRefreshing = false
        }
        val context = LocalContext.current

        fun refreshTitles(returnExisting: Boolean = false) {
            isTitlesRefreshing = true
            var updatedList: List<Title>
            var iter = 0
            scope.launch {
                do {
                    iter++
                    println("iter: $iter")
                    updatedList = withContext(Dispatchers.IO) {
                        updateTitles(context, db, settingsViewModel, settingsManager, returnExisting = returnExisting, titlesRefreshed)
                    }
                } while (isTitlesRefreshing)

                val filteredUpdatedList = updatedList.filter {it.text != "<промежуточный текст>"}

                if (filteredUpdatedList.isNotEmpty()) {
                    titlesList.clear()
                    titlesList.addAll(filteredUpdatedList)
                }
            }
        }

        Scaffold(
            bottomBar = {
                MyBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        selectedTab = newTab
                    },
                    compact = compactTab,
                )
            }
        ) { paddingValues ->
            when (selectedTab) {
                TabScreen.Sources -> {
                    LaunchedEffect(key1 = Unit) {
                        updateSources()
                    }

                    SourcesGrid(
                        sourcesList,
                        modifier = Modifier.padding(paddingValues),
                        db = db,
                        onSourcesChanged = updateSources,
                        settingsViewModel = settingsViewModel
                    )
                }
                TabScreen.Titles -> {
                    LaunchedEffect(key1 = Unit) {
                        refreshTitles(returnExisting = true)
                    }

                    TitlesGrid(
                        itemsList = titlesList,
                        modifier = Modifier.padding(paddingValues)  ,
                        isRefreshing = isTitlesRefreshing,
                        onRefresh = ::refreshTitles,
                        settingsViewModel = settingsViewModel,
                        closeIndicator = titlesRefreshed,
                        scope = scope
                    )
                }
                else -> SettingsGrid(
                    modifier = Modifier.padding(paddingValues),
                    settingsModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun MyBottomBar(selectedTab: TabScreen, onTabSelected: (TabScreen) -> Unit, compact: Boolean = false) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)

    val targetHeight = if (compact) 50.dp else 70.dp

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 300),
        label = "NavigationBarHeight"
    )

    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .height(animatedHeight),
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        tabs.forEach { tab ->
            val tabTitle = stringResource(id = tab.titleResId)

            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(imageVector = tab.icon, contentDescription = tabTitle)
                },
                label = if (!compact) {
                    { Text(text = tabTitle) }
                } else null,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}