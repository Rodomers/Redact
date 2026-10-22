package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rds.mews.MainActivity
import com.rds.mews.ui.grids.SettingsScreen
import com.rds.mews.ui.grids.SourcesScreen
import com.rds.mews.ui.grids.TitlesScreen
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.viewmodels.SourcesViewModel
import com.rds.mews.viewmodels.TitlesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max

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
    scope: CoroutineScope,
    holdProgress: Float = 0f,
    isHolding: Boolean = false,
    isBlitzActive: Boolean = false,
) {
    val context = LocalContext.current

    val systemBottomPadding = paddingValues.calculateBottomPadding()
    val customBarHeight = if (compactTab) 59.dp else 76.dp
    val totalBottomSpacer = customBarHeight + systemBottomPadding

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val smoothHoldProgress by animateFloatAsState(
            targetValue = if (isBlitzActive || isHolding) 1f else 0f,
            animationSpec = tween(durationMillis = 400),
            label = "blur_anim"
        )

        val effectiveProgress = max(holdProgress, smoothHoldProgress)
        val blurRadius = (effectiveProgress * 50f)
        val overlayAlpha = (effectiveProgress * 0.5f).coerceIn(0f, 0.5f)

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
                    Box(modifier = screenModifier) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(radius = blurRadius.dp)
                        ) {
                            TitlesScreen(
                                viewModel = titlesViewModel,
                                lazyGridState = titlesGridState,
                                mainActivity = mainActivity,
                                modifier = Modifier.fillMaxSize(),
                                scope = scope,
                                bottomSpacer = totalBottomSpacer
                            )
                        }

                        if (isHolding && effectiveProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = overlayAlpha))
                            )
                        }
                    }
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
}