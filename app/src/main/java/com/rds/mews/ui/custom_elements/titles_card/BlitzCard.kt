package com.rds.mews.ui.custom_elements.titles_card

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toSize
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rds.mews.R
import com.rds.mews.core.text.TextSanitizer
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleCardStates
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.custom_elements.CustomDropdown
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.custom_elements.DetachableOverlayCard
import com.rds.mews.ui.custom_elements.image_viewer.DynamicPagerIndicator
import com.rds.mews.ui.custom_elements.image_viewer.FullScreenImageViewer
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private data class IconParams(
    val defaultIconColor: Color,
    val transitionIconColor: Color,
    val iconSize: Dp
)

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun BlitzCard(
    title: Title,
    dateString: String,
    isExpanded: Boolean,
    onToggleExpanded: (Rect?) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    sources: List<SourceMessages>?,
    sanitizeCopiedText: Boolean,
    dynamicMediaUrls: List<MediaWithSource>? = null,
    onLoadMediaUrls: () -> Unit = {},
    imagePagerState: PagerState,
    clickedImageIndex: Int?,
    currentImage: Int = 0,
    onImageChanged: (Int) -> Unit,
    onImageClicked: (Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit,
    onBanTheme: (String) -> Unit = {},
    customWidth: Dp? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    val filteredMediaUrls = dynamicMediaUrls?.filter {
        (it.message?.source?.showMedia ?: true) && it.mediaLink.isNotBlank()
    } ?: emptyList()
    val validMedia = filteredMediaUrls
    val hasMedia = validMedia.isNotEmpty()

    val maxCardWidth = (config.screenWidthDp - 36).dp
    val defaultWidth = if (hasMedia) maxCardWidth else (config.screenWidthDp * 0.7f).dp
    val cardWidth = customWidth ?: defaultWidth

    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    val sourceLabel = stringResource(R.string.titles_card_source)
    val appNameLabel = stringResource(R.string.app_name)
    val copiedToastText = stringResource(R.string.titles_card_copied)

    val copiedText = remember(title.summary, title.sources, sanitizeCopiedText, sourceLabel, appNameLabel) {
        val summary = if (sanitizeCopiedText) TextSanitizer.sanitize(title.summary, saveWhitespace = true) else title.summary
        val sourcesFormatted = title.sources
        "$summary\n\n$sourceLabel: $appNameLabel, $sourcesFormatted"
    }

    val onShareAction: () -> Unit = {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, copiedText)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    val onCopyAction: () -> Unit = {
        clipboardManager.setText(AnnotatedString(copiedText))
        Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(title.id, sources) {
        onLoadMediaUrls()
    }

    Surface(
        modifier = modifier
            .width(cardWidth)
            .wrapContentHeight()
            .onGloballyPositioned { coordinates ->
                cardBounds = Rect(
                    offset = coordinates.positionInWindow(),
                    size = coordinates.size.toSize()
                )
            }
            .alpha(if (isExpanded) 0f else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggleExpanded(cardBounds) },
                onLongClick = onCopyAction
            ),
        shape = Shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 0.dp
    ) {
        BlitzCardInnerContent(
            title = title,
            validMedia = validMedia,
            imagePagerState = imagePagerState,
            clickedImageIndex = clickedImageIndex,
            currentImage = currentImage,
            isOverlayMode = false,
            isExpanded = isExpanded,
            onTogglePin = onTogglePin,
            onShare = onShareAction,
            onLongClick = onCopyAction,
            onCardClick = { onToggleExpanded(cardBounds) },
            onImageChanged = onImageChanged,
            onImageClicked = onImageClicked,
            setShowMedia = setShowMedia
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalCoilApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BlitzCardInnerContent(
    title: Title,
    validMedia: List<MediaWithSource>,
    imagePagerState: PagerState,
    clickedImageIndex: Int?,
    currentImage: Int = 0,
    isOverlayMode: Boolean,
    isExpanded: Boolean,
    onTogglePin: (Boolean) -> Unit,
    onShare: () -> Unit,
    onLongClick: () -> Unit,
    onCardClick: () -> Unit,
    onImageChanged: (Int) -> Unit,
    onImageClicked: (Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val imageBoundsMap = remember { mutableMapOf<Int, Rect>() }

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

    val isPinned = title.isPinned
    val isRead = title.isRead
    val hasMedia = validMedia.isNotEmpty()

    val iconParams = IconParams(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.secondaryContainer,
        28.dp
    )
    var sharedMediaBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(currentImage) {
        if (validMedia.isNotEmpty() && currentImage in validMedia.indices) {
            if (imagePagerState.currentPage != currentImage) {
                imagePagerState.scrollToPage(currentImage)
            }
        }
    }

    LaunchedEffect(imagePagerState.currentPage) {
        onImageChanged(imagePagerState.currentPage)
    }

    LaunchedEffect(clickedImageIndex) {
        if (clickedImageIndex != null && imagePagerState.currentPage != clickedImageIndex) {
            imagePagerState.scrollToPage(clickedImageIndex)
        }
    }

    val dropdownButtons = listOf(
        TextButtonInputs(
            text = stringResource(R.string.share_btn_desc),
            action = onShare
        ),
        TextButtonInputs(
            text = stringResource(if (isPinned) R.string.unpin_btn_desc else R.string.pin_btn_desc),
            action = { onTogglePin(!isPinned) }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    top = 12.dp,
                    end = if (hasMedia) 10.dp else 14.dp,
                    bottom = 10.dp
                ),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = title.summary.ifBlank { title.title },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (isRead) 0.65f else 1.0f
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!isOverlayMode) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onCardClick,
                                        onLongClick = onLongClick
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusTimeBadge(
                            eventTime = title.eventTime,
                            isRead = isRead,
                            isPinned = isPinned,
                            modifier = Modifier.heightIn(min = iconParams.iconSize)
                        )

                        if (!hasMedia) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CustomIconButton(
                                inputs = IconButtonInputs(
                                    icon = Icons.Default.MoreVert,
                                    action = {
                                        dropdownTransitionState.targetState =
                                            !dropdownTransitionState.currentState
                                    }
                                ),
                                modifier = Modifier
                                    .requiredSize(iconParams.iconSize)
                                    .aspectRatio(1f)
                                    .onGloballyPositioned { coordinates ->
                                        buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                    },
                                iconModifier = Modifier.size(16.dp),
                                defaultBackgroundColor = iconParams.defaultIconColor,
                                transitionBackgroundColor = iconParams.transitionIconColor,
                                transitionState = dropdownTransitionState,
                                shape = Shapes.large
                            )
                        }
                    }
                }

                if (hasMedia) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                sharedMediaBounds = coordinates.boundsInWindow()
                            }
                            .clip(Shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                    HorizontalPager(
                        state = imagePagerState,
                        userScrollEnabled = isOverlayMode && validMedia.size > 1,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val mediaUrl = validMedia[page].mediaLink
                        val isImageFullOpened = clickedImageIndex != null && imagePagerState.currentPage == page
                        var retryCount by remember(mediaUrl) { mutableStateOf(0) }
                        var lastRetryTime by remember(mediaUrl) { mutableStateOf(0L) }
                        var isLoadError by remember(mediaUrl) { mutableStateOf(false) }

                        val imageRequest = remember(mediaUrl, retryCount) {
                            ImageRequest.Builder(context)
                                .data(mediaUrl)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .setParameter("retry_token", retryCount, memoryCacheKey = null)
                                .listener(
                                    onError = { _, _ -> isLoadError = true },
                                    onSuccess = { _, _ -> isLoadError = false }
                                )
                                .build()
                        }

                        val canRetry =
                            retryCount < 3 && (System.currentTimeMillis() - lastRetryTime > 2000L)
                        val onRetryClick: () -> Unit = {
                            if (canRetry) {
                                lastRetryTime = System.currentTimeMillis()
                                retryCount++
                                isLoadError = false
                                coil.Coil.imageLoader(context).diskCache?.remove(mediaUrl)
                                coil.Coil.imageLoader(context).memoryCache?.remove(
                                    coil.memory.MemoryCache.Key(
                                        mediaUrl
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    imageBoundsMap[page] = coordinates.boundsInWindow()
                                }
                                .clickable(enabled = !isImageFullOpened && !isLoadError) {
                                    onImageClicked(true)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadError) {
                                CustomIconButton(
                                    inputs = IconButtonInputs(
                                        icon = Icons.Default.Refresh,
                                        action = onRetryClick
                                    ),
                                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .alpha(if (canRetry) 1f else 0.4f),
                                    iconModifier = Modifier.size(18.dp),
                                    shape = Shapes.large
                                )
                            } else {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(24.dp)
                                        .alpha(0.6f)
                                        .clip(Shapes.large),
                                    contentScale = ContentScale.Crop
                                )

                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = if (isImageFullOpened) 0f else 1f
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(6.dp)
                    ) {
                        if (isOverlayMode && validMedia.size > 1) {
                            DynamicPagerIndicator(
                                currentPage = imagePagerState.currentPage,
                                pageCount = validMedia.size,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        } else if (!isOverlayMode && validMedia.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .background(
                                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+${validMedia.size - 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        CustomIconButton(
                            inputs = IconButtonInputs(
                                icon = Icons.Default.MoreVert,
                                action = {
                                    dropdownTransitionState.targetState =
                                        !dropdownTransitionState.currentState
                                }
                            ),
                            modifier = Modifier
                                .requiredSize(iconParams.iconSize)
                                .aspectRatio(1f)
                                .align(Alignment.CenterEnd)
                                .onGloballyPositioned { coordinates ->
                                    buttonBounds = coordinates.boundsInWindow().roundToIntRect()
                                },
                            iconModifier = Modifier.size(14.dp),
                            defaultBackgroundColor = iconParams.defaultIconColor.copy(alpha = 0.85f),
                            transitionBackgroundColor = iconParams.transitionIconColor,
                            transitionState = dropdownTransitionState,
                            shape = Shapes.large
                        )
                    }
                }

                    if (clickedImageIndex != null && (isOverlayMode || !isExpanded)) {
                        val fallbackBoundsMap = remember(imageBoundsMap, sharedMediaBounds) {
                            validMedia.indices.associateWith { page ->
                                imageBoundsMap[page] ?: sharedMediaBounds ?: Rect.Zero
                            }
                        }

                        FullScreenImageViewer(
                            initialPage = clickedImageIndex,
                            dynamicMediaUrls = validMedia,
                            imageBoundsMap = fallbackBoundsMap,
                            onClose = { onImageClicked(false) },
                            onPageChanged = { page ->
                                onImageChanged(page)
                                coroutineScope.launch {
                                    imagePagerState.scrollToPage(page)
                                }
                            },
                            setShowImages = setShowMedia
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val totalWidth = constraints.maxWidth
            val textMeasurable = measurables[0]
            val mediaMeasurable = measurables.getOrNull(1)

            if (mediaMeasurable == null) {
                val textPlaceable = textMeasurable.measure(
                    Constraints(
                        minWidth = totalWidth,
                        maxWidth = totalWidth,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    )
                )
                layout(totalWidth, textPlaceable.height) {
                    textPlaceable.placeRelative(0, 0)
                }
            } else {
                val spacingPx = 10.dp.roundToPx()
                val availableForBoth = (totalWidth - spacingPx).coerceAtLeast(1)

                var lowWidth = (availableForBoth * 0.45f).toInt().coerceAtLeast(1)
                var highWidth = (availableForBoth * 0.75f).toInt().coerceAtLeast(lowWidth)
                var bestTextWidth = (lowWidth + highWidth) / 2

                for (step in 0 until 6) {
                    val midWidth = (lowWidth + highWidth) / 2
                    val requiredTextHeight = textMeasurable.maxIntrinsicHeight(midWidth)
                    val squareSide = availableForBoth - midWidth
                    if (requiredTextHeight > squareSide) {
                        lowWidth = midWidth + 1
                    } else {
                        bestTextWidth = midWidth
                        highWidth = midWidth - 1
                    }
                }

                val mediaSide = availableForBoth - bestTextWidth
                val textHeight = textMeasurable.maxIntrinsicHeight(bestTextWidth)
                val finalHeight = maxOf(textHeight, mediaSide)

                val textPlaceable = textMeasurable.measure(
                    Constraints.fixed(bestTextWidth, finalHeight)
                )
                val mediaPlaceable = mediaMeasurable.measure(
                    Constraints.fixed(mediaSide, finalHeight)
                )

                layout(totalWidth, finalHeight) {
                    textPlaceable.placeRelative(0, 0)
                    mediaPlaceable.placeRelative(bestTextWidth + spacingPx, 0)
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
                onDismissRequest = { dropdownTransitionState.targetState = false },
                arrowPosition = ArrowPosition.BottomRight,
                backgroundColor = iconParams.defaultIconColor
            )
        }
    }
}


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun BlitzCarouselRow(
    items: List<Title>,
    expandedTitleId: Long? = null,
    dateStringProvider: (Title) -> String,
    titlesCardStates: Set<TitleCardStates>,
    dynamicMediaUrls: Map<Long, List<MediaWithSource>>,
    sanitizeCopiedText: Boolean,
    onToggleExpanded: (Title, Rect?, String) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    onLoadMediaUrls: (Long) -> Unit,
    onImageChanged: (Long, Int) -> Unit,
    onImageClicked: (Long, Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit,
    markTitleAsRead: (Long, Boolean) -> Unit,
    onBanTheme: (String) -> Unit = {},
    carouselState: LazyListState = rememberLazyListState(),
    cardImagePages: Map<Long, Int> = emptyMap(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val config = LocalConfiguration.current

    val isolateHorizontalGestures = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(x = available.x, y = 0f)
            }
        }
    }

    LaunchedEffect(carouselState) {
        snapshotFlow { carouselState.layoutInfo.visibleItemsInfo }
            .map { visibleItems -> visibleItems.mapNotNull { it.key as? Long } }
            .distinctUntilChanged()
            .collect { visibleIds ->
                delay(1500.milliseconds)
                visibleIds.forEach { id ->
                    markTitleAsRead(id, true)
                }
            }
    }

    LaunchedEffect(items) {
        items.forEach { onLoadMediaUrls(it.id) }
    }

    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(items, dynamicMediaUrls) {
        val allLoaded = items.all { dynamicMediaUrls.containsKey(it.id) }
        if (allLoaded) {
            isReady = true
        }
    }

    LaunchedEffect(Unit) {
        delay(5000.milliseconds)
        isReady = true
    }

    if (!isReady) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
            val defaultCardWidthDp = (config.screenWidthDp * 0.7f).dp
            val minCardWidthDp = (config.screenWidthDp * 0.46f).dp
            val maxCardWidthDp = (config.screenWidthDp - 36).dp

            val defaultHeights = items.associate { title ->
                val mediaForTitle = dynamicMediaUrls[title.id]
                val filteredMedia = mediaForTitle?.filter { (it.message?.source?.showMedia ?: false) && it.mediaLink.isNotBlank() } ?: emptyList()
                val hasMedia = filteredMedia.isNotEmpty()
                val initialWidth = if (hasMedia) maxCardWidthDp else defaultCardWidthDp
                val dummyPager = PagerState(currentPage = 0) { filteredMedia.size }

                val measuredHeight = subcompose("measure_def_${title.id}") {
                    BlitzCard(
                        title = title,
                        dateString = "",
                        isExpanded = false,
                        onToggleExpanded = {},
                        onTogglePin = {},
                        sources = null,
                        sanitizeCopiedText = sanitizeCopiedText,
                        dynamicMediaUrls = mediaForTitle,
                        imagePagerState = dummyPager,
                        clickedImageIndex = null,
                        onImageChanged = {},
                        onImageClicked = {},
                        setShowMedia = { _, _ -> },
                        customWidth = initialWidth
                    )
                }.firstOrNull()?.measure(Constraints(maxWidth = constraints.maxWidth))?.height ?: 0

                title.id to measuredHeight
            }

            val maxItemHeightPx = defaultHeights.values.maxOrNull() ?: 0

            val cardWidths = items.associate { title ->
                val mediaForTitle = dynamicMediaUrls[title.id]
                val filteredMedia = mediaForTitle?.filter { (it.message?.source?.showMedia ?: false) && it.mediaLink.isNotBlank() } ?: emptyList()
                val hasMedia = filteredMedia.isNotEmpty()

                if (hasMedia) {
                    title.id to maxCardWidthDp
                } else {
                    val hDefault = defaultHeights[title.id] ?: 0
                    if (maxItemHeightPx <= 0 || hDefault >= maxItemHeightPx) {
                        title.id to defaultCardWidthDp
                    } else {
                        val dummyPager = PagerState(currentPage = 0) { 0 }
                        val hMin = subcompose("measure_min_${title.id}") {
                            BlitzCard(
                                title = title,
                                dateString = "",
                                isExpanded = false,
                                onToggleExpanded = {},
                                onTogglePin = {},
                                sources = null,
                                sanitizeCopiedText = sanitizeCopiedText,
                                dynamicMediaUrls = mediaForTitle,
                                imagePagerState = dummyPager,
                                clickedImageIndex = null,
                                onImageChanged = {},
                                onImageClicked = {},
                                setShowMedia = { _, _ -> },
                                customWidth = minCardWidthDp
                            )
                        }.firstOrNull()?.measure(Constraints(maxWidth = constraints.maxWidth))?.height ?: 0

                        val calculatedWidth = when {
                            hMin < (maxItemHeightPx * 0.72f) -> defaultCardWidthDp
                            hMin <= maxItemHeightPx -> minCardWidthDp
                            else -> {
                                val ratio = ((maxItemHeightPx - hDefault).toFloat() / (hMin - hDefault).coerceAtLeast(1)).coerceIn(0f, 1f)
                                defaultCardWidthDp - (defaultCardWidthDp - minCardWidthDp) * ratio
                            }
                        }
                        title.id to calculatedWidth
                    }
                }
            }

            val rowHeightDp = maxItemHeightPx.toDp() + 16.dp

            val rowPlaceable = subcompose("carousel_content") {
                LazyRow(
                    state = carouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .clip(Shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .nestedScroll(isolateHorizontalGestures),
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = if (items.size == 1) {
                        Arrangement.End
                    } else {
                        Arrangement.spacedBy(8.dp, Alignment.Start)
                    }
                ) {
                    items(
                        items = items,
                        key = { it.id }
                    ) { title ->
                        val statesItem = titlesCardStates.find { it.id == title.id }
                        val isExpanded = (expandedTitleId == title.id) || (statesItem?.expanded ?: false)
                        val sources = statesItem?.sources
                        val dateStr = dateStringProvider(title)
                        val mediaForTitle = dynamicMediaUrls[title.id]
                        val filteredMedia = mediaForTitle?.filter { (it.message?.source?.showMedia ?: false) && it.mediaLink.isNotBlank() } ?: emptyList()

                        val currentImg = cardImagePages[title.id] ?: statesItem?.currentImage ?: 0
                        val imagePagerState = rememberPagerState(
                            initialPage = currentImg,
                            pageCount = { filteredMedia.size }
                        )
                        val clickedImageIndex = if (statesItem?.fullscreenImage == true) currentImg else null

                        BlitzCard(
                            title = title,
                            dateString = dateStr,
                            isExpanded = isExpanded,
                            onToggleExpanded = { bounds ->
                                onToggleExpanded(title, bounds, dateStr)
                                markTitleAsRead(title.id, true)
                            },
                            onTogglePin = { isPinned -> onTogglePin(title.id, isPinned) },
                            sources = sources,
                            sanitizeCopiedText = sanitizeCopiedText,
                            dynamicMediaUrls = mediaForTitle,
                            onLoadMediaUrls = { onLoadMediaUrls(title.id) },
                            imagePagerState = imagePagerState,
                            clickedImageIndex = clickedImageIndex,
                            onImageChanged = { page -> onImageChanged(title.id, page) },
                            onImageClicked = { flag -> onImageClicked(title.id, flag) },
                            setShowMedia = setShowMedia,
                            onBanTheme = onBanTheme,
                            customWidth = cardWidths[title.id]
                        )
                    }
                }
            }.first().measure(constraints)

            layout(rowPlaceable.width, rowPlaceable.height) {
                rowPlaceable.placeRelative(0, 0)
            }
        }
    }
}

@Composable
fun BlitzCardSourceExpansionOverlay(
    title: Title,
    dateString: String,
    sources: List<SourceMessages>?,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    onTogglePin: (Boolean) -> Unit = {},
    onShare: () -> Unit = {},
    onLongClick: () -> Unit = {},
    dynamicMediaUrls: List<MediaWithSource>? = null,
    imagePagerState: PagerState,
    clickedImageIndex: Int?,
    onImageChanged: (Int) -> Unit,
    onImageClicked: (Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit,
    onBanTheme: (String) -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val filteredMediaUrls = dynamicMediaUrls?.filter { (it.message?.source?.showMedia ?: false) && it.mediaLink.isNotBlank() } ?: emptyList()

    DetachableOverlayCard(
        collapsedBounds = collapsedBounds,
        onDismissRequest = onDismissRequest,
        actionsMaxHeight = 160.dp,
        modifier = modifier,
        mainContent = {
            BlitzCardInnerContent(
                title = title,
                validMedia = filteredMediaUrls,
                imagePagerState = imagePagerState,
                clickedImageIndex = clickedImageIndex,
                isOverlayMode = true,
                onTogglePin = onTogglePin,
                onShare = onShare,
                onLongClick = onLongClick,
                onCardClick = {},
                onImageChanged = onImageChanged,
                onImageClicked = onImageClicked,
                setShowMedia = setShowMedia,
                isExpanded = false
            )
        },
        actionsContent = {
            BlitzCardActionsContent(
                sources = sources,
                titleSources = title.sources,
                keywords = title.keywords,
                onBanTheme = onBanTheme
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlitzCardActionsContent(
    sources: List<SourceMessages>?,
    titleSources: String,
    keywords: List<String>,
    onBanTheme: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val handler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        val sourcesList = sources ?: emptyList()
        val allValidMessages = remember(sourcesList) {
            sourcesList.flatMap { it.messages }
        }

        if (allValidMessages.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                sourcesList.forEach { pack ->
                    val sourceObj = pack.source
                    val sourceName = if (sourceObj != null) sourceObj.currentName ?: sourceObj.originalName else "null"

                    pack.messages.filter { it.time > 1000L && it.link.isNotBlank() && it.originalText.trim().length > 3 }.forEach { item ->
                        val timeStr = getFormattedTimeUnix(item.time)
                        val buttonText = if (timeStr.isNotBlank()) "$sourceName $timeStr" else sourceName

                        CustomTextButton(
                            inputs = TextButtonInputs(
                                text = buttonText,
                                action = { handler.openUri(item.link) }
                            ),
                            defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            shape = Shapes.large,
                            maxLines = 3
                        )
                    }
                }
            }
        } else if (titleSources.isNotEmpty()) {
            Text(
                text = titleSources,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }

        if (keywords.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                keywords.forEach { keyword ->
                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = keyword,
                            action = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBanTheme(keyword)
                            },
                            toast = stringResource(R.string.titles_card_banned)
                        ),
                        defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = Shapes.large,
                        maxLines = 1
                    )
                }
            }
        }
    }
}