package com.rds.mews.ui.custom_elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.rds.mews.ArrowPosition

class CenteredPopupPositionProvider(
    private val inputBounds: IntRect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = inputBounds.left + inputBounds.width / 2
        val anchorCenterY = inputBounds.top + inputBounds.height / 2

        val popupX = anchorCenterX - popupContentSize.width / 2
        val popupY = anchorCenterY - popupContentSize.height / 2

        return IntOffset(popupX, popupY)
    }
}

class BubbleShape(
    private val cornerRadius: Dp = 8.dp,
    private val arrowWidth: Dp = 16.dp,
    private val arrowHeight: Dp = 8.dp,
    private val arrowPosition: ArrowPosition = ArrowPosition.TopCenter,
    private val arrowOffset: Dp = 16.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }
        val arrowWidthPx = with(density) { arrowWidth.toPx() }
        val arrowHeightPx = with(density) { arrowHeight.toPx() }
        val arrowOffsetPx = with(density) { arrowOffset.toPx() }

        val bodyRect = when (arrowPosition) {
            ArrowPosition.TopLeft, ArrowPosition.TopCenter, ArrowPosition.TopRight ->
                Rect(0f, arrowHeightPx, size.width, size.height)
            ArrowPosition.BottomLeft, ArrowPosition.BottomCenter, ArrowPosition.BottomRight ->
                Rect(0f, 0f, size.width, size.height - arrowHeightPx)
            ArrowPosition.LeftTop, ArrowPosition.LeftCenter, ArrowPosition.LeftBottom ->
                Rect(arrowHeightPx, 0f, size.width, size.height)
            ArrowPosition.RightTop, ArrowPosition.RightCenter, ArrowPosition.RightBottom ->
                Rect(0f, 0f, size.width - arrowHeightPx, size.height)
        }

        val bodyPath = Path().apply {
            addRoundRect(RoundRect(bodyRect, cornerRadiusPx, cornerRadiusPx))
        }

        val arrowPath = Path().apply {
            when (arrowPosition) {
                ArrowPosition.TopCenter -> {
                    val arrowX = size.width / 2
                    moveTo(arrowX, 0f)
                    lineTo(arrowX + arrowWidthPx / 2, arrowHeightPx)
                    lineTo(arrowX - arrowWidthPx / 2, arrowHeightPx)
                }
                ArrowPosition.TopLeft -> {
                    val arrowX = arrowOffsetPx + arrowWidthPx / 2
                    moveTo(arrowX, 0f)
                    lineTo(arrowX + arrowWidthPx / 2, arrowHeightPx)
                    lineTo(arrowX - arrowWidthPx / 2, arrowHeightPx)
                }
                ArrowPosition.TopRight -> {
                    val arrowX = size.width - arrowOffsetPx - arrowWidthPx / 2
                    moveTo(arrowX, 0f)
                    lineTo(arrowX + arrowWidthPx / 2, arrowHeightPx)
                    lineTo(arrowX - arrowWidthPx / 2, arrowHeightPx)
                }

                ArrowPosition.BottomCenter -> {
                    val arrowX = size.width / 2
                    moveTo(arrowX, size.height)
                    lineTo(arrowX - arrowWidthPx / 2, size.height - arrowHeightPx)
                    lineTo(arrowX + arrowWidthPx / 2, size.height - arrowHeightPx)
                }
                ArrowPosition.BottomLeft -> {
                    val arrowX = arrowOffsetPx + arrowWidthPx / 2
                    moveTo(arrowX, size.height)
                    lineTo(arrowX - arrowWidthPx / 2, size.height - arrowHeightPx)
                    lineTo(arrowX + arrowWidthPx / 2, size.height - arrowHeightPx)
                }
                ArrowPosition.BottomRight -> {
                    val arrowX = size.width - arrowOffsetPx - arrowWidthPx / 2
                    moveTo(arrowX, size.height)
                    lineTo(arrowX - arrowWidthPx / 2, size.height - arrowHeightPx)
                    lineTo(arrowX + arrowWidthPx / 2, size.height - arrowHeightPx)
                }

                ArrowPosition.LeftCenter -> {
                    val arrowY = size.height / 2
                    moveTo(0f, arrowY)
                    lineTo(arrowHeightPx, arrowY - arrowWidthPx / 2)
                    lineTo(arrowHeightPx, arrowY + arrowWidthPx / 2)
                }
                ArrowPosition.LeftTop -> {
                    val arrowY = arrowOffsetPx + arrowWidthPx / 2
                    moveTo(0f, arrowY)
                    lineTo(arrowHeightPx, arrowY - arrowWidthPx / 2)
                    lineTo(arrowHeightPx, arrowY + arrowWidthPx / 2)
                }
                ArrowPosition.LeftBottom -> {
                    val arrowY = size.height - arrowOffsetPx - arrowWidthPx / 2
                    moveTo(0f, arrowY)
                    lineTo(arrowHeightPx, arrowY - arrowWidthPx / 2)
                    lineTo(arrowHeightPx, arrowY + arrowWidthPx / 2)
                }

                ArrowPosition.RightCenter -> {
                    val arrowY = size.height / 2
                    moveTo(size.width, arrowY)
                    lineTo(size.width - arrowHeightPx, arrowY + arrowWidthPx / 2)
                    lineTo(size.width - arrowHeightPx, arrowY - arrowWidthPx / 2)
                }
                ArrowPosition.RightTop -> {
                    val arrowY = arrowOffsetPx + arrowWidthPx / 2
                    moveTo(size.width, arrowY)
                    lineTo(size.width - arrowHeightPx, arrowY + arrowWidthPx / 2)
                    lineTo(size.width - arrowHeightPx, arrowY - arrowWidthPx / 2)
                }
                ArrowPosition.RightBottom -> {
                    val arrowY = size.height - arrowOffsetPx - arrowWidthPx / 2
                    moveTo(size.width, arrowY)
                    lineTo(size.width - arrowHeightPx, arrowY + arrowWidthPx / 2)
                    lineTo(size.width - arrowHeightPx, arrowY - arrowWidthPx / 2)
                }
            }
            close()
        }

        bodyPath.op(bodyPath, arrowPath, PathOperation.Union)

        return Outline.Generic(bodyPath)
    }
}

@Composable
fun CustomDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<Pair<String, () -> Unit>>,
    animDuration: Int = 200,
    timeList: Boolean = false,
    arrowPosition: ArrowPosition = ArrowPosition.BottomCenter
) {
    val surfaceWidth = if (timeList) 70.dp else 150.dp
    val maxHeight = if (timeList) 180.dp else 360.dp
    val arrowHeight = 8.dp

    val bubbleShape = BubbleShape(
        cornerRadius = 8.dp,
        arrowPosition = arrowPosition,
        arrowHeight = arrowHeight,
        arrowWidth = 16.dp,
        arrowOffset = 16.dp
    )

    val padding = when(arrowPosition) {
        ArrowPosition.TopLeft, ArrowPosition.TopCenter, ArrowPosition.TopRight ->
            PaddingValues(top = arrowHeight)
        ArrowPosition.BottomLeft, ArrowPosition.BottomCenter, ArrowPosition.BottomRight ->
            PaddingValues(bottom = arrowHeight)
        ArrowPosition.LeftTop, ArrowPosition.LeftCenter, ArrowPosition.LeftBottom ->
            PaddingValues(start = arrowHeight)
        ArrowPosition.RightTop, ArrowPosition.RightCenter, ArrowPosition.RightBottom ->
            PaddingValues(end = arrowHeight)
    }

    Surface(
        modifier = Modifier
            .width(surfaceWidth)
            .heightIn(max = maxHeight),
        shape = bubbleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = expandVertically(
                expandFrom = Alignment.CenterVertically,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeOut()
        ) {
            val onDismiss = remember(transitionState) { { transitionState.targetState = false } }
            LazyColumn(
                modifier = Modifier.padding(padding)
            ) {
                buttons.forEachIndexed { index, button ->
                    item { CustomDropdownMenuItem(button.first, button.second, onDismiss = onDismiss) }
                    if (index < buttons.size - 1) {
                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 4.dp)
            .clickable {
                onClick()
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}