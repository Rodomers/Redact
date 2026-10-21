package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.rds.mews.localcore.ScreenQuadrant
import com.rds.mews.localcore.getScreenQuadrant
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch

@SuppressLint("LocalContextResourcesRead", "InternalInsetResource", "DiscouragedApi")
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

    val targetWidthDp = with(density) { collapsedBounds.width.toDp() }

    val statusBarResId = remember {
        context.resources.getIdentifier("status_bar_height", "dimen", "android")
    }
    val fallbackStatusBarTopPx = remember(statusBarResId) {
        if (statusBarResId > 0) context.resources.getDimensionPixelSize(statusBarResId).toFloat() else 0f
    }
    val insetsStatusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val statusBarTopPx = if (insetsStatusBarTopPx > 0f) insetsStatusBarTopPx else fallbackStatusBarTopPx
    val statusBarTopDp = with(density) { statusBarTopPx.toDp() }

    var isPopupReady by remember { mutableStateOf(false) }
    var measuredActionsHeight by remember { mutableStateOf<Float?>(null) }

    val expansionAnim = remember { Animatable(0f) }
    val scrimAnim = remember { Animatable(0f) }

    val isTopQuadrant = remember(config, collapsedBounds) {
        when (getScreenQuadrant(config, collapsedBounds.roundToIntRect())) {
            ScreenQuadrant.TopLeft, ScreenQuadrant.TopRight -> true
            else -> false
        }
    }
    val isActionsAbove = !isTopQuadrant

    val handleDismiss: () -> Unit = {
        scope.launch {
            launch {
                scrimAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            expansionAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 750f)
            )
            onDismissRequest()
        }
    }

    LaunchedEffect(isPopupReady) {
        if (isPopupReady) {
            launch {
                scrimAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            expansionAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    val expansionProgress = expansionAnim.value
    val scrimAlpha = (scrimAnim.value * 0.6f).coerceIn(0f, 1f)
    val contentAlpha = ((expansionProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)

    BackHandler(enabled = true) {
        handleDismiss()
    }

    Box(
        contentAlignment = Alignment.TopStart,
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
                    .requiredWidth(targetWidthDp)
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
        val currentActionsHeight = actionsHeight * expansionProgress
        val currentContainerHeight = mainHeight + currentActionsHeight

        val currentTop = if (isActionsAbove) {
            collapsedBounds.top - currentActionsHeight
        } else {
            collapsedBounds.top
        }

        val mainHeightDp = with(density) { mainHeight.toDp() }
        val actionsHeightDp = with(density) { actionsHeight.toDp() }
        val currentActionsHeightDp = with(density) { currentActionsHeight.toDp() }
        val currentContainerHeightDp = with(density) { currentContainerHeight.toDp() }

        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTopDp)
                .graphicsLayer { clip = true }
        ) {
            Surface(
                modifier = modifier
                    .graphicsLayer {
                        translationX = collapsedBounds.left
                        translationY = currentTop - statusBarTopPx
                        clip = true
                        shape = mainCardShape
                    }
                    .requiredWidth(targetWidthDp)
                    .requiredHeight(currentContainerHeightDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = mainCardShape,
                color = containerColor,
                shadowElevation = (8 * expansionProgress).dp
            ) {
                Column(
                    modifier = Modifier
                        .requiredWidth(targetWidthDp)
                        .requiredHeight(currentContainerHeightDp)
                ) {
                    if (isActionsAbove) {
                        Box(
                            modifier = Modifier
                                .requiredWidth(targetWidthDp)
                                .requiredHeight(currentActionsHeightDp)
                                .graphicsLayer { clip = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .requiredWidth(targetWidthDp)
                                    .requiredHeight(actionsHeightDp)
                            ) {
                                ActionsWrapper(
                                    actionsMaxHeight = actionsMaxHeight,
                                    contentAlpha = contentAlpha,
                                    modifier = Modifier.fillMaxSize(),
                                    actionsContent = { actionsContent(handleDismiss) }
                                )
                            }
                        }
                        Surface(
                            shape = mainCardShape,
                            color = mainCardColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(mainHeightDp)
                        ) {
                            mainContent()
                        }
                    } else {
                        Surface(
                            shape = mainCardShape,
                            color = mainCardColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(mainHeightDp)
                        ) {
                            mainContent()
                        }
                        Box(
                            modifier = Modifier
                                .requiredWidth(targetWidthDp)
                                .requiredHeight(currentActionsHeightDp)
                                .graphicsLayer { clip = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .requiredWidth(targetWidthDp)
                                    .requiredHeight(actionsHeightDp)
                            ) {
                                ActionsWrapper(
                                    actionsMaxHeight = actionsMaxHeight,
                                    contentAlpha = contentAlpha,
                                    modifier = Modifier.fillMaxSize(),
                                    actionsContent = { actionsContent(handleDismiss) }
                                )
                            }
                        }
                    }
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