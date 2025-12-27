package com.rds.mews.ui.custom_elements

import android.widget.Toast
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun CustomTextButton(
    inputs: TextButtonInputs,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    shape: CornerBasedShape = Shapes.medium,
    enabled: Boolean = true,
    defaultBackgroundColor: Color = Color.Transparent,
    transitionBackgroundColor: Color? = null,
    defaultContentColor: Color = LocalContentColor.current,
    transitionContentColor: Color? = null,
    transitionState: MutableTransitionState<Boolean>? = null,
    verticalPadding: Dp = 8.dp,
    horizontalPadding: Dp = 16.dp,
    indication: Indication? = null
) {
    val context = LocalContext.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scaleAnimation"
    )

    val currentState = transitionState ?: remember { MutableTransitionState(false) }
    val transition = rememberTransition(currentState, label = "ButtonTransition")

    val contentColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 500) },
        label = "ContentColor"
    ) { isActive ->
        if (isActive && transitionContentColor != null) transitionContentColor else defaultContentColor
    }

    val radialProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "RadialProgress"
    ) { isActive ->
        if (isActive && transitionBackgroundColor != null) 1f else 0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .clip(shape)
            .drawBehind {
                drawRect(color = defaultBackgroundColor)

                if (transitionBackgroundColor != null && radialProgress > 0f) {
                    val maxRadius = sqrt(size.width.pow(2) + size.height.pow(2))

                    drawCircle(
                        color = transitionBackgroundColor,
                        radius = maxRadius * radialProgress,
                        center = center
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                onClick = {
                    inputs.action()
                    if (inputs.toast != null) Toast.makeText(context, inputs.toast, Toast.LENGTH_SHORT).show()
                          },
                role = Role.Button
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = inputs.text,
            modifier = textModifier,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign
        )
    }
}

@Composable
fun CustomIconButton(
    inputs: IconButtonInputs,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultBackgroundColor: Color = Color.Transparent,
    transitionBackgroundColor: Color? = null,
    defaultContentColor: Color = LocalContentColor.current,
    transitionContentColor: Color? = null,
    transitionState: MutableTransitionState<Boolean>? = null,
    shape: CornerBasedShape? = null
) {
    val context = LocalContext.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scaleAnimation"
    )

    val currentState = transitionState ?: remember { MutableTransitionState(false) }
    val transition = rememberTransition(currentState, label = "ButtonTransition")

    val contentColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 500) },
        label = "ContentColor"
    ) { isActive ->
        if (isActive && transitionContentColor != null) transitionContentColor else defaultContentColor
    }

    val radialProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "RadialProgress"
    ) { isActive ->
        if (isActive && transitionBackgroundColor != null) 1f else 0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f

                this.shape = shape ?: Shapes.large
                this.clip = true
            }
            .drawBehind {
                drawRect(color = defaultBackgroundColor)

                if (transitionBackgroundColor != null && radialProgress > 0f) {
                    val maxRadius = sqrt(size.width * size.width + size.height * size.height) / 2f

                    drawCircle(
                        color = transitionBackgroundColor,
                        radius = maxRadius * radialProgress,
                        center = center
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    inputs.action()
                    if (inputs.toast != null) Toast.makeText(context, inputs.toast, Toast.LENGTH_SHORT).show()
                },
                role = Role.Button
            )
    ) {
        Icon(
            modifier = iconModifier,
            imageVector = inputs.icon,
            contentDescription = null,
            tint = contentColor
        )
    }
}