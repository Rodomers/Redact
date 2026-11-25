package com.rds.mews.ui.custom_elements.image_viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.rds.mews.R
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.custom_elements.CustomDropdown
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalCoilApi::class, UnstableApi::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun FullScreenImageViewer(
    activeTitleId: Long,
    initialPage: Int,
    hasOpened: Boolean,
    dynamicMediaUrls: List<MediaWithSource>,
    imageBoundsMap: Map<Int, Rect>,
    videoPlayerController: SharedVideoPlayerController,
    onOpened: () -> Unit,
    onClose: () -> Unit,
    onPageChanged: (Int) -> Unit,
    setShowImages: (Long, Boolean) -> Unit,
    fallbackBounds: Rect? = null
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val videoPlayerState = rememberVideoPlayerState(videoPlayerController)

    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val previousBehavior = insetsController.systemBarsBehavior
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = previousBehavior
        }
    }

    RootViewOverlay(alwaysOnTop = true) {
        val pageCount = dynamicMediaUrls.size
        val safeInitialPage = if (pageCount > 0) initialPage.coerceIn(0, pageCount - 1) else 0
        val fullScreenPagerState = rememberPagerState(initialPage = safeInitialPage) { pageCount }
        val transitionAnim = remember(activeTitleId) { Animatable(if (hasOpened) 1f else 0f) }
        var isClosing by remember(activeTitleId) { mutableStateOf(false) }
        val swipeDismissY = remember(activeTitleId) { Animatable(0f) }
        var isUiVisible by rememberSaveable(activeTitleId) { mutableStateOf(true) }
        var isProgrammaticScroll by remember(activeTitleId) { mutableStateOf(false) }
        val latestSafeInitialPage by rememberUpdatedState(safeInitialPage)

        val dropdownTransitionState = remember { MutableTransitionState(false) }
        var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

        val sourceMediaOverrides = remember { mutableStateMapOf<Long, Boolean>() }
        val currentMedia = dynamicMediaUrls.getOrNull(fullScreenPagerState.currentPage)
        val currentSource = currentMedia?.message?.source
        val isMediaShown = if (currentSource != null) {
            sourceMediaOverrides[currentSource.id] ?: (currentSource.showMedia)
        } else false

        val dropdownButtons = listOf(
            TextButtonInputs(
                text = stringResource(R.string.download),
                action = {
                    dropdownTransitionState.targetState = false
                    val targetUrl = dynamicMediaUrls.getOrNull(fullScreenPagerState.currentPage)?.mediaLink
                    if (!targetUrl.isNullOrBlank()) {
                        coroutineScope.launch {
                            saveMediaToGallery(
                                context = context,
                                mediaUrl = targetUrl,
                                isVideo = isVideoUrl(targetUrl)
                            )
                        }
                    }
                },
                toast = stringResource(R.string.saving_to_gallery)
            ),
            TextButtonInputs(
                text = if (isMediaShown) {
                    stringResource(R.string.source_do_not_show_media)
                } else {
                    stringResource(R.string.source_show_media)
                },
                action = {
                    dropdownTransitionState.targetState = false
                    if (currentSource != null) {
                        val newShowMedia = !isMediaShown
                        sourceMediaOverrides[currentSource.id] = newShowMedia
                        setShowImages(currentSource.id, newShowMedia)
                    }
                }
            )
        )

        LaunchedEffect(activeTitleId, hasOpened) {
            if (!hasOpened) {
                transitionAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                )
                onOpened()
            } else if (transitionAnim.value != 1f) {
                transitionAnim.snapTo(1f)
            }
        }

        LaunchedEffect(activeTitleId, safeInitialPage, pageCount) {
            if (pageCount > 0 && !fullScreenPagerState.isScrollInProgress && fullScreenPagerState.currentPage != safeInitialPage) {
                isProgrammaticScroll = true
                try {
                    fullScreenPagerState.scrollToPage(safeInitialPage)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        LaunchedEffect(fullScreenPagerState, pageCount) {
            if (pageCount <= 0) return@LaunchedEffect
            snapshotFlow { fullScreenPagerState.currentPage }
                .collect { page ->
                    val safePage = page.coerceIn(0, pageCount - 1)
                    if (!isProgrammaticScroll && safePage != latestSafeInitialPage) {
                        onPageChanged(safePage)
                    }
                    listOf(safePage - 1, safePage + 1).forEach { neighborIndex ->
                        val neighborUrl = dynamicMediaUrls.getOrNull(neighborIndex)?.mediaLink.orEmpty()
                        if (isVideoUrl(neighborUrl)) {
                            launch {
                                VideoCacheManager.preload(context, neighborUrl, 2L * 1024L * 1024L)
                            }
                        }
                    }
                }
        }

        LaunchedEffect(activeTitleId, fullScreenPagerState.settledPage, isClosing, dynamicMediaUrls) {
            if (isClosing) {
                videoPlayerState.pause(persistUserChoice = false)
                return@LaunchedEffect
            }
            val settledIndex = fullScreenPagerState.settledPage
            val settledMedia = dynamicMediaUrls.getOrNull(settledIndex)
            val settledUrl = settledMedia?.mediaLink.orEmpty()
            if (isVideoUrl(settledUrl)) {
                videoPlayerState.bindToPage(activeTitleId, settledIndex, settledUrl)
            } else {
                videoPlayerState.clearPage()
            }
        }

        val closeWithAnimation: () -> Unit = {
            if (!isClosing) {
                isClosing = true
                videoPlayerState.pause(persistUserChoice = false)
                coroutineScope.launch {
                    if (dynamicMediaUrls.isNotEmpty()) {
                        onPageChanged(fullScreenPagerState.currentPage.coerceIn(0, dynamicMediaUrls.lastIndex))
                    }
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
            val currentBoundsPage = if (dynamicMediaUrls.isNotEmpty()) {
                fullScreenPagerState.currentPage.coerceIn(0, dynamicMediaUrls.lastIndex)
            } else {
                0
            }
            val targetBounds = imageBoundsMap[currentBoundsPage]
                ?: fallbackBounds
                ?: imageBoundsMap.values.firstOrNull { it != Rect.Zero }
                ?: Rect.Zero
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
                    userScrollEnabled = transitionAnim.value == 1f && swipeDismissY.value == 0f && !videoPlayerState.isScrubbing
                ) { page ->
                    val mediaItem = dynamicMediaUrls.getOrNull(page) ?: return@HorizontalPager
                    val mediaUrl = mediaItem.mediaLink
                    val isVideoPage = remember(mediaUrl) { isVideoUrl(mediaUrl) }

                    if (isVideoPage) {
                        val isBoundToThisPage = videoPlayerState.activeTitleId == activeTitleId && videoPlayerState.activePage == page
                        val presentationState = rememberPresentationState(
                            player = if (isBoundToThisPage) videoPlayerState.player else null
                        )
                        var videoContainerSize by remember { mutableStateOf(Size.Zero) }
                        val initialCachedThumb = remember(mediaUrl) {
                            val memCache = coil.Coil.imageLoader(context).memoryCache
                            val bitmap = memCache?.get(coil.memory.MemoryCache.Key("video_thumb_${mediaUrl}_0"))?.bitmap
                                ?: memCache?.get(coil.memory.MemoryCache.Key("video_thumb_$mediaUrl"))?.bitmap
                            bitmap?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }?.also {
                                VideoCacheManager.recordMediaSize(
                                    mediaUrl,
                                    it.width.toFloat(),
                                    it.height.toFloat()
                                )
                            }
                        }
                        var previewIntrinsicSize by remember(mediaUrl) {
                            mutableStateOf(
                                initialCachedThumb?.let { Size(it.width.toFloat(), it.height.toFloat()) }
                                    ?: VideoCacheManager.getMediaSize(mediaUrl)
                            )
                        }
                        val hostView = LocalView.current
                        val lifecycleOwner = LocalLifecycleOwner.current
                        val currentCoverSurface by rememberUpdatedState(presentationState.coverSurface)

                        val previewRequest = remember(mediaUrl) {
                            coil.request.ImageRequest.Builder(context)
                                .data(mediaUrl)
                                .fetcherFactory(VideoThumbnailFetcher.Factory())
                                .memoryCacheKey("video_thumb_${mediaUrl}_0")
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build()
                        }

                        val lastFrameBitmap = if (isBoundToThisPage && videoPlayerState.lastFrameUrl == mediaUrl) {
                            videoPlayerState.lastFrameBitmap
                        } else {
                            null
                        }

                        val showPreview = !isBoundToThisPage || presentationState.coverSurface

                        LaunchedEffect(isBoundToThisPage, presentationState.coverSurface) {
                            if (isBoundToThisPage && !presentationState.coverSurface && videoPlayerState.lastFrameUrl == mediaUrl) {
                                videoPlayerState.clearLastFrame()
                            }
                        }

                        DisposableEffect(isBoundToThisPage, mediaUrl, lifecycleOwner, hostView) {
                            if (!isBoundToThisPage) return@DisposableEffect onDispose {}
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_PAUSE && activity?.isChangingConfigurations == true && !currentCoverSurface) {
                                    val textureView = findAttachedTextureView(hostView)
                                    if (textureView != null && textureView.isAvailable && textureView.width > 0 && textureView.height > 0) {
                                        runCatching { textureView.bitmap }.getOrNull()?.let { bitmap ->
                                            videoPlayerState.saveLastFrame(mediaUrl, bitmap)
                                        }
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                                if (activity?.isChangingConfigurations == true && !currentCoverSurface && videoPlayerState.lastFrameUrl != mediaUrl) {
                                    val textureView = findAttachedTextureView(hostView)
                                    if (textureView != null && textureView.isAvailable && textureView.width > 0 && textureView.height > 0) {
                                        runCatching { textureView.bitmap }.getOrNull()?.let { bitmap ->
                                            videoPlayerState.saveLastFrame(mediaUrl, bitmap)
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    videoContainerSize = Size(
                                        coordinates.size.width.toFloat(),
                                        coordinates.size.height.toFloat()
                                    )
                                }
                                .pointerInput(page) {
                                    detectTapGestures(
                                        onTap = {
                                            isUiVisible = !isUiVisible
                                        }
                                    )
                                }
                                .pointerInput(page) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        var isVerticalSwipe = false
                                        var firstPan = true

                                        do {
                                            val event = awaitPointerEvent()
                                            val pan = event.calculatePan()

                                            if (pan != Offset.Zero) {
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
                                        } while (event.changes.any { it.pressed })

                                        if (abs(swipeDismissY.value) > 180f) {
                                            closeWithAnimation()
                                        } else {
                                            coroutineScope.launch { swipeDismissY.animateTo(0f) }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBoundToThisPage && videoPlayerState.hasError) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (videoPlayerState.isUnsupportedFormat) {
                                        CustomTextButton(
                                            inputs = TextButtonInputs(
                                                text = stringResource(R.string.open_in_browser),
                                                action = {
                                                    val targetLink = mediaItem.message?.link?.takeIf { it.isNotBlank() } ?: mediaUrl
                                                    handler.openUri(targetLink)
                                                }
                                            ),
                                            defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            shape = Shapes.large
                                        )
                                    } else {
                                        CustomIconButton(
                                            inputs = IconButtonInputs(
                                                icon = Icons.Default.Refresh,
                                                action = { videoPlayerState.retry() }
                                            ),
                                            modifier = Modifier
                                                .size(48.dp)
                                                .alpha(if (videoPlayerState.canRetry) 1f else 0.4f),
                                            iconModifier = Modifier.size(24.dp),
                                            defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = Shapes.large
                                        )
                                    }
                                }
                            } else {
                                val lastFrameSize = lastFrameBitmap?.let {
                                    if (it.width > 0 && it.height > 0) {
                                        Size(it.width.toFloat(), it.height.toFloat())
                                    } else {
                                        null
                                    }
                                }
                                val videoSize = listOfNotNull(
                                    if (isBoundToThisPage) videoPlayerState.videoSize else null,
                                    lastFrameSize,
                                    previewIntrinsicSize,
                                    VideoCacheManager.getMediaSize(mediaUrl)
                                ).firstOrNull {
                                    it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f
                                }
                                val fittedVideoModifier = if (
                                    videoSize != null &&
                                    videoContainerSize.width > 0f &&
                                    videoContainerSize.height > 0f
                                ) {
                                    val scaleFactor = minOf(
                                        videoContainerSize.width / videoSize.width,
                                        videoContainerSize.height / videoSize.height
                                    )
                                    Modifier.size(
                                        width = with(density) { (videoSize.width * scaleFactor).toDp() },
                                        height = with(density) { (videoSize.height * scaleFactor).toDp() }
                                    )
                                } else {
                                    Modifier.fillMaxSize()
                                }

                                val previewLayerModifier = fittedVideoModifier.graphicsLayer {
                                    alpha = if (showPreview) 1f else 0f
                                }

                                if (lastFrameBitmap != null) {
                                    Image(
                                        bitmap = lastFrameBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = previewLayerModifier,
                                        contentScale = ContentScale.Fit
                                    )
                                } else if (initialCachedThumb != null) {
                                    Image(
                                        bitmap = initialCachedThumb.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = previewLayerModifier,
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    AsyncImage(
                                        model = previewRequest,
                                        contentDescription = null,
                                        onState = { state ->
                                            if (state is AsyncImagePainter.State.Success) {
                                                val intrinsic = state.painter.intrinsicSize
                                                if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0f && intrinsic.height > 0f) {
                                                    previewIntrinsicSize = intrinsic
                                                    VideoCacheManager.recordMediaSize(
                                                        mediaUrl,
                                                        intrinsic.width,
                                                        intrinsic.height
                                                    )
                                                }
                                            }
                                        },
                                        modifier = previewLayerModifier,
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                if (isBoundToThisPage) {
                                    PlayerSurface(
                                        player = videoPlayerState.player,
                                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                                        modifier = fittedVideoModifier.graphicsLayer {
                                            alpha = if (presentationState.coverSurface) 0f else 1f
                                        }
                                    )
                                }

                                if (isBoundToThisPage && videoPlayerState.isBuffering && !isClosing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        val scale = remember { Animatable(1f) }
                        val offsetX = remember { Animatable(0f) }
                        val offsetY = remember { Animatable(0f) }

                        var imageSize by remember { mutableStateOf(Size.Zero) }
                        var containerSize by remember { mutableStateOf(Size.Zero) }

                        var retryCount by remember(mediaUrl) { mutableStateOf(0) }
                        var lastRetryTime by remember(mediaUrl) { mutableStateOf(0L) }
                        var isLoadError by remember(mediaUrl) { mutableStateOf(false) }

                        LaunchedEffect(isClosing) {
                            if (isClosing && (scale.value != 1f || offsetX.value != 0f || offsetY.value != 0f)) {
                                launch {
                                    scale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                launch {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }

                        val imageRequest = remember(mediaUrl, retryCount) {
                            coil.request.ImageRequest.Builder(context)
                                .data(mediaUrl)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .setParameter("retry_token", retryCount, memoryCacheKey = null)
                                .listener(
                                    onError = { _, _ -> isLoadError = true },
                                    onSuccess = { _, _ -> isLoadError = false }
                                )
                                .build()
                        }

                        val canRetry = retryCount < 3 && (System.currentTimeMillis() - lastRetryTime > 2000L)
                        val onRetryClick: () -> Unit = {
                            if (canRetry) {
                                lastRetryTime = System.currentTimeMillis()
                                retryCount++
                                isLoadError = false
                                coil.Coil.imageLoader(context).diskCache?.remove(mediaUrl)
                                coil.Coil.imageLoader(context).memoryCache?.remove(coil.memory.MemoryCache.Key(mediaUrl))
                            }
                        }

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
                                .then(
                                    if (!isLoadError) {
                                        Modifier.pointerInput(actualImageWidth, actualImageHeight, containerSize) {
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
                                    } else {
                                        Modifier
                                    }
                                )
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
                            if (isLoadError) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CustomIconButton(
                                        inputs = IconButtonInputs(
                                            icon = Icons.Default.Refresh,
                                            action = onRetryClick
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(if (canRetry) 1f else 0.4f),
                                        iconModifier = Modifier.size(24.dp),
                                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = Shapes.large
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = imageRequest,
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
                }
            }

            AnimatedVisibility(
                visible = isUiVisible && transitionAnim.value > 0.05f && abs(swipeDismissY.value) < 100f,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (dynamicMediaUrls.isNotEmpty()) {
                        val currentPage = fullScreenPagerState.currentPage
                        val minPage = (currentPage - 1).coerceAtLeast(0)
                        val maxPage = (currentPage + 1).coerceAtMost(dynamicMediaUrls.size - 1)

                        for (pageIndex in minPage..maxPage) {
                            key(pageIndex) {
                                val pageMedia = dynamicMediaUrls.getOrNull(pageIndex)
                                val pageMediaUrl = pageMedia?.mediaLink.orEmpty()
                                val isPageVideo = remember(pageMediaUrl) { isVideoUrl(pageMediaUrl) }
                                val specificSource = pageMedia?.message?.source?.currentName
                                    ?: pageMedia?.message?.source?.originalName
                                    ?: "null"
                                val specificTime = pageMedia?.message?.time ?: 0L
                                val link = pageMedia?.message?.link
                                val isActivePage = pageIndex == fullScreenPagerState.currentPage
                                var menuButtonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                                LaunchedEffect(
                                    isActivePage,
                                    fullScreenPagerState.settledPage,
                                    fullScreenPagerState.isScrollInProgress
                                ) {
                                    if (isActivePage) {
                                        menuButtonCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                            buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            val pageOffset = fullScreenPagerState.currentPage + fullScreenPagerState.currentPageOffsetFraction
                                            translationX = (pageIndex - pageOffset) * screenWidth
                                        }
                                ) {
                                    if (isPageVideo) {
                                        val isBoundVideoPage = videoPlayerState.activeTitleId == activeTitleId &&
                                                videoPlayerState.activePage == pageIndex
                                        val isPlayingForPage = isBoundVideoPage && videoPlayerState.isPlaying
                                        val durationMs = if (isBoundVideoPage) videoPlayerState.durationMs else 0L
                                        val positionMs = if (isBoundVideoPage) videoPlayerState.displayPositionMs else 0L
                                        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                                        val sliderValue = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat()

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CustomIconButton(
                                                    inputs = IconButtonInputs(
                                                        icon = if (isPlayingForPage) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        action = {
                                                            if (isActivePage && isBoundVideoPage) {
                                                                videoPlayerState.togglePlayPause()
                                                            }
                                                        }
                                                    ),
                                                    modifier = Modifier.size(36.dp),
                                                    iconModifier = Modifier.size(20.dp),
                                                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    defaultContentColor = MaterialTheme.colorScheme.onSurface,
                                                    shape = Shapes.large
                                                )

                                                Slider(
                                                    value = if (durationMs > 0L) sliderValue else 0f,
                                                    onValueChange = { newValue ->
                                                        if (isActivePage && isBoundVideoPage) {
                                                            if (!videoPlayerState.isScrubbing) {
                                                                videoPlayerState.onScrubStart()
                                                            }
                                                            videoPlayerState.onScrubChange(newValue.toLong())
                                                        }
                                                    },
                                                    onValueChangeFinished = {
                                                        if (isActivePage && isBoundVideoPage) {
                                                            videoPlayerState.onScrubEnd()
                                                        }
                                                    },
                                                    valueRange = 0f..sliderMax,
                                                    enabled = isActivePage && isBoundVideoPage && durationMs > 0L,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = MaterialTheme.colorScheme.primary,
                                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                                        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .clip(Shapes.large)
                                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val formattedPos = formatMediaDuration(positionMs)
                                                    val formattedDur = if (durationMs > 0L) formatMediaDuration(durationMs) else "--:--"
                                                    Text(
                                                        text = "$formattedPos / $formattedDur",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Clip
                                                    )
                                                }

                                                CustomIconButton(
                                                    inputs = IconButtonInputs(
                                                        icon = Icons.Default.MoreVert,
                                                        action = {
                                                            if (isActivePage) {
                                                                menuButtonCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                                                    buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                                                }
                                                                dropdownTransitionState.targetState =
                                                                    !dropdownTransitionState.currentState
                                                            }
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .onGloballyPositioned { coordinates ->
                                                            menuButtonCoordinates = coordinates
                                                            if (isActivePage) {
                                                                buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                                            }
                                                        },
                                                    iconModifier = Modifier.size(18.dp),
                                                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    transitionBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    defaultContentColor = MaterialTheme.colorScheme.onSurface,
                                                    transitionContentColor = MaterialTheme.colorScheme.onSurface,
                                                    transitionState = dropdownTransitionState,
                                                    shape = Shapes.large
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    CustomTextButton(
                                                        inputs = TextButtonInputs(
                                                            text = "$specificSource • ${getFormattedTimeUnix(specificTime)}",
                                                            action = {
                                                                if (isActivePage) {
                                                                    link?.let { handler.openUri(it) }
                                                                }
                                                            }
                                                        ),
                                                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = Shapes.large,
                                                        textColor = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                }

                                                DynamicPagerIndicator(
                                                    currentPage = pageIndex,
                                                    pageCount = dynamicMediaUrls.size
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CustomTextButton(
                                                    inputs = TextButtonInputs(
                                                        text = "$specificSource • ${getFormattedTimeUnix(specificTime)}",
                                                        action = {
                                                            if (isActivePage) {
                                                                link?.let { handler.openUri(it) }
                                                            }
                                                        }
                                                    ),
                                                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = Shapes.large,
                                                    modifier = Modifier.padding(horizontal = 48.dp),
                                                    textColor = MaterialTheme.colorScheme.onSurface
                                                )

                                                CustomIconButton(
                                                    inputs = IconButtonInputs(
                                                        icon = Icons.Default.MoreVert,
                                                        action = {
                                                            if (isActivePage) {
                                                                menuButtonCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                                                    buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                                                }
                                                                dropdownTransitionState.targetState =
                                                                    !dropdownTransitionState.currentState
                                                            }
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .size(36.dp)
                                                        .onGloballyPositioned { coordinates ->
                                                            menuButtonCoordinates = coordinates
                                                            if (isActivePage) {
                                                                buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                                            }
                                                        },
                                                    iconModifier = Modifier.size(18.dp),
                                                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    transitionBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    defaultContentColor = MaterialTheme.colorScheme.onSurface,
                                                    transitionContentColor = MaterialTheme.colorScheme.onSurface,
                                                    transitionState = dropdownTransitionState,
                                                    shape = Shapes.large
                                                )
                                            }

                                            if (dynamicMediaUrls.size > 1) {
                                                Spacer(modifier = Modifier.height(16.dp))

                                                DynamicPagerIndicator(
                                                    currentPage = pageIndex,
                                                    pageCount = dynamicMediaUrls.size
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (dropdownTransitionState.currentState || dropdownTransitionState.targetState) {
                CustomDropdown(
                    transitionState = dropdownTransitionState,
                    buttons = dropdownButtons,
                    inputBounds = buttonBounds,
                    config = config,
                    density = density,
                    arrowPosition = ArrowPosition.BottomRight,
                    onDismissRequest = { dropdownTransitionState.targetState = false },
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }
    }
}

private fun findAttachedTextureView(view: View?): TextureView? {
    if (view is TextureView) {
        return if (view.isAvailable) view else null
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findAttachedTextureView(view.getChildAt(i))?.let { return it }
        }
    }
    return null
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
    alwaysOnTop: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val parentSaveableStateRegistry = LocalSaveableStateRegistry.current
    val overlayId = remember { UUID.randomUUID() }

    DisposableEffect(overlayId, alwaysOnTop) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val rootViewGroup = activity.window.decorView as ViewGroup

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            if (alwaysOnTop) {
                translationZ = 100f
            }
            setParentCompositionContext(parentComposition)
            setViewTreeLifecycleOwner(view.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(view.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(view.findViewTreeSavedStateRegistryOwner())
            setContent {
                CompositionLocalProvider(
                    LocalSaveableStateRegistry provides parentSaveableStateRegistry
                ) {
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
        }
        val hierarchyListener = if (alwaysOnTop) {
            object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (child !== composeView) {
                        composeView.post {
                            if (composeView.isAttachedToWindow) {
                                composeView.bringToFront()
                            }
                        }
                    }
                }

                override fun onChildViewRemoved(parent: View?, child: View?) {}
            }
        } else {
            null
        }
        if (hierarchyListener != null) {
            rootViewGroup.setOnHierarchyChangeListener(hierarchyListener)
        }
        rootViewGroup.addView(composeView)
        onDispose {
            if (hierarchyListener != null) {
                rootViewGroup.setOnHierarchyChangeListener(null)
            }
            rootViewGroup.removeView(composeView)
        }
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