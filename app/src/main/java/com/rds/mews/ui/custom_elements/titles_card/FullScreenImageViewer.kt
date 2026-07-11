package com.rds.mews.ui.custom_elements.titles_card

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun FullScreenImageViewer(
    initialPage: Int,
    dynamicMediaUrls: List<MediaWithSource>,
    imageBoundsMap: Map<Int, Rect>,
    onClose: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val handler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    RootViewOverlay {
        val fullScreenPagerState = rememberPagerState(initialPage = initialPage) { dynamicMediaUrls.size }
        val transitionAnim = remember { Animatable(0f) }
        var isClosing by remember { mutableStateOf(false) }
        val swipeDismissY = remember { Animatable(0f) }
        var isUiVisible by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            transitionAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            )
        }

        LaunchedEffect(fullScreenPagerState.currentPage) {
            onPageChanged(fullScreenPagerState.currentPage)
        }

        val closeWithAnimation: () -> Unit = {
            if (!isClosing) {
                isClosing = true
                coroutineScope.launch {
                    onPageChanged(fullScreenPagerState.currentPage)
                    launch {
                        swipeDismissY.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
                    }
                    transitionAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                    )
                    onClose()
                }
            }
        }

        BackHandler { closeWithAnimation() }

        val dynamicThemeSurfaceColor = MaterialTheme.colorScheme.surface
        val swipeAlpha = (1f - (abs(swipeDismissY.value) / 800f)).coerceIn(0f, 1f)
        val backgroundAlpha = transitionAnim.value * swipeAlpha

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dynamicThemeSurfaceColor.copy(alpha = backgroundAlpha))
        ) {
            val targetBounds = imageBoundsMap[fullScreenPagerState.currentPage] ?: Rect.Zero
            val screenWidth = with(density) { config.screenWidthDp.dp.toPx() }
            val screenHeight = with(density) { config.screenHeightDp.dp.toPx() }

            val currentLeft = targetBounds.left + (0f - targetBounds.left) * transitionAnim.value
            val currentTop = targetBounds.top + (0f - targetBounds.top) * transitionAnim.value + (swipeDismissY.value * transitionAnim.value)
            val currentWidth = targetBounds.width + (screenWidth - targetBounds.width) * transitionAnim.value
            val currentHeight = targetBounds.height + (screenHeight - targetBounds.height) * transitionAnim.value
            val cornerSize = 16.dp * (1f - transitionAnim.value)

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentLeft.roundToInt(), currentTop.roundToInt()) }
                    .size(with(density) { currentWidth.toDp() }, with(density) { currentHeight.toDp() })
                    .clip(RoundedCornerShape(cornerSize.coerceAtLeast(0.dp)))
            ) {
                HorizontalPager(
                    state = fullScreenPagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = transitionAnim.value == 1f && swipeDismissY.value == 0f
                ) { page ->
                    val scale = remember { Animatable(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val offsetY = remember { Animatable(0f) }

                    var imageSize by remember { mutableStateOf(Size.Zero) }
                    var containerSize by remember { mutableStateOf(Size.Zero) }

                    val actualImageWidth = remember(imageSize, containerSize) {
                        if (imageSize.width > 0 && imageSize.height > 0 && containerSize.width > 0 && containerSize.height > 0) {
                            val scaleFactor = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
                            imageSize.width * scaleFactor
                        } else containerSize.width
                    }

                    val actualImageHeight = remember(imageSize, containerSize) {
                        if (imageSize.width > 0 && imageSize.height > 0 && containerSize.width > 0 && containerSize.height > 0) {
                            val scaleFactor = minOf(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
                            imageSize.height * scaleFactor
                        } else containerSize.height
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(actualImageWidth, actualImageHeight, containerSize) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        coroutineScope.launch {
                                            if (scale.value > 1.05f) {
                                                launch { scale.animateTo(1f) }
                                                launch { offsetX.animateTo(0f) }
                                                launch { offsetY.animateTo(0f) }
                                            } else {
                                                val targetScale = 2.5f
                                                val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                                val tapFromCenter = tapOffset - containerCenter

                                                val targetX = -tapFromCenter.x * (targetScale - 1f)
                                                val targetY = -tapFromCenter.y * (targetScale - 1f)

                                                val maxX = maxOf(0f, (actualImageWidth * targetScale - containerSize.width) / 2f)
                                                val maxY = maxOf(0f, (actualImageHeight * targetScale - containerSize.height) / 2f)

                                                launch { scale.animateTo(targetScale) }
                                                launch { offsetX.animateTo(targetX.coerceIn(-maxX, maxX)) }
                                                launch { offsetY.animateTo(targetY.coerceIn(-maxY, maxY)) }
                                            }
                                        }
                                    },
                                    onTap = {
                                        isUiVisible = !isUiVisible
                                    }
                                )
                            }
                            .pointerInput(actualImageWidth, actualImageHeight, containerSize) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var zoomActive = false
                                    var isVerticalSwipe = false
                                    var firstPan = true

                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val centroid = event.calculateCentroid()

                                        val isZooming = zoom != 1f && centroid != Offset.Unspecified

                                        if (scale.value > 1f || isZooming) {
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }

                                            val oldScale = scale.value
                                            val newScale = (oldScale * zoom).coerceIn(1f, 5f)

                                            val maxX = maxOf(0f, (actualImageWidth * newScale - containerSize.width) / 2f)
                                            val maxY = maxOf(0f, (actualImageHeight * newScale - containerSize.height) / 2f)

                                            val targetX: Float
                                            val targetY: Float

                                            if (isZooming) {
                                                val containerCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)

                                                val targetOffsetX = offsetX.value + pan.x
                                                val targetOffsetY = offsetY.value + pan.y

                                                targetX = targetOffsetX - (centroid.x - containerCenter.x - targetOffsetX) * (zoom - 1f)
                                                targetY = targetOffsetY - (centroid.y - containerCenter.y - targetOffsetY) * (zoom - 1f)
                                            } else {
                                                targetX = offsetX.value + pan.x
                                                targetY = offsetY.value + pan.y
                                            }

                                            coroutineScope.launch {
                                                scale.snapTo(newScale)
                                                offsetX.snapTo(targetX.coerceIn(-maxX, maxX))
                                                offsetY.snapTo(targetY.coerceIn(-maxY, maxY))
                                            }
                                        } else {
                                            if (zoom !in 0.99f..1.01f) zoomActive = true
                                            if (pan != Offset.Zero && !zoomActive) {
                                                if (firstPan) {
                                                    isVerticalSwipe = abs(pan.y) > abs(pan.x)
                                                    firstPan = false
                                                }
                                                if (isVerticalSwipe) {
                                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    val targetY = swipeDismissY.value + pan.y
                                                    coroutineScope.launch { swipeDismissY.snapTo(targetY) }
                                                }
                                            }
                                            if (zoomActive && zoom != 1f) {
                                                val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                                                coroutineScope.launch { scale.snapTo(newScale) }
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (scale.value < 1.05f) {
                                        coroutineScope.launch {
                                            scale.animateTo(1f)
                                            offsetX.animateTo(0f)
                                            offsetY.animateTo(0f)
                                        }
                                    }
                                    if (abs(swipeDismissY.value) > 180f) {
                                        closeWithAnimation()
                                    } else {
                                        coroutineScope.launch { swipeDismissY.animateTo(0f) }
                                    }
                                }
                            }
                    ) {
                        AsyncImage(
                            model = dynamicMediaUrls[page].mediaLink,
                            contentDescription = null,
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    imageSize = state.painter.intrinsicSize
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    containerSize = Size(
                                        coordinates.size.width.toFloat(),
                                        coordinates.size.height.toFloat()
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value,
                                    translationX = offsetX.value,
                                    translationY = offsetY.value
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isUiVisible && transitionAnim.value > 0.05f && abs(swipeDismissY.value) < 100f,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val currentMedia = dynamicMediaUrls[fullScreenPagerState.currentPage]
                    val specificSource = currentMedia.message?.source?.currentName ?: currentMedia.message?.source?.originalName ?: "null"
                    val specificTime = currentMedia.message?.time ?: 0L
                    val link = currentMedia.message?.link

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = "$specificSource • ${getFormattedTimeUnix(specificTime)}",
                            action = { link?.let { handler.openUri(it) } }
                        ),
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = Shapes.large
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DynamicPagerIndicator(
                        currentPage = fullScreenPagerState.currentPage,
                        pageCount = dynamicMediaUrls.size
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicPagerIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    val maxVisible = 5
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), Shapes.large)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val start = (currentPage - 2).coerceIn(0, (pageCount - maxVisible).coerceAtLeast(0))
        val end = (start + maxVisible - 1).coerceAtMost(pageCount - 1)
        for (i in start..end) {
            val isSelected = i == currentPage
            val size = if (isSelected) {
                9.dp
            } else if (i == start && start > 0) {
                4.dp
            } else if (i == end && end < pageCount - 1) {
                4.dp
            } else {
                7.dp
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Composable
fun RootViewOverlay(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val overlayId = remember { UUID.randomUUID() }

    DisposableEffect(overlayId) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val rootViewGroup = activity.window.decorView as ViewGroup

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setParentCompositionContext(parentComposition)
            setViewTreeLifecycleOwner(view.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(view.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(view.findViewTreeSavedStateRegistryOwner())
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { }
                ) {
                    content()
                }
            }
        }
        rootViewGroup.addView(composeView)
        onDispose { rootViewGroup.removeView(composeView) }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}