package com.rds.mews.ui.custom_elements

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.localcore.ScreenQuadrant
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.getScreenQuadrant
import com.rds.mews.ui.theme.Shapes
import androidx.compose.material3.ripple

class BubbleShape(
    private val cornerShape: CornerBasedShape,
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
            ArrowPosition.None -> Rect(0f, 0f, size.width, size.height)
        }

        val bodyPath = Path().apply {
            val topStartPx = cornerShape.topStart.toPx(bodyRect.size, density)
            val topEndPx = cornerShape.topEnd.toPx(bodyRect.size, density)
            val bottomEndPx = cornerShape.bottomEnd.toPx(bodyRect.size, density)
            val bottomStartPx = cornerShape.bottomStart.toPx(bodyRect.size, density)

            addRoundRect(
                RoundRect(
                    rect = bodyRect,
                    topLeft = CornerRadius(topStartPx),
                    topRight = CornerRadius(topEndPx),
                    bottomRight = CornerRadius(bottomEndPx),
                    bottomLeft = CornerRadius(bottomStartPx)
                )
            )
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
                ArrowPosition.None -> {}
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
    buttons: List<TextButtonInputs>,
    inputBounds: IntRect?,
    config: Configuration,
    density: Density,
    onDismissRequest: () -> Unit,
    animDuration: Int = 200,
    centeredArrow: Boolean? = null,
    noArrow: Boolean = false,
    timeList: Boolean = false,
    arrowPosition: ArrowPosition? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    if (inputBounds != null) {
        val arrowPosition = when {
            arrowPosition != null -> arrowPosition
            noArrow || timeList -> ArrowPosition.None
            centeredArrow ?: false -> when (getScreenQuadrant(config, inputBounds)) {
                ScreenQuadrant.TopLeft, ScreenQuadrant.TopRight -> ArrowPosition.TopCenter
                else -> ArrowPosition.BottomCenter
            }

            else -> getArrowPosByBounds(config, inputBounds)
        }

        Popup(
            popupPositionProvider = if (!timeList )BubblePopupPositionProvider(
                inputBounds,
                arrowPosition,
                density
            ) else CenteredPopupPositionProvider(inputBounds),
            onDismissRequest = onDismissRequest
        ) {
            NPDropdown(
                transitionState = transitionState,
                buttons = buttons,
                animDuration = animDuration,
                arrowPosition = arrowPosition,
                timeList = timeList,
                surfaceColor = backgroundColor
            )
        }
    }
}

@Composable
private fun NPDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<TextButtonInputs>,
    animDuration: Int = 200,
    timeList: Boolean = false,
    arrowPosition: ArrowPosition = ArrowPosition.None,
    surfaceColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val surfaceWidth = if (timeList) 70.dp else 200.dp
    val maxHeight = if (timeList) 180.dp else 360.dp
    val arrowHeight = 8.dp
    val arrowWidth = 16.dp
    val arrowOffset = 16.dp

    val bubbleShape = remember(arrowPosition) {
        BubbleShape(
            cornerShape = Shapes.medium,
            arrowPosition = arrowPosition,
            arrowHeight = arrowHeight,
            arrowWidth = arrowWidth,
            arrowOffset = arrowOffset
        )
    }

    val padding = when (arrowPosition) {
        ArrowPosition.TopLeft, ArrowPosition.TopCenter, ArrowPosition.TopRight -> PaddingValues(top = arrowHeight)
        ArrowPosition.BottomLeft, ArrowPosition.BottomCenter, ArrowPosition.BottomRight -> PaddingValues(bottom = arrowHeight)
        ArrowPosition.LeftTop, ArrowPosition.LeftCenter, ArrowPosition.LeftBottom -> PaddingValues(start = arrowHeight)
        ArrowPosition.RightTop, ArrowPosition.RightCenter, ArrowPosition.RightBottom -> PaddingValues(end = arrowHeight)
        else -> PaddingValues()
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn(animationSpec = tween(durationMillis = 50)),
        exit = fadeOut(animationSpec = tween(durationMillis = animDuration))
    ) {
        val transition = this.transition

        val progress by transition.animateFloat(
            transitionSpec = {
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            },
            label = "Progress"
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

        val surfaceColor = surfaceColor

        Surface(
            modifier = Modifier
                .wrapContentSize()
                .widthIn(
                    min = if (timeList) surfaceWidth else 20.dp,
                    max = surfaceWidth
                )
                .heightIn(max = maxHeight)
                .graphicsLayer {
                    val currentProgress = progress.coerceAtLeast(0f)

                    scaleX = currentProgress
                    scaleY = currentProgress
                    this.alpha = progress.coerceIn(0f, 1f)

                    val aOffsetPx = arrowOffset.toPx()
                    val aWidthPx = arrowWidth.toPx()
                    val w = size.width
                    val h = size.height

                    if (w > 0 && h > 0) {
                        val (pivotX, pivotY) = when (arrowPosition) {
                            ArrowPosition.TopCenter -> 0.5f to 0f
                            ArrowPosition.TopLeft -> (aOffsetPx + aWidthPx / 2) / w to 0f
                            ArrowPosition.TopRight -> (w - aOffsetPx - aWidthPx / 2) / w to 0f

                            ArrowPosition.BottomCenter -> 0.5f to 1f
                            ArrowPosition.BottomLeft -> (aOffsetPx + aWidthPx / 2) / w to 1f
                            ArrowPosition.BottomRight -> (w - aOffsetPx - aWidthPx / 2) / w to 1f

                            ArrowPosition.LeftCenter -> 0f to 0.5f
                            ArrowPosition.LeftTop -> 0f to (aOffsetPx + aWidthPx / 2) / h
                            ArrowPosition.LeftBottom -> 0f to (h - aOffsetPx - aWidthPx / 2) / h

                            ArrowPosition.RightCenter -> 1f to 0.5f
                            ArrowPosition.RightTop -> 1f to (aOffsetPx + aWidthPx / 2) / h
                            ArrowPosition.RightBottom -> 1f to (h - aOffsetPx - aWidthPx / 2) / h

                            ArrowPosition.None -> 0.5f to 0.5f
                        }
                        transformOrigin = TransformOrigin(pivotX, pivotY)
                    }
                },
            shape = bubbleShape,
            color = if (!timeList) surfaceColor.copy(alpha = 0.97f) else surfaceColor,
            shadowElevation = 0.dp
        ) {
            val onDismiss = remember(transitionState) { { transitionState.targetState = false } }

            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                buttons.forEachIndexed { index, inputs ->
                    NPDropdownMenuItem(
                        inputs = inputs,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateEnterExit(
                                enter = slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(
                                        durationMillis = animDuration,
                                        delayMillis = index * 30
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = animDuration,
                                        delayMillis = index * 30
                                    )
                                ),
                                exit = fadeOut(animationSpec = tween(50))
                            )
                    )
                    if (index < buttons.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface,
                            thickness = 0.15.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NPDropdownMenuItem(
    inputs: TextButtonInputs,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    CustomTextButton(
        inputs = inputs.copy(
            action = {
                inputs.action()
                onDismiss()
            }
        ),
        modifier = modifier.wrapContentHeight(),
        textModifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        defaultBackgroundColor = Color.Transparent,
        verticalPadding = 0.dp,
        horizontalPadding = 0.dp,
        indication = ripple(bounded = true),
        shape = Shapes.large
    )
}

private fun getArrowPosByBounds(config: Configuration, bounds: IntRect): ArrowPosition {
    val screenQuadrant = getScreenQuadrant(config, bounds)

    return when (screenQuadrant) {
        ScreenQuadrant.TopLeft -> ArrowPosition.TopLeft
        ScreenQuadrant.TopRight -> ArrowPosition.TopRight
        ScreenQuadrant.BottomLeft -> ArrowPosition.BottomLeft
        ScreenQuadrant.BottomRight -> ArrowPosition.BottomRight
    }
}