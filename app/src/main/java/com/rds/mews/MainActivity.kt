package com.rds.mews

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.localcore.isNotificationPermissionGranted
import com.rds.mews.localcore.isScheduleExactAlarm
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.ui.MainContentPager
import com.rds.mews.ui.custom_elements.MyBottomBar
import com.rds.mews.ui.custom_elements.TabScreen
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

    val currentTheme by settingsViewModel.darkTheme.collectAsStateWithLifecycle()
    val appTheme by settingsViewModel.appTheme.collectAsStateWithLifecycle()

    val currentLangResource = stringResource(R.string.current_language)
    LaunchedEffect(currentLangResource) {
        MewsRepository.setCurrentLanguage(currentLangResource)
    }

    MewsTheme(settingsTheme = currentTheme, appTheme = appTheme) {
        val selectedTab by MewsRepository.selectedTab.collectAsStateWithLifecycle()
        val compactTab by settingsViewModel.compactTabBar.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()

        MewsRepository.setLastTitlesUpdate(1792388640000)

        val sourcesGridState = rememberLazyGridState()
        val titlesGridState = rememberLazyGridState()
        val settingsGridState = rememberLazyGridState()

        val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)
        val pagerState = rememberPagerState(pageCount = { tabs.size })

        var isProgrammaticScroll by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                if (!isProgrammaticScroll) {
                    val currentTabFromPager = tabs[page]
                    if (MewsRepository.selectedTab.value != currentTabFromPager) {
                        MewsRepository.setCurrentTab(currentTabFromPager)
                    }
                }
            }
        }

        LaunchedEffect(selectedTab) {
            val targetIndex = tabs.indexOf(selectedTab)
            if (pagerState.currentPage != targetIndex) {
                isProgrammaticScroll = true
                pagerState.animateScrollToPage(targetIndex)
                isProgrammaticScroll = false
            }
        }

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

        Scaffold { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {

                MainContentPager(
                    pagerState = pagerState,
                    tabs = tabs,
                    paddingValues = paddingValues,
                    compactTab = compactTab,
                    sourcesViewModel = sourcesViewModel,
                    titlesViewModel = titlesViewModel,
                    settingsViewModel = settingsViewModel,
                    sourcesGridState = sourcesGridState,
                    titlesGridState = titlesGridState,
                    settingsGridState = settingsGridState,
                    mainActivity = mainActivity,
                    scope = scope
                )

                MyBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        if (selectedTab == newTab) {
                            when (selectedTab) {
                                TabScreen.Sources -> sourcesViewModel.scrollToTop()
                                TabScreen.Titles -> titlesViewModel.scrollToTop()
                                TabScreen.Settings -> settingsViewModel.scrollToTop()
                            }
                        } else {
                            MewsRepository.setCurrentTab(newTab)
                        }
                    },
                    compact = compactTab,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}