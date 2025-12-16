package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rds.mews.ArrowPosition
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DropdownButton(
    buttons: List<Pair<String, () -> Unit>>,
    density: Density,
    modifier: Modifier = Modifier,
    initialSelectedIndex: Int = 0,
    maxVisibleItems: Int = 5,
    width: Dp = 150.dp,
    animDuration: Int = 200,
    cornerShape: CornerBasedShape = Shapes.medium
) {
    var isOpen by remember { mutableStateOf(false) }

    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = isOpen

    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, buttons.lastIndex)) }

    LaunchedEffect(initialSelectedIndex, buttons) {
        selectedIndex = initialSelectedIndex.coerceIn(0, buttons.lastIndex)
    }

    val currentText = buttons.getOrNull(selectedIndex)?.first ?: ""
    val textStyle = MaterialTheme.typography.bodyLarge
    val verticalTextPadding = 12.dp

    val textMeasurer = rememberTextMeasurer()
    val dimensions = remember(density, textStyle, maxVisibleItems, buttons.size) {
        with(density) {
            val textLayoutResult = textMeasurer.measure(text = "Tp", style = textStyle)
            val itemHeightPx = textLayoutResult.size.height + (verticalTextPadding.toPx() * 2)
            val itemHeightDp = itemHeightPx.toDp()

            val visibleCount = buttons.size.coerceAtMost(maxVisibleItems)
            val listHeightPx = itemHeightPx * visibleCount
            val listHeightDp = listHeightPx.toDp()

            val listContentPaddingPx = (listHeightPx - itemHeightPx) / 2
            val listContentPaddingDp = listContentPaddingPx.toDp()

            DropdownDimensions(itemHeightDp, listHeightDp, listContentPaddingDp)
        }
    }

    val shape = remember { BubbleShape(cornerShape = cornerShape, arrowPosition = ArrowPosition.None) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = MaterialTheme.colorScheme.onSurface

    fun startClose() {
        isOpen = false
    }

    Box(
        modifier = modifier
            .width(width)
            .height(dimensions.itemHeightDp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = shape,
            color = surfaceColor.copy(alpha = 0.97f),
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isOpen = true
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = currentText,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }


        if (isOpen || transitionState.currentState || !transitionState.isIdle) {
            val popupScope = rememberCoroutineScope()

            Popup(
                alignment = Alignment.Center,
                properties = PopupProperties(clippingEnabled = false),
                onDismissRequest = { startClose() }
            ) {
                val transition = rememberTransition(transitionState, label = "DropdownTransition")

                val animHeight by transition.animateDp(
                    transitionSpec = {
                        if (targetState) {
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        } else {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        }
                    },
                    label = "Height"
                ) { if (it) dimensions.listHeightDp else dimensions.itemHeightDp }

                val animAlpha by transition.animateFloat(
                    transitionSpec = {
                        if (targetState) tween(50) else tween(animDuration)
                    },
                    label = "Alpha"
                ) { if (it) 1f else 0f }

                val revealProgress by transition.animateFloat(
                    transitionSpec = { tween(animDuration) },
                    label = "Reveal"
                ) { if (it) 1f else 0f }

                Box(
                    modifier = Modifier
                        .width(width)
                        .height(dimensions.listHeightDp)
                        .clip(remember(animHeight, cornerShape) {
                            CenterRevealShape(animHeight, cornerShape, density)
                        })
                        .alpha(animAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = surfaceColor.copy(alpha = 0.98f),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val listState = rememberLazyListState(
                                initialFirstVisibleItemIndex = selectedIndex
                            )
                            val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                            var hasUserScrolled by remember { mutableStateOf(false) }

                            val isDragged by listState.interactionSource.collectIsDraggedAsState()
                            val isScrolling = listState.isScrollInProgress

                            LaunchedEffect(isDragged, isScrolling) {
                                if (isDragged || isScrolling) hasUserScrolled = true
                            }

                            val layoutInfo by remember { derivedStateOf { listState.layoutInfo } }
                            val centerItemIndex by remember {
                                derivedStateOf {
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    if (visibleItems.isEmpty()) return@derivedStateOf -1
                                    val viewportCenter = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                                    visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index
                                        ?: -1
                                }
                            }

                            LaunchedEffect(listState) {
                                snapshotFlow { listState.isScrollInProgress }
                                    .distinctUntilChanged()
                                    .collectLatest { scrollInProgress ->
                                        if (!scrollInProgress && hasUserScrolled) {
                                            val index = centerItemIndex
                                            if (index != -1 && index != selectedIndex) {
                                                selectedIndex = index
                                                buttons.getOrNull(index)?.second?.invoke()
                                            }
                                        }
                                    }
                            }

                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(vertical = dimensions.listContentPaddingDp),
                                flingBehavior = flingBehavior,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(buttons) { index, item ->
                                    val isCentered = if (hasUserScrolled) index == centerItemIndex else index == selectedIndex

                                    val focusScale by animateFloatAsState(
                                        targetValue = if (isCentered) 1f else 0.85f,
                                        label = "FocusScale"
                                    )
                                    val focusAlpha by animateFloatAsState(
                                        targetValue = if (isCentered) 1f else 0.5f,
                                        label = "FocusAlpha"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .height(dimensions.itemHeightDp)
                                            .fillMaxWidth()
                                            .clickable {
                                                popupScope.launch {
                                                    selectedIndex = index
                                                    item.second()
                                                    listState.animateScrollToItem(index)
                                                    delay(100)
                                                    startClose()
                                                }
                                            }
                                            .graphicsLayer {
                                                val startOffset = (selectedIndex - index) * 15.dp.toPx()
                                                translationY = startOffset * (1f - revealProgress)

                                                scaleX = focusScale
                                                scaleY = focusScale

                                                val distance = index - selectedIndex
                                                val itemRevealAlpha = if (distance == 0) 1f else revealProgress

                                                alpha = focusAlpha * itemRevealAlpha
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.first,
                                            style = textStyle,
                                            textAlign = TextAlign.Center,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = dimensions.listContentPaddingDp, bottom = 8.dp)
                                    .alpha(revealProgress),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thickness = 1.dp
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = dimensions.listContentPaddingDp, top = 8.dp)
                                    .alpha(revealProgress),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}


private class CenterRevealShape(
    val currentHeight: Dp,
    val cornerShape: CornerBasedShape,
    val density: Density
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val currentHeightPx = with(density) { currentHeight.toPx() }

        val verticalOffset = (size.height - currentHeightPx) / 2

        if (verticalOffset >= size.height / 2) {
            return Outline.Rounded(RoundRect(0f, size.height/2, size.width, size.height/2, CornerRadius.Zero))
        }

        val cornerRadiusPx = cornerShape.topStart.toPx(size, density)

        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = verticalOffset,
                right = size.width,
                bottom = size.height - verticalOffset,
                cornerRadius = CornerRadius(cornerRadiusPx)
            )
        )
    }
}

private data class DropdownDimensions(
    val itemHeightDp: Dp,
    val listHeightDp: Dp,
    val listContentPaddingDp: Dp
)