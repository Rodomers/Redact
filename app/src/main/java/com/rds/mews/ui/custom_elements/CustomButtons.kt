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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes
import kotlin.math.hypot
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
                    val maxRadius = hypot(size.width, size.height)

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
            textAlign = textAlign,
            maxLines = 2
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

                this.shape = shape ?: RoundedCornerShape(0.dp)
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

@Composable
fun CustomKeywordButton(
    text: String,
    onDeleteAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultBackgroundColor: Color = Color.Transparent,
    contentColor: Color = LocalContentColor.current,
    shape: CornerBasedShape = Shapes.medium
) {
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .wrapContentWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .clip(shape)
            .drawBehind {
                drawRect(color = defaultBackgroundColor)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onDeleteAction,
                role = Role.Button
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun AnimatedKeywordItem(
    keyword: String,
    onDelete: (String) -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        finishedListener = {
            if (isDeleting) onDelete(keyword)
        },
        label = "alphaOut"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDeleting) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "scaleOut"
    )

    if (alpha > 0f) {
        CustomKeywordButton(
            text = keyword,
            onDeleteAction = { isDeleting = true },
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
            defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = Shapes.large
        )
    }
}