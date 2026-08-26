package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rds.mews.R
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed class TabScreen(@StringRes val titleResId: Int, val icon: ImageVector) {
    data object Sources: TabScreen(titleResId = R.string.tabscreen_sources, Icons.Default.Favorite)
    data object Titles: TabScreen(titleResId = R.string.tabscreen_titles, Icons.Rounded.Menu)
    data object Settings: TabScreen(titleResId = R.string.tabscreen_settings, Icons.Default.Settings)
}

private data class TooltipMessage(
    @StringRes val textRes: Int,
    val durationMs: Long = 3000L
)

@SuppressLint("UseOfNonLambdaOffsetOverload")
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
    onHoldProgressChanged: (progress: Float, centerOffset: Offset, isHolding: Boolean) -> Unit = { _, _, _ -> },
    onBlitzTriggered: (centerOffset: Offset) -> Unit = {},
    isBlitzActive: Boolean = false,
    showBlitzTooltip: Boolean = false,
    isOnline: Boolean? = null
) {
    val tooltipChannel = remember { Channel<TooltipMessage>(Channel.UNLIMITED) }
    var currentTooltipTextId by remember { mutableIntStateOf(0) }
    var previousIsOnline by remember { mutableStateOf<Boolean?>(null) }

    val tabs = remember { listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings) }
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnHoldProgressChanged by rememberUpdatedState(onHoldProgressChanged)
    val currentOnBlitzTriggered by rememberUpdatedState(onBlitzTriggered)
    val currentSelectedTab by rememberUpdatedState(selectedTab)

    val selectedIndex = tabs.indexOf(selectedTab)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var pressedIndex by remember { mutableIntStateOf(-1) }
    var totalWidthPx by remember { mutableFloatStateOf(1f) }

    val buttonCenters = remember { mutableStateMapOf<Int, Offset>() }
    val coroutineScope = rememberCoroutineScope()

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

    val tabStretch = remember { Animatable(0f) }
    val tooltipReveal = remember { Animatable(0f) }

    val showTooltip: suspend CoroutineScope.() -> Unit = {
        tabStretch.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        launch {
            tabStretch.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            tooltipReveal.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val hideTooltip: suspend CoroutineScope.() -> Unit = {
        launch {
            tabStretch.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            tabStretch.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
        }
        launch {
            tooltipReveal.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) {
        for (message in tooltipChannel) {
            currentTooltipTextId = message.textRes
            showTooltip()
            delay(message.durationMs.milliseconds)
            hideTooltip()
            delay(200.milliseconds)
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline == null) return@LaunchedEffect
        val previous = previousIsOnline
        previousIsOnline = isOnline

        when (isOnline) {
            false -> {
                tooltipChannel.send(
                    TooltipMessage(
                        textRes = R.string.tooltip_no_network,
                        durationMs = 5000L
                    )
                )
            }
            true -> {
                if (previous == null) return@LaunchedEffect
                tooltipChannel.send(
                    TooltipMessage(
                        textRes = R.string.tooltip_network_restored,
                        durationMs = 2000L
                    )
                )
            }
        }
    }

    LaunchedEffect(showBlitzTooltip) {
        if (showBlitzTooltip) {
            tooltipChannel.send(
                TooltipMessage(
                    textRes = R.string.tabscreen_tooltip_blitz,
                    durationMs = 3000L
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            .height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        val tooltipOffset = (-12).dp - (40.dp * tooltipReveal.value)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = tooltipOffset),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (currentTooltipTextId != 0) {
                TextTooltip(
                    text = stringResource(currentTooltipTextId),
                    revealProgress = tooltipReveal.value,
                    backgroundColor = backgroundColor,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    shape = containerShape
                )
            }
        }

        val baseHeight = containerHeight * 0.72f
        val animatedHeight = baseHeight + (tabStretch.value * 45).dp
        val scaleXValue = 1f + (tabStretch.value * 0.04f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = containerHeight * 0.14f)
                .height(animatedHeight)
                .graphicsLayer {
                    scaleX = scaleXValue
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(containerShape)
                .background(backgroundColor)
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
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downIndex = getIndexForOffset(down.position.x)
                        val targetTab = tabs.getOrNull(downIndex)

                        if (targetTab == TabScreen.Titles) {
                            if (currentSelectedTab == TabScreen.Titles) {
                                pressedIndex = downIndex
                                val centerOffset = buttonCenters[downIndex] ?: Offset.Zero
                                var isHolding = true
                                var holdSuccess = false
                                val startTime = System.currentTimeMillis()

                                val progressJob = coroutineScope.launch {
                                    while (isHolding) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val progress = (elapsed / 250f).coerceIn(0f, 1f)

                                        if (progress >= 1f && !holdSuccess) {
                                            holdSuccess = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }

                                        currentOnHoldProgressChanged(progress, centerOffset, true)
                                        delay(16.milliseconds)
                                    }
                                }

                                var pointer = down
                                while (pointer.pressed) {
                                    val event = awaitPointerEvent()
                                    pointer = event.changes.firstOrNull() ?: break
                                }

                                val totalDuration = System.currentTimeMillis() - startTime
                                isHolding = false
                                progressJob.cancel()

                                pressedIndex = -1
                                currentOnHoldProgressChanged(0f, centerOffset, false)

                                if (holdSuccess) {
                                    currentOnBlitzTriggered(centerOffset)
                                } else if (totalDuration < 200) {
                                    currentOnTabSelected(TabScreen.Titles)
                                }
                            } else {
                                pressedIndex = downIndex
                                currentOnTabSelected(TabScreen.Titles)
                                pressedIndex = -1
                            }
                        } else if (targetTab != null) {
                            pressedIndex = downIndex
                            currentOnTabSelected(targetTab)
                            var lastSentIndex = downIndex

                            var pointer = down
                            while (pointer.pressed) {
                                val event = awaitPointerEvent()
                                pointer = event.changes.firstOrNull() ?: break
                                val newIndex = getIndexForOffset(pointer.position.x)

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
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isBlitzTab = tab == TabScreen.Titles && isBlitzActive
                val displayLabel = if (isBlitzTab) stringResource(R.string.tabscreen_blitz) else stringResource(id = tab.titleResId)
                val displayIcon = if (isBlitzTab) Icons.Rounded.Alarm else tab.icon

                BottomBarButton(
                    icon = displayIcon,
                    label = displayLabel,
                    isSelected = index == selectedIndex,
                    isPressed = index == pressedIndex,
                    compact = compact,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInRoot()
                            buttonCenters[index] = bounds.center
                        }
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