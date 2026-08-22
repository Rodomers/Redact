package com.rds.mews.ui.custom_elements.titles_card

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.theme.Shapes

@Composable
fun StatusTimeBadge(
    eventTime: Long,
    isRead: Boolean,
    isPinned: Boolean,
    dateString: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val targetTimeWeight = if (isRead) 400 else 700
    val targetAlpha = if (isRead) 0.6f else 1.0f
    val targetDotSize = if (isRead) 0.dp else 8.dp
    val targetDotAlpha = if (isRead) 0f else 1f
    val targetSpacerWidth = if (isRead) 0.dp else 6.dp

    val targetPinSize = if (isPinned) 14.dp else 0.dp
    val targetPinAlpha = if (isPinned) 1f else 0f
    val targetPinSpacerWidth = if (isPinned) 4.dp else 0.dp

    val timeWeight by animateIntAsState(
        targetValue = targetTimeWeight,
        label = "statusTimeBadgeTimeWeight"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        label = "statusTimeBadgeContentAlpha"
    )
    val dotSize by animateDpAsState(
        targetValue = targetDotSize,
        label = "statusTimeBadgeDotSize"
    )
    val dotAlpha by animateFloatAsState(
        targetValue = targetDotAlpha,
        label = "statusTimeBadgeDotAlpha"
    )
    val spacerWidth by animateDpAsState(
        targetValue = targetSpacerWidth,
        label = "statusTimeBadgeSpacerWidth"
    )
    val pinSize by animateDpAsState(
        targetValue = targetPinSize,
        label = "statusTimeBadgePinSize"
    )
    val pinAlpha by animateFloatAsState(
        targetValue = targetPinAlpha,
        label = "statusTimeBadgePinAlpha"
    )
    val pinSpacerWidth by animateDpAsState(
        targetValue = targetPinSpacerWidth,
        label = "statusTimeBadgePinSpacerWidth"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, Shapes.large)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (!isRead || dotSize > 0.dp) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer)
            )
            Spacer(modifier = Modifier.width(spacerWidth))
        }

        if (isPinned || pinSize > 0.dp) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                modifier = Modifier
                    .size(pinSize)
                    .alpha(pinAlpha)
            )
            Spacer(modifier = Modifier.width(pinSpacerWidth))
        }

        Text(
            text = if (dateString != null) "$dateString ${getFormattedTimeUnix(eventTime)}"
                    else getFormattedTimeUnix(eventTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight(timeWeight),
            textAlign = TextAlign.Left
        )
    }
}