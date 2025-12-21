package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rds.mews.ui.theme.Shapes

@Composable
fun AnimatedSegmentedControl(
    items: List<Pair<String, () -> Unit>>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    isIndicatorVisible: Boolean = true,
    cornerShape: CornerBasedShape = Shapes.large,
    itemPadding: Dp = 16.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    textColorNormal: Color = MaterialTheme.colorScheme.onSurface,
    textColorSelected: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val density = LocalDensity.current

    val itemWidths = remember { mutableStateListOf<Dp>().apply { addAll(List(items.size) { 0.dp }) } }

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

    val indicatorWidth by animateDpAsState(
        targetValue = targetIndicatorWidth,
        animationSpec = tween(durationMillis = 300),
        label = "Indicator Width"
    )

    val indicatorOffset by animateDpAsState(
        targetValue = targetIndicatorOffset,
        animationSpec = tween(durationMillis = 300),
        label = "Indicator Offset"
    )

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isIndicatorVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "Indicator Alpha"
    )

    Box(
        modifier = modifier
            .clip(cornerShape)
            .background(backgroundColor)
            .heightIn(min = 35.dp)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .alpha(indicatorAlpha)
                .padding(4.dp)
                .clip(cornerShape)
                .background(indicatorColor)
        )

        Row(
            modifier = Modifier.wrapContentSize()
        ) {
            items.forEachIndexed { index, item ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            item.second()
                        }
                        .onSizeChanged { size ->
                            val widthDp = with(density) { size.width.toDp() }
                            if (index < itemWidths.size) {
                                itemWidths[index] = widthDp
                            }
                        }
                        .padding(horizontal = itemPadding, vertical = 4.dp)
                ) {
                    Text(
                        text = item.first,
                        color = if (index == selectedIndex && isIndicatorVisible) textColorSelected else textColorNormal,
                        fontWeight = if (index == selectedIndex && isIndicatorVisible) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}