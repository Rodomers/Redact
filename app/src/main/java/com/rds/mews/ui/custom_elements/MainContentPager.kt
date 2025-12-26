package com.rds.mews.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rds.mews.MainActivity
import com.rds.mews.ui.custom_elements.TabScreen
import com.rds.mews.ui.grids.SettingsScreen
import com.rds.mews.ui.grids.SourcesScreen
import com.rds.mews.ui.grids.TitlesScreen
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.viewmodels.SourcesViewModel
import com.rds.mews.viewmodels.TitlesViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun MainContentPager(
    pagerState: PagerState,
    tabs: List<TabScreen>,
    paddingValues: PaddingValues,
    compactTab: Boolean,
    sourcesViewModel: SourcesViewModel,
    titlesViewModel: TitlesViewModel,
    settingsViewModel: SettingsViewModel,
    sourcesGridState: LazyGridState,
    titlesGridState: LazyGridState,
    settingsGridState: LazyGridState,
    mainActivity: MainActivity,
    scope: CoroutineScope
) {
    val context = LocalContext.current

    val systemBottomPadding = paddingValues.calculateBottomPadding()
    val customBarHeight = if (compactTab) 59.dp else 76.dp
    val totalBottomSpacer = customBarHeight + systemBottomPadding

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) { page ->
        val screenModifier = Modifier.fillMaxSize()

        when (tabs[page]) {
            TabScreen.Sources -> {
                SourcesScreen(
                    context = context,
                    gridState = sourcesGridState,
                    modifier = screenModifier,
                    viewModel = sourcesViewModel,
                    bottomSpacer = totalBottomSpacer
                )
            }

            TabScreen.Titles -> {
                TitlesScreen(
                    viewModel = titlesViewModel,
                    lazyGridState = titlesGridState,
                    mainActivity = mainActivity,
                    modifier = screenModifier,
                    scope = scope,
                    bottomSpacer = totalBottomSpacer
                )
            }

            TabScreen.Settings -> {
                SettingsScreen(
                    gridState = settingsGridState,
                    modifier = screenModifier,
                    viewModel = settingsViewModel,
                    mainActivity = mainActivity,
                    bottomSpacer = totalBottomSpacer
                )
            }
        }
    }
}