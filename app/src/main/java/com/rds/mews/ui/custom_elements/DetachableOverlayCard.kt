package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.lerp
import com.rds.mews.localcore.ScreenQuadrant
import com.rds.mews.localcore.getScreenQuadrant
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch

@SuppressLint("LocalContextResourcesRead")
@Composable
fun DetachableOverlayCard(
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actionsMaxHeight: Dp? = null,
    mainCardShape: Shape = Shapes.large,
    mainCardColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    mainContent: @Composable () -> Unit,
    actionsContent: @Composable (dismiss: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val displayMetrics = context.resources.displayMetrics
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val verticalMarginPx = with(density) { 16.dp.toPx() }

    val targetWidthDp = with(density) { collapsedBounds.width.toDp() }

    var isPopupReady by remember { mutableStateOf(false) }
    var measuredActionsHeight by remember { mutableStateOf<Float?>(null) }

    val expansionAnim = remember { Animatable(0f) }

    val isTopQuadrant = remember(config, collapsedBounds) {
        when (getScreenQuadrant(config, collapsedBounds.roundToIntRect())) {
            ScreenQuadrant.TopLeft, ScreenQuadrant.TopRight -> true
            else -> false
        }
    }
    val isActionsAbove = !isTopQuadrant

    val handleDismiss: () -> Unit = {
        scope.launch {
            expansionAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 750f)
            )
            onDismissRequest()
        }
    }

    LaunchedEffect(isPopupReady) {
        if (isPopupReady) {
            expansionAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    val expansionProgress = expansionAnim.value
    val scrimAlpha = (expansionProgress * 0.6f).coerceIn(0f, 1f)
    val contentAlpha = ((expansionProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)

    BackHandler(enabled = true) {
        handleDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = true
            ) { handleDismiss() }
    ) {
        if (measuredActionsHeight == null) {
            Box(
                modifier = modifier
                    .width(targetWidthDp)
                    .alpha(0f)
                    .onGloballyPositioned { coordinates ->
                        measuredActionsHeight = coordinates.size.height.toFloat()
                        isPopupReady = true
                    }
            ) {
                ActionsWrapper(
                    actionsMaxHeight = actionsMaxHeight,
                    contentAlpha = 1f,
                    modifier = Modifier.fillMaxWidth(),
                    actionsContent = { actionsContent(handleDismiss) }
                )
            }
        }

        val mainHeight = collapsedBounds.height
        val actionsHeight = measuredActionsHeight ?: 0f
        val targetHeight = mainHeight + actionsHeight

        val currentTop: Float
        val clipRect: Rect

        if (isActionsAbove) {
            val minTop = verticalMarginPx
            val maxTop = (screenHeightPx - targetHeight - verticalMarginPx).coerceAtLeast(minTop)
            val idealTop = collapsedBounds.bottom - targetHeight
            val targetTop = idealTop.coerceIn(minTop, maxTop)

            val startTop = collapsedBounds.top - actionsHeight
            currentTop = lerp(startTop, targetTop, expansionProgress)

            val currentClipTop = lerp(actionsHeight, 0f, expansionProgress)
            clipRect = Rect(0f, currentClipTop, collapsedBounds.width, targetHeight)
        } else {
            val minTop = verticalMarginPx
            val maxTop = (screenHeightPx - targetHeight - verticalMarginPx).coerceAtLeast(minTop)
            val targetTop = collapsedBounds.top.coerceIn(minTop, maxTop)

            currentTop = lerp(collapsedBounds.top, targetTop, expansionProgress)
            val currentHeight = lerp(collapsedBounds.height, targetHeight, expansionProgress)
            clipRect = Rect(0f, 0f, collapsedBounds.width, currentHeight)
        }

        val mainHeightDp = with(density) { mainHeight.toDp() }
        val actionsHeightDp = with(density) { actionsHeight.toDp() }
        val targetHeightDp = with(density) { targetHeight.toDp() }

        Surface(
            modifier = modifier
                .graphicsLayer {
                    translationX = collapsedBounds.left
                    translationY = currentTop
                    shape = object : Shape {
                        override fun createOutline(
                            size: Size,
                            layoutDirection: LayoutDirection,
                            density: Density
                        ): Outline {
                            return Outline.Rounded(
                                RoundRect(
                                    rect = clipRect,
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            )
                        }
                    }
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .width(targetWidthDp)
                .height(targetHeightDp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = Shapes.large,
            color = containerColor,
            shadowElevation = (8 * expansionProgress).dp
        ) {
            Box(
                modifier = Modifier
                    .width(targetWidthDp)
                    .height(targetHeightDp)
                    .background(containerColor)
            ) {
                if (isActionsAbove) {
                    ActionsWrapper(
                        actionsMaxHeight = actionsMaxHeight,
                        contentAlpha = contentAlpha,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(actionsHeightDp),
                        actionsContent = { actionsContent(handleDismiss) }
                    )
                    Surface(
                        shape = mainCardShape,
                        color = mainCardColor,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(mainHeightDp)
                    ) {
                        mainContent()
                    }
                } else {
                    Surface(
                        shape = mainCardShape,
                        color = mainCardColor,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(mainHeightDp)
                    ) {
                        mainContent()
                    }
                    ActionsWrapper(
                        actionsMaxHeight = actionsMaxHeight,
                        contentAlpha = contentAlpha,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(actionsHeightDp),
                        actionsContent = { actionsContent(handleDismiss) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsWrapper(
    actionsMaxHeight: Dp?,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
    actionsContent: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .alpha(contentAlpha)
            .then(
                if (actionsMaxHeight != null) Modifier.heightIn(max = actionsMaxHeight)
                else Modifier
            )
            .verticalScroll(scrollState)
    ) {
        actionsContent()
    }
}