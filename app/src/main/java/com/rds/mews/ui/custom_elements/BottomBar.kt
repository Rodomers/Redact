package com.rds.mews.ui.custom_elements

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.rds.mews.R
import kotlinx.coroutines.launch
import kotlin.collections.set
//
//import androidx.compose.animation.core.animateDpAsState
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.navigationBarsPadding
//import androidx.compose.material.icons.filled.Favorite
//import androidx.compose.material.icons.filled.Settings
//import androidx.compose.material.icons.rounded.Menu
//import androidx.compose.material3.Icon
//import androidx.compose.material3.NavigationBar
//import androidx.compose.material3.NavigationBarItem
//import androidx.compose.material3.Text
//import androidx.compose.ui.unit.dp

sealed class TabScreen(@StringRes val titleResId: Int, val icon: ImageVector) {
    data object Sources: TabScreen(titleResId = R.string.tabscreen_sources, Icons.Default.Favorite)
    data object Titles: TabScreen(titleResId = R.string.tabscreen_titles, Icons.Rounded.Menu)
    data object Settings: TabScreen(titleResId = R.string.tabscreen_settings, Icons.Default.Settings)
}

private data class TabPosition(val left: Float, val width: Float)

@Composable
private fun RowScope.MyBottomBarItems(
    selectedTab: TabScreen,
    onTabSelected: (TabScreen) -> Unit,
    tabs: List<TabScreen>,
    parentCoordinates: LayoutCoordinates?,
    onTabPositioned: (Int, TabPosition) -> Unit,
    compact: Boolean
) {
    tabs.forEachIndexed { index, tab ->
        val isSelected = selectedTab == tab
        val tabTitle = stringResource(id = tab.titleResId)
        NavigationBarItem(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    parentCoordinates?.let { parent ->
                        val position = TabPosition(
                            left = parent.localPositionOf(coordinates, Offset.Zero).x,
                            width = coordinates.size.width.toFloat()
                        )
                        onTabPositioned(index, position)
                    }
                },
            selected = isSelected,
            onClick = { onTabSelected(tab) },
            icon = { Icon(imageVector = tab.icon, contentDescription = tabTitle) },
            label = if (compact) null else { { Text(text = tabTitle) } },
            alwaysShowLabel = !compact,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.surface,
                unselectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                unselectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                indicatorColor = if (compact) Color.Transparent else MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
@Composable
private fun DraggableIndicatorNavBarContent(
    modifier: Modifier = Modifier,
    selectedTab: TabScreen,
    onTabSelected: (TabScreen) -> Unit,
    compact: Boolean
) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)
    val selectedIndex = tabs.indexOf(selectedTab)
    val coroutineScope = rememberCoroutineScope()

    var parentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var tabPositions by remember { mutableStateOf<Map<Int, TabPosition>>(emptyMap()) }

    val indicatorOffset = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }

    val previousSelectedIndex = remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex, tabPositions) {
        val targetPosition = tabPositions[selectedIndex] ?: return@LaunchedEffect
        if (previousSelectedIndex.intValue != selectedIndex) {
            coroutineScope.launch {
                indicatorOffset.animateTo(targetPosition.left, tween(300))
            }
            coroutineScope.launch {
                indicatorWidth.animateTo(targetPosition.width, tween(300))
            }
        } else {
            if (indicatorOffset.value != targetPosition.left) {
                coroutineScope.launch {
                    indicatorOffset.snapTo(targetPosition.left)
                }
            }
            if (indicatorWidth.value != targetPosition.width) {
                coroutineScope.launch {
                    indicatorWidth.snapTo(targetPosition.width)
                }
            }
        }

        previousSelectedIndex.intValue = selectedIndex
    }
    NavigationBar(
        modifier = modifier.onGloballyPositioned { parentCoordinates = it },
        containerColor = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
        tonalElevation = if (compact) 3.dp else 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (compact) {
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(indicatorOffset.value, 0f),
                        size = Size(indicatorWidth.value, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxSize()) {
                MyBottomBarItems(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    tabs = tabs,
                    parentCoordinates = parentCoordinates,
                    onTabPositioned = { index, position ->
                        tabPositions = tabPositions.toMutableMap().also { it[index] = position }
                    },
                    compact = compact
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MyBottomBar(
    selectedTab: TabScreen,
    onTabSelected: (TabScreen) -> Unit,
    compact: Boolean = false
) {
    AnimatedContent(
        targetState = compact,
        label = "CompactModeAnimation",
        transitionSpec = {
            val enter = fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(550, delayMillis = 90))
            val exit = fadeOut(animationSpec = tween(90))

            enter togetherWith exit
        }
    ) { isCompactTarget ->
        if (isCompactTarget) {
            Popup(alignment = Alignment.BottomCenter) {
                DraggableIndicatorNavBarContent(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 64.dp, vertical = 8.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    compact = true
                )
            }
        } else {
            DraggableIndicatorNavBarContent(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(70.dp),
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                compact = false
            )
        }
    }
}

// Legacy
//sealed class TabScreen(@StringRes val titleResId: Int, val icon: ImageVector) {
//    data object Sources: TabScreen(titleResId = R.string.tabscreen_sources, Icons.Default.Favorite)
//    data object Titles: TabScreen(titleResId = R.string.tabscreen_titles, Icons.Rounded.Menu)
//    data object Settings: TabScreen(titleResId = R.string.tabscreen_settings, Icons.Default.Settings)
//}
//
//@Composable
//fun MyBottomBar(selectedTab: TabScreen, onTabSelected: (TabScreen) -> Unit, compact: Boolean = false) {
//    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)
//
//    val targetHeight = if (compact) 50.dp else 70.dp
//
//    val animatedHeight by animateDpAsState(
//        targetValue = targetHeight,
//        animationSpec = tween(durationMillis = 300),
//        label = "NavigationBarHeight"
//    )
//
//    NavigationBar(
//        modifier = Modifier
//            .navigationBarsPadding()
//            .height(animatedHeight),
//        windowInsets = WindowInsets(0, 0, 0, 0),
//        containerColor = MaterialTheme.colorScheme.surface
//    ) {
//        tabs.forEach { tab ->
//            val tabTitle = stringResource(id = tab.titleResId)
//
//            NavigationBarItem(
//                selected = selectedTab == tab,
//                onClick = { onTabSelected(tab) },
//                icon = {
//                    Icon(imageVector = tab.icon, contentDescription = tabTitle)
//                },
//                label = if (!compact) {
//                    { Text(text = tabTitle) }
//                } else null,
//                colors = NavigationBarItemDefaults.colors(
//                    selectedIconColor = MaterialTheme.colorScheme.surface,
//                    unselectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
//                    indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer
//                )
//            )
//        }
//    }
//}