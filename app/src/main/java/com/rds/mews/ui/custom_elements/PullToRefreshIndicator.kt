package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.ui.theme.Shapes
import com.rds.mews.R
import androidx.compose.foundation.layout.Spacer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    statusText: String,
    progress: Float,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    onCancellation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedHeight = 50.dp
    val collapsedHeight = 8.dp

    val animatedHeight by animateDpAsState(
        targetValue = if (isCollapsed) collapsedHeight else expandedHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "heightSpring"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "textAlphaSpring"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "progressSpring"
    )

    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }

    val currentTranslationY = if (isRefreshing) {
        0f
    } else {
        (state.distanceFraction * thresholdPx) - thresholdPx
    }

    val indicatorAlpha = if (isRefreshing) 1f else (state.distanceFraction * 2f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = currentTranslationY
                alpha = indicatorAlpha
            }
            .padding(horizontal = 8.dp)
            .padding(top = if (!isCollapsed) 1.dp else 0.dp)
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .clip(Shapes.large)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCollapseChange(!isCollapsed) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val (_, y) = dragAmount

                        if (y < -15) {
                            onCollapseChange(true)
                        } else if (y > 15) {
                            onCollapseChange(false)
                        }
                    }
                },
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )
                }

                if (textAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = textAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            val toastText = stringResource(R.string.update_cancelled)
                            CustomIconButton(
                                inputs = IconButtonInputs(
                                    icon = Icons.Default.Clear,
                                    action = onCancellation,
                                    toast = toastText
                                ),
                                defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }

                    }
                }
            }
        }
    }
}