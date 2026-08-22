package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.localcore.getFormattedTimeUnix

@Composable
fun TimelineMarker(
    time: Long,
    isFirst: Boolean,
    isLast: Boolean,
    isRead: Boolean,
    isPinned: Boolean,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    topOffset: Dp = 35.dp
) {
    val pinIconSize = 12.dp
    val targetDotRadius = if (isPinned) 9.dp else 5.dp

    val animatedDotRadius by animateDpAsState(
        targetValue = targetDotRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "timelineMarkerDotRadius"
    )

    val pinScale by animateFloatAsState(
        targetValue = if (isPinned) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "timelineMarkerPinScale"
    )

    Row(modifier = modifier.fillMaxHeight()) {
        val backgroundColor = MaterialTheme.colorScheme.surface
        val dotColor = if (isRead) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

        Text(
            text = getFormattedTimeUnix(time).split(":").joinToString("\n"),
            modifier = Modifier
                .width(37.dp)
                .padding(top = 16.dp, end = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            val lineColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotRadius = animatedDotRadius.toPx()
                val lineWidth = 2.dp.toPx()
                val yOffsetPx = topOffset.toPx()
                val centerX = size.width / 2f

                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, yOffsetPx),
                        strokeWidth = lineWidth
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, yOffsetPx),
                        end = Offset(centerX, size.height),
                        strokeWidth = lineWidth
                    )
                }

                if (isRead) {
                    drawCircle(
                        color = backgroundColor,
                        radius = dotRadius,
                        center = Offset(centerX, yOffsetPx)
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, yOffsetPx),
                        style = Stroke(width = lineWidth)
                    )
                } else {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, yOffsetPx)
                    )
                }
            }

            if (pinScale > 0f) {
                val iconTint = if (isRead) dotColor else MaterialTheme.colorScheme.surface

                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = topOffset - (pinIconSize / 2))
                        .size(pinIconSize)
                        .graphicsLayer {
                            scaleX = pinScale
                            scaleY = pinScale
                            alpha = pinScale
                        }
                )
            }
        }
    }
}