package com.rds.mews.ui.custom_elements.titles_card

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rds.mews.R
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.custom_elements.CustomDropdown
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp

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
    onClick: (Rect) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onShare: () -> Unit,
    dynamicMediaUrls: List<MediaWithSource>? = null,
    onLoadMediaUrls: () -> Unit = {},
    imagePagerState: PagerState,
    clickedImageIndex: Int?,
    onLongClick: () -> Unit = {},
    onImageChanged: (Int) -> Unit,
    onImageClicked: (Boolean) -> Unit,
    isExpanded: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val imageBoundsMap = remember { mutableMapOf<Int, Rect>() }

    val isPinned = title.isPinned
    val isRead = title.isRead

    val maxHeightLimit = (config.screenHeightDp * 0.45f).dp

    val iconParams = IconParams(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.secondaryContainer,
        32.dp
    )

    LaunchedEffect(title.id) {
        onLoadMediaUrls()
    }

    LaunchedEffect(imagePagerState.currentPage) {
        onImageChanged(imagePagerState.currentPage)
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInWindow()
            }
            .alpha(if (isExpanded) 0f else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    cardBounds?.let { bounds ->
                        onClick(bounds)
                    }
                },
                onLongClick = onLongClick
            )
            .animateContentSize(),
        shape = Shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = title.summary.ifBlank { title.title },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isRead) 0.65f else 1.0f),
                fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            val validMedia = dynamicMediaUrls?.filter { it.mediaLink.isNotBlank() } ?: emptyList()
            if (validMedia.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.fillMaxWidth().clip(Shapes.medium)) {
                    HorizontalPager(
                        state = imagePagerState,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val isImageFullOpened = clickedImageIndex == page
                        var isImageLoaded by remember(validMedia[page].mediaLink) { mutableStateOf(false) }

                        val imageRequest = remember(validMedia[page].mediaLink) {
                            ImageRequest.Builder(context)
                                .data(validMedia[page].mediaLink)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isImageLoaded) Modifier.heightIn(max = maxHeightLimit)
                                    else Modifier.wrapContentHeight()
                                )
                                .onGloballyPositioned { coordinates ->
                                    imageBoundsMap[page] = coordinates.boundsInWindow()
                                }
                                .clickable(enabled = !isImageFullOpened) {
                                    onImageClicked(true)
                                }
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                onSuccess = { isImageLoaded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isImageLoaded) Modifier.heightIn(max = maxHeightLimit)
                                        else Modifier.wrapContentHeight()
                                    )
                                    .graphicsLayer {
                                        alpha = if (isImageFullOpened) 0f else 1f
                                    },
                                contentScale = ContentScale.FillWidth
                            )
                        }


                    }

                    if (validMedia.size > 1) {
                        DynamicPagerIndicator(
                            currentPage = imagePagerState.currentPage,
                            pageCount = validMedia.size,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }
                }

                if (clickedImageIndex != null && !isExpanded) {
                    FullScreenImageViewer(
                        initialPage = clickedImageIndex,
                        dynamicMediaUrls = validMedia,
                        imageBoundsMap = imageBoundsMap,
                        onClose = { onImageClicked(false) },
                        onPageChanged = { page ->
                            coroutineScope.launch {
                                imagePagerState.scrollToPage(page)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusTimeBadge(
                    eventTime = title.eventTime,
                    isRead = isRead,
                    isPinned = isPinned,
                    dateString = dateString.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(
                            LocalLocale.current.platformLocale
                        ) else it.toString()
                    },
                    modifier = Modifier.heightIn(min = iconParams.iconSize)
                )

                CustomIconButton(
                    inputs = IconButtonInputs(
                        icon = Icons.Default.MoreVert,
                        action = {
                            dropdownTransitionState.targetState =
                                !dropdownTransitionState.currentState
                        }
                    ),
                    modifier = Modifier
                        .size(iconParams.iconSize)
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
}

@SuppressLint("LocalContextResourcesRead")
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
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val displayMetrics = context.resources.displayMetrics
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val verticalMarginPx = with(density) { 16.dp.toPx() }

    val targetWidthDp = with(density) { collapsedBounds.width.toDp() }

    var isPopupReady by remember { mutableStateOf(false) }
    var measuredContentHeight by remember { mutableStateOf<Float?>(null) }

    val expansionAnim = remember { Animatable(0f) }

    val iconParams = IconParams(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.secondaryContainer,
        32.dp
    )

    val handleDismiss = {
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
        if (measuredContentHeight == null) {
            Box(
                modifier = modifier
                    .width(targetWidthDp)
                    .alpha(0f)
                    .onGloballyPositioned { coordinates ->
                        measuredContentHeight = coordinates.size.height.toFloat()
                        isPopupReady = true
                    }
            ) {
                BlitzCardExpandedContent(
                    iconParams = iconParams,
                    title = title,
                    dateString = dateString,
                    sources = sources,
                    onTogglePin = onTogglePin,
                    onShare = onShare,
                    onLongClick = onLongClick,
                    dynamicMediaUrls = dynamicMediaUrls,
                    imagePagerState = imagePagerState,
                    clickedImageIndex = clickedImageIndex,
                    onImageChanged = onImageChanged,
                    onImageClicked = onImageClicked,
                    contentAlpha = 1f
                )
            }
        }

        val targetHeight = measuredContentHeight ?: collapsedBounds.height
        val maxTop = screenHeightPx - targetHeight - verticalMarginPx
        val targetTop = collapsedBounds.top.coerceAtMost(maxTop).coerceAtLeast(verticalMarginPx)

        val currentTop = lerp(collapsedBounds.top, targetTop, expansionProgress)
        val currentHeight = lerp(collapsedBounds.height, targetHeight, expansionProgress)

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
                                    rect = Rect(0f, 0f, collapsedBounds.width, currentHeight),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            )
                        }
                    }
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .width(targetWidthDp)
                .height(with(density) { targetHeight.toDp() })
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = Shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = (8 * expansionProgress).dp
        ) {
            Box(
                modifier = Modifier
                    .width(targetWidthDp)
                    .fillMaxHeight()
            ) {
                BlitzCardExpandedContent(
                    iconParams = iconParams,
                    title = title,
                    dateString = dateString,
                    sources = sources,
                    onTogglePin = onTogglePin,
                    onShare = onShare,
                    onLongClick = onLongClick,
                    dynamicMediaUrls = dynamicMediaUrls,
                    imagePagerState = imagePagerState,
                    clickedImageIndex = clickedImageIndex,
                    onImageChanged = onImageChanged,
                    onImageClicked = onImageClicked,
                    contentAlpha = contentAlpha
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BlitzCardExpandedContent(
    iconParams: IconParams,
    title: Title,
    dateString: String,
    sources: List<SourceMessages>?,
    onTogglePin: (Boolean) -> Unit = {},
    onShare: () -> Unit = {},
    onLongClick: () -> Unit = {},
    dynamicMediaUrls: List<MediaWithSource>? = null,
    imagePagerState: PagerState,
    clickedImageIndex: Int?,
    onImageChanged: (Int) -> Unit,
    onImageClicked: (Boolean) -> Unit,
    contentAlpha: Float,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val handler = LocalUriHandler.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val imageBoundsMap = remember { mutableMapOf<Int, Rect>() }

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

    val isPinned = title.isPinned
    val isRead = title.isRead
    val maxHeightLimit = (config.screenHeightDp * 0.45f).dp

    LaunchedEffect(imagePagerState.currentPage) {
        onImageChanged(imagePagerState.currentPage)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(
            shape = Shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = title.summary.ifBlank { title.title },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isRead) 0.65f else 1.0f),
                    fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                val validMedia = dynamicMediaUrls?.filter { it.mediaLink.isNotBlank() } ?: emptyList()
                if (validMedia.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(modifier = Modifier.fillMaxWidth().clip(Shapes.medium)) {
                        HorizontalPager(
                            state = imagePagerState,
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val isImageFullOpened = clickedImageIndex == page
                            var isImageLoaded by remember(validMedia[page].mediaLink) { mutableStateOf(false) }

                            val imageRequest = remember(validMedia[page].mediaLink) {
                                ImageRequest.Builder(context)
                                    .data(validMedia[page].mediaLink)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isImageLoaded) Modifier.heightIn(max = maxHeightLimit)
                                        else Modifier.wrapContentHeight()
                                    )
                                    .onGloballyPositioned { coordinates ->
                                        imageBoundsMap[page] = coordinates.boundsInWindow()
                                    }
                                    .clickable(enabled = !isImageFullOpened) {
                                        onImageClicked(true)
                                    }
                            ) {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    onSuccess = { isImageLoaded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isImageLoaded) Modifier.heightIn(max = maxHeightLimit)
                                            else Modifier.wrapContentHeight()
                                        )
                                        .graphicsLayer {
                                            alpha = if (isImageFullOpened) 0f else 1f
                                        },
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }

                        if (validMedia.size > 1) {
                            DynamicPagerIndicator(
                                currentPage = imagePagerState.currentPage,
                                pageCount = validMedia.size,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                            )
                        }
                    }

                    if (clickedImageIndex != null) {
                        FullScreenImageViewer(
                            initialPage = clickedImageIndex,
                            dynamicMediaUrls = validMedia,
                            imageBoundsMap = imageBoundsMap,
                            onClose = { onImageClicked(false) },
                            onPageChanged = { page ->
                                coroutineScope.launch {
                                    imagePagerState.scrollToPage(page)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusTimeBadge(
                        eventTime = title.eventTime,
                        isRead = isRead,
                        isPinned = isPinned,
                        dateString = dateString.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                LocalLocale.current.platformLocale
                            ) else it.toString()
                        },
                        modifier = Modifier.heightIn(min = iconParams.iconSize)
                    )

                    CustomIconButton(
                        inputs = IconButtonInputs(
                            icon = Icons.Default.MoreVert,
                            action = {
                                dropdownTransitionState.targetState =
                                    !dropdownTransitionState.currentState
                            }
                        ),
                        modifier = Modifier
                            .size(iconParams.iconSize)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                .padding(10.dp)
        ) {
            Text(
                text = stringResource(R.string.titles_card_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            val sourcesList = sources ?: emptyList()
            val allValidMessages = remember(sourcesList) {
                sourcesList.flatMap { pack ->
                    pack.messages.filter { it.time > 1000L && it.link.isNotBlank() && it.originalText.trim().length > 3 }
                }
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
            } else if (title.sources.isNotBlank()) {
                Text(
                    text = title.sources,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}