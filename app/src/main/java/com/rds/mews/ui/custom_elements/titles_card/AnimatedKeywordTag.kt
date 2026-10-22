package com.rds.mews.ui.custom_elements.titles_card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AnimatedKeywordTag(
    keyword: String,
    index: Int,
    isExpanded: Boolean,
    maxVisibleYPx: Float,
    onBanTheme: (String) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    shouldAnimate: Boolean = true
) {
    val offsetX = remember { Animatable(if (shouldAnimate) 200f else 0f) }
    val alpha = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    var isOffScreen by remember { mutableStateOf(false) }
    var isMeasured by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, isMeasured) {
        if (isExpanded) {
            if (!shouldAnimate || isOffScreen) {
                offsetX.snapTo(0f)
                alpha.snapTo(1f)
            } else {
                delay((index * 50L).milliseconds)
                launch {
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                launch {
                    alpha.animateTo(1f, tween(150))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                if (!isMeasured) {
                    val tagTopInWindow = coordinates.boundsInWindow().top
                    if (tagTopInWindow > maxVisibleYPx) {
                        isOffScreen = true
                    }
                    isMeasured = true
                }
            }
            .graphicsLayer {
                translationX = offsetX.value
                this.alpha = alpha.value
            }
    ) {
        CustomTextButton(
            inputs = TextButtonInputs(
                text = keyword,
                action = {
                    onBanTheme(keyword)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                toast = stringResource(R.string.titles_card_banned)
            ),
            defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = Shapes.large
        )
    }
}