package com.rds.mews.ui.custom_elements

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.rds.mews.localcore.ArrowPosition
import kotlin.math.roundToInt

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

class BubblePopupPositionProvider(
    private val anchorBounds: IntRect,
    private val arrowPosition: ArrowPosition,
    private val density: Density,
    private val arrowWidth: Dp = 16.dp,
    private val arrowOffset: Dp = 16.dp
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val arrowWidthPx: Float
        val arrowOffsetPx: Float
        with (density) {
            arrowWidthPx = arrowWidth.toPx()
            arrowOffsetPx = arrowOffset.toPx()
        }

        val anchorCenterX = this.anchorBounds.left + this.anchorBounds.width / 2f
        val anchorCenterY = this.anchorBounds.top + this.anchorBounds.height / 2f

        // Выполняем все вычисления в Float для точности
        val popupX: Float
        val popupY: Float

        when (arrowPosition) {
            // Стрелка сверху
            ArrowPosition.TopCenter -> {
                popupX = anchorCenterX - popupContentSize.width / 2f
                popupY = anchorCenterY
            }
            ArrowPosition.TopLeft -> {
                popupX = anchorCenterX - arrowOffsetPx - (arrowWidthPx / 2f)
                popupY = anchorCenterY
            }
            ArrowPosition.TopRight -> {
                popupX = anchorCenterX - popupContentSize.width + arrowOffsetPx + (arrowWidthPx / 2f)
                popupY = anchorCenterY
            }

            // Стрелка снизу
            ArrowPosition.BottomCenter -> {
                popupX = anchorCenterX - popupContentSize.width / 2f
                popupY = anchorCenterY - popupContentSize.height
            }
            ArrowPosition.BottomLeft -> {
                popupX = anchorCenterX - arrowOffsetPx - (arrowWidthPx / 2f)
                popupY = anchorCenterY - popupContentSize.height
            }
            ArrowPosition.BottomRight -> {
                popupX = anchorCenterX - popupContentSize.width + arrowOffsetPx + (arrowWidthPx / 2f)
                popupY = anchorCenterY - popupContentSize.height
            }

            // Стрелка слева
            ArrowPosition.LeftCenter -> {
                popupX = anchorCenterX
                popupY = anchorCenterY - popupContentSize.height / 2f
            }
            ArrowPosition.LeftTop -> {
                popupX = anchorCenterX
                popupY = anchorCenterY - arrowOffsetPx - (arrowWidthPx / 2f)
            }
            ArrowPosition.LeftBottom -> {
                popupX = anchorCenterX
                popupY = anchorCenterY - popupContentSize.height + arrowOffsetPx + (arrowWidthPx / 2f)
            }

            // Стрелка справа
            ArrowPosition.RightCenter -> {
                popupX = anchorCenterX - popupContentSize.width
                popupY = anchorCenterY - popupContentSize.height / 2f
            }
            ArrowPosition.RightTop -> {
                popupX = anchorCenterX - popupContentSize.width
                popupY = anchorCenterY - arrowOffsetPx - (arrowWidthPx / 2f)
            }
            ArrowPosition.RightBottom -> {
                popupX = anchorCenterX - popupContentSize.width
                popupY = anchorCenterY - popupContentSize.height + arrowOffsetPx + (arrowWidthPx / 2f)
            }

            // Стрелка выключена
            ArrowPosition.None -> {
                popupX = anchorCenterX
                popupY = anchorCenterY
            }
        }

        // Округляем до ближайшего целого числа перед возвратом IntOffset
        return IntOffset(popupX.roundToInt(), popupY.roundToInt())
    }
}