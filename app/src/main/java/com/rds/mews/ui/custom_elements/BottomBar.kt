package com.rds.mews.ui.custom_elements

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rds.mews.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.rds.mews.ui.theme.Shapes

sealed class TabScreen(@StringRes val titleResId: Int, val icon: ImageVector) {
    data object Sources: TabScreen(titleResId = R.string.tabscreen_sources, Icons.Default.Favorite)
    data object Titles: TabScreen(titleResId = R.string.tabscreen_titles, Icons.Rounded.Menu)
    data object Settings: TabScreen(titleResId = R.string.tabscreen_settings, Icons.Default.Settings)
}

@Composable
fun MyBottomBar(
    selectedTab: TabScreen,
    onTabSelected: (TabScreen) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    containerShape: CornerBasedShape = Shapes.large,
    indicatorShape: CornerBasedShape = Shapes.large,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    val selectedIndex = tabs.indexOf(selectedTab)
    val density = LocalDensity.current

    var pressedIndex by remember { mutableIntStateOf(-1) }

    var totalWidthPx by remember { mutableFloatStateOf(1f) }

    val containerHeight by animateDpAsState(
        targetValue = if (compact) 50.dp else 70.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ContainerHeight"
    )

    val itemWidths = remember { mutableStateListOf<Dp>().apply { addAll(List(tabs.size) { 0.dp }) } }

    fun getIndexForOffset(x: Float): Int {
        val rawIndex = (x / totalWidthPx * tabs.size).toInt()
        return rawIndex.coerceIn(0, tabs.lastIndex)
    }

    val targetIndicatorWidth = if (itemWidths.isNotEmpty() && selectedIndex in itemWidths.indices) {
        itemWidths[selectedIndex]
    } else {
        0.dp
    }

    val targetIndicatorOffset = if (itemWidths.isNotEmpty() && selectedIndex in itemWidths.indices) {
        itemWidths.take(selectedIndex).fold(0.dp) { acc, dp -> acc + dp }
    } else {
        0.dp
    }

    val springSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioLowBouncy
    )

    val indicatorWidth by animateDpAsState(targetIndicatorWidth, animationSpec = springSpec, label = "IndW")
    val indicatorOffset by animateDpAsState(targetIndicatorOffset, animationSpec = springSpec, label = "IndOff")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            .height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clip(containerShape)
                .background(backgroundColor)
                .align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .fillMaxHeight(0.82f)
                .width(indicatorWidth)
                .align(Alignment.CenterStart)
                .offset(x = indicatorOffset)
                .padding(vertical = 1.dp)
                .clip(indicatorShape)
                .background(indicatorColor)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { totalWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downIndex = getIndexForOffset(down.position.x)

                        pressedIndex = downIndex

                        var lastSentIndex = downIndex

                        currentOnTabSelected(tabs[downIndex])

                        var change = down
                        while (change.pressed) {
                            val event = awaitPointerEvent()
                            val currentChange = event.changes.firstOrNull() ?: break
                            change = currentChange

                            val newIndex = getIndexForOffset(change.position.x)

                            if (pressedIndex != newIndex) {
                                pressedIndex = newIndex
                            }

                            if (newIndex != lastSentIndex) {
                                currentOnTabSelected(tabs[newIndex])
                                lastSentIndex = newIndex
                            }
                        }

                        pressedIndex = -1
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                BottomBarButton(
                    icon = tab.icon,
                    label = stringResource(id = tab.titleResId),
                    isSelected = index == selectedIndex,
                    isPressed = index == pressedIndex,
                    compact = compact,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { size ->
                            val widthDp = with(density) { size.width.toDp() }
                            if (index < itemWidths.size) itemWidths[index] = widthDp
                        }
                )
            }
        }
    }
}

@Composable
fun BottomBarButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isPressed: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "ContentColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ButtonPressScale"
    )

    val iconSelectionScale by animateFloatAsState(
        targetValue = if (isSelected && !compact) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "IconSelectionScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        if (compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(iconSelectionScale)
                )

                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(tween(150)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(100)) + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}