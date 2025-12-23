package com.rds.mews

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.localcore.isNotificationPermissionGranted
import com.rds.mews.localcore.isScheduleExactAlarm
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.ui.custom_elements.MyBottomBar
import com.rds.mews.ui.custom_elements.TabScreen
import com.rds.mews.ui.grids.SettingsScreen
import com.rds.mews.ui.grids.SourcesScreen
import com.rds.mews.ui.grids.TitlesScreen
import com.rds.mews.ui.theme.MewsTheme
import com.rds.mews.viewmodels.SettingsScrollEvent
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.viewmodels.SettingsViewModelFactory
import com.rds.mews.viewmodels.SourcesScrollEvent
import com.rds.mews.viewmodels.SourcesViewModel
import com.rds.mews.viewmodels.SourcesViewModelFactory
import com.rds.mews.viewmodels.TitlesScrollEvent
import com.rds.mews.viewmodels.TitlesViewModel
import com.rds.mews.viewmodels.TitlesViewModelFactory


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
//        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//        var optimizationIgnore by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val sourcesGridState = rememberLazyGridState()
        val titlesGridState = rememberLazyGridState()
        val settingsGridState = rememberLazyGridState()

        LaunchedEffect(Unit) {
            sourcesViewModel.scrollEvents.collect { event ->
                when (event) {
                    SourcesScrollEvent.ScrollToTop -> sourcesGridState.animateScrollToItem(0)
                }
            }
        }
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
        LaunchedEffect(Unit) {
            settingsViewModel.scrollEvents.collect { event ->
                when (event) {
                    SettingsScrollEvent.ScrollToTop -> settingsGridState.animateScrollToItem(0)
                }
            }
        }

//        if (!isBatteryOptimizationIgnored(context) && !optimizationIgnore) {
//            CustomErrorBottomSheet(
//                title = stringResource(R.string.optimization_sheet_header),
//                text = stringResource(R.string.optimization_sheet_text),
//                cancelBtnText = stringResource(R.string.optimization_sheet_cancel),
//                confBtnText = stringResource(R.string.optimization_sheet_conf),
//                onDismissRequest = { optimizationIgnore = true },
//                onConfirm = {
//                    requestIgnoreBatteryOptimization(context)
//                    optimizationIgnore = true
//                },
//                scope = scope,
//                sheetState = sheetState,
//            )
//        }

        Scaffold(
            bottomBar = {
                MyBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        if (selectedTab == newTab) {
                            when (selectedTab) {
                                TabScreen.Sources -> {
                                    sourcesViewModel.scrollToTop()
                                }

                                TabScreen.Titles -> {
                                    titlesViewModel.scrollToTop()
                                }

                                TabScreen.Settings -> {
                                    settingsViewModel.scrollToTop()
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
                    SourcesScreen(
                        context = context,
                        gridState = sourcesGridState,
                        modifier = modifier,
                        viewModel = sourcesViewModel
                    )
                }
                TabScreen.Titles -> {
                    TitlesScreen(
                        viewModel = titlesViewModel,
                        lazyGridState = titlesGridState,
                        mainActivity = mainActivity,
                        modifier = modifier,
                        scope = scope
                    )
                }
                else -> SettingsScreen(
                    gridState = settingsGridState,
                    modifier = modifier,
                    viewModel = settingsViewModel,
                    mainActivity = mainActivity
                )
            }
        }
    }
}