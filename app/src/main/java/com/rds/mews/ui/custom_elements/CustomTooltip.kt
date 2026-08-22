package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rds.mews.ui.theme.Shapes

@Composable
fun BaseTooltip(
    revealProgress: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
    shape: Shape = Shapes.large,
    content: @Composable () -> Unit
) {
    if (revealProgress > 0f) {
        Surface(
            shape = shape,
            color = backgroundColor,
            shadowElevation = 0.dp,
            modifier = modifier
                .graphicsLayer {
                    alpha = (revealProgress * 1.5f).coerceIn(0f, 1f)
                    scaleX = 0.85f + (0.15f * revealProgress)
                    scaleY = 0.85f + (0.15f * revealProgress)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
fun TextTooltip(
    text: String,
    revealProgress: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = Shapes.large
) {
    BaseTooltip(
        revealProgress = revealProgress,
        modifier = modifier,
        backgroundColor = backgroundColor,
        shape = shape
    ) {
        Text(
            text = text,
            modifier = Modifier.wrapContentHeight(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}