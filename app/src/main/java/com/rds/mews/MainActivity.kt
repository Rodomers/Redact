package com.rds.mews

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.localcore.isBatteryOptimizationIgnored
import com.rds.mews.localcore.isNotificationPermissionGranted
import com.rds.mews.localcore.isScheduleExactAlarm
import com.rds.mews.localcore.requestIgnoreBatteryOptimization
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.MyBottomBar
import com.rds.mews.ui.custom_elements.TabScreen
import com.rds.mews.ui.grids.SettingsGrid
import com.rds.mews.ui.grids.SourcesGrid
import com.rds.mews.ui.grids.TitlesGrid
import com.rds.mews.ui.theme.MewsTheme
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.viewmodels.SettingsViewModelFactory
import com.rds.mews.viewmodels.SourcesViewModel
import com.rds.mews.viewmodels.SourcesViewModelFactory
import com.rds.mews.viewmodels.TitlesScrollEvent
import com.rds.mews.viewmodels.TitlesViewModel
import com.rds.mews.viewmodels.TitlesViewModelFactory


import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleIntent(intent)

        setContent {
            MainScreen(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val alarmsAllowed = isScheduleExactAlarm(this)
        if (alarmsAllowed != MewsRepository.exactAlarmsAllowed.value) {
            MewsRepository.setExactAlarmsAllowed(alarmsAllowed)
        }
        val notificationsAllowed = isNotificationPermissionGranted(this)
        if (notificationsAllowed != MewsRepository.notificationsGranted.value) {
            MewsRepository.setNotificationsGranted(notificationsAllowed)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.hasExtra("selected_tab")) {
            val tabIndex = intent.getIntExtra("selected_tab", 0)
            MewsRepository.setCurrentTab(
                when (tabIndex) {
                    1 -> TabScreen.Titles
                    2 -> TabScreen.Settings
                    else -> TabScreen.Sources
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainActivity: MainActivity) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory())
    val titlesViewModel: TitlesViewModel = viewModel(factory = TitlesViewModelFactory(LocalContext.current.applicationContext as Application))
    val sourcesViewModel: SourcesViewModel = viewModel(factory = SourcesViewModelFactory())

    val currentTheme by settingsViewModel.currentTheme.collectAsStateWithLifecycle()
    val isMonetColors by settingsViewModel.isMonetColors.collectAsStateWithLifecycle()

    val currentLangResource = stringResource(R.string.current_language)
    LaunchedEffect(currentLangResource) {
        MewsRepository.setCurrentLanguage(currentLangResource)
    }

    MewsTheme(settingsTheme = currentTheme, monetTheme = isMonetColors) {
        val selectedTab by MewsRepository.selectedTab.collectAsStateWithLifecycle()
        val compactTab by settingsViewModel.compactTabBar.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var optimizationIgnore by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val titlesGridState = rememberLazyGridState()

        val sourcesGridState = sourcesViewModel.gridState
        val settingsGridState = settingsViewModel.gridState

        LaunchedEffect(Unit) {
            titlesViewModel.scrollEvents.collect { event ->
                when (event) {
                    TitlesScrollEvent.ScrollToTop -> {
                        if (titlesGridState.firstVisibleItemIndex == 0) {
                            titlesViewModel.toggleTitleExpanded(null)
                        } else {
                            titlesGridState.animateScrollToItem(0)
                        }
                    }
                    is TitlesScrollEvent.ScrollToItem -> {
                        titlesGridState.scrollToItem(event.id)
                    }
                }
            }
        }

        if (!isBatteryOptimizationIgnored(context) && !optimizationIgnore) {
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
                        if (selectedTab == newTab) {
                            when (selectedTab) {
                                TabScreen.Sources -> scope.launch {
                                    sourcesGridState.animateScrollToItem(0)
                                }

                                TabScreen.Settings -> scope.launch {
                                    settingsGridState.animateScrollToItem(0)
                                }

                                TabScreen.Titles -> {
                                    titlesViewModel.scrollToTop()
                                }
                            }
                        } else MewsRepository.setCurrentTab(newTab)
                    },
                    compact = compactTab
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
                    val onAddSource = remember(sourcesViewModel, context) {
                        { name: String, link: String -> sourcesViewModel.addSource(context, name, link) }
                    }

                    SourcesGrid(
                        gridState = sourcesGridState,
                        itemsList = sourcesList,
                        modifier = modifier,
                        onSourceAdd = onAddSource,
                        onSourceDelete = sourcesViewModel::deleteSource,
                        onSourceChange = { oldName, newName ->
                            sourcesViewModel.changeSource(oldName, newName)
                        }
                    )
                }
                TabScreen.Titles -> {
                    val groupedTitles by titlesViewModel.groupedTitles.collectAsStateWithLifecycle()
                    val isRefreshing by titlesViewModel.isRefreshing.collectAsStateWithLifecycle()
                    val err by titlesViewModel.errState.collectAsStateWithLifecycle()
                    val showEmptyMess by titlesViewModel.showEmptyMess.collectAsStateWithLifecycle()
                    val titlesCardStates by titlesViewModel.titleCardStates.collectAsStateWithLifecycle()

                    val showDates by titlesViewModel.showDates.collectAsStateWithLifecycle()
                    val endureTime by titlesViewModel.enlargedTimestamps.collectAsStateWithLifecycle()
                    val lastTitlesUpdate by titlesViewModel.lastUpdated.collectAsStateWithLifecycle()

                    val onRefreshTitle = remember(titlesViewModel) { { titlesViewModel.refreshTitles() } }
                    val onClearError = remember(titlesViewModel) { { titlesViewModel.clearErr() } }

                    TitlesGrid(
                        lazyGridState = titlesGridState,
                        groupedItems = groupedTitles,
                        modifier = modifier,
                        isRefreshing = isRefreshing,
                        showEmptyMess = showEmptyMess,
                        toggleEmptyMess = titlesViewModel::toggleEmptyMess,
                        errState = err,
                        titlesCardStates = titlesCardStates,
                        onRefresh = onRefreshTitle,
                        onClearErr = onClearError,
                        onErrAction = titlesViewModel::handleErrorAction,
                        onToggleExpanded = titlesViewModel::toggleTitleExpanded,
                        rememberCardPage = titlesViewModel::changeTitleCurrentPage,
                        showDates = showDates,
                        lastTitlesUpdate = lastTitlesUpdate,
                        scope = scope,
                        endureTime = endureTime,
                        mainActivity = mainActivity,
                        onBanTheme = titlesViewModel::onBanTheme,
                        onConfigChange = titlesViewModel::scrollToItem
                    )
                }
                else -> SettingsGrid(
                    gridState = settingsGridState,
                    modifier = modifier,
                    settingsModel = settingsViewModel,
                    mainActivity = mainActivity
                )
            }
        }
    }
}