package com.rds.mews

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.ui.theme.MewsTheme
import io.ktor.http.ContentType


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri


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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory())
    val titlesViewModel: TitlesViewModel = viewModel(factory = TitlesViewModelFactory(LocalContext.current.applicationContext as Application))
    val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModelFactory())

    val currentTheme by settingsViewModel.currentTheme.collectAsStateWithLifecycle()
    val isMonetColors by settingsViewModel.isMonetColors.collectAsStateWithLifecycle()

    // отладочное
//    settingsViewModel.setTitlesNum(3)

    MewsTheme(settingsTheme = currentTheme, monetTheme = isMonetColors) {
        var selectedTab by remember { mutableStateOf<TabScreen>(TabScreen.Sources) }

        val compactTab by settingsViewModel.compactTabBar.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var optimizationIgnore by remember { mutableStateOf(false) }

        if (!isBatteryOptimizationIgnored(context) && !optimizationIgnore) {
            val scope = rememberCoroutineScope()

            CustomErrorBottomSheet(
                title = stringResource(R.string.optimization_sheet_header),
                text = stringResource(R.string.optimization_sheet_text),
                cancelBtnText = stringResource(R.string.optimization_sheet_cancel),
                confBtnText = stringResource(R.string.optimization_sheet_conf),
                onDismissRequest = { optimizationIgnore = true },
                onConfirm = {
                    requestIgnoreBatteryOptimization(context)
                    optimizationIgnore = true
                            },
                scope = scope,
                sheetState = sheetState,
            )
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
            val modifier = remember(paddingValues) {
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            }

            when (selectedTab) {
                TabScreen.Sources -> {
                    val sourcesList by sourcesViewModel.sources.collectAsState()
                    val context = context

                    SourcesGrid(
                        sourcesList,
                        modifier = modifier,
                        onSourceAdd = { name, link -> sourcesViewModel.addSource(context, name, link) },
                        onSourceDelete = { name -> sourcesViewModel.deleteSource(name) },
                        onSourceChange = { oldName, newName -> sourcesViewModel.changeSource(oldName, newName)}
                    )
                }
                TabScreen.Titles -> {
                    val gridState = titlesViewModel.gridState

                    val groupedTitles by titlesViewModel.groupedTitles.collectAsStateWithLifecycle()
                    val isRefreshing by titlesViewModel.isRefreshing.collectAsState()
                    val err by titlesViewModel.errState.collectAsStateWithLifecycle()
                    val showEmptyMess by titlesViewModel.showEmptyMess.collectAsStateWithLifecycle()
                    val titlesCardStates by titlesViewModel.titleCardStates.collectAsStateWithLifecycle()

                    val showDates by titlesViewModel.showDates.collectAsStateWithLifecycle()
                    val lastTitlesUpdate by titlesViewModel.lastUpdated.collectAsStateWithLifecycle()

                    val scope = rememberCoroutineScope()

                    LaunchedEffect(key1 = Unit) {
                        if (groupedTitles.isEmpty()) {
                            titlesViewModel.refreshTitles(returnExisting = true)
                        }
                    }

                    TitlesGrid(
                        lazyGridState = gridState,
                        groupedItems = groupedTitles,
                        modifier = modifier,
                        isRefreshing = isRefreshing,
                        showEmptyMess = showEmptyMess,
                        toggleEmptyMess = titlesViewModel::toggleEmptyMess,
                        errState = err,
                        titlesCardStates = titlesCardStates,
                        onRefresh = { titlesViewModel.refreshTitles() },
                        onClearErr = { titlesViewModel.clearErr() },
                        onErrAction = titlesViewModel::handleErrorAction,
                        onToggleExpanded = titlesViewModel::toggleTitleExpanded,
                        rememberCardPage = titlesViewModel::changeTitleCurrentPage,
                        showDates = showDates,
                        lastTitlesUpdate = lastTitlesUpdate,
                        scope = scope
                    )
                }
                else -> SettingsGrid(
                    modifier = modifier,
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