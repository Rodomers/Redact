package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
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
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes

@Composable
fun AnimatedSegmentedControl(
    items: List<Pair<String, () -> Unit>>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerShape: CornerBasedShape = Shapes.large,
    itemPadding: Dp = 0.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer
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

    val animationSpec = spring<Dp>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioLowBouncy
    )

    val indicatorWidth by animateDpAsState(
        targetValue = targetIndicatorWidth,
        animationSpec = animationSpec,
        label = "Indicator Width"
    )

    val indicatorOffset by animateDpAsState(
        targetValue = targetIndicatorOffset,
        animationSpec = animationSpec,
        label = "Indicator Offset"
    )

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
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
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                CustomTextButton(
                    inputs = TextButtonInputs(item.first, item.second),
                    modifier = Modifier
                        .wrapContentSize()
                        .onSizeChanged { size ->
                            val widthDp = with(density) { size.width.toDp() }
                            if (index < itemWidths.size) {
                                itemWidths[index] = widthDp
                            }
                        }
                        .padding(horizontal = itemPadding),
                    fontWeight = if (index == selectedIndex && enabled) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    enabled = enabled,
                    verticalPadding = 5.dp,
                    horizontalPadding = 12.dp
                )
            }
        }
    }
}