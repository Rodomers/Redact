package com.rds.mews.ui.custom_elements.titles_card

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rds.mews.R
import com.rds.mews.core.text.SnippetExtractor
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleSorting
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.core.text.TextSanitizer
import com.rds.mews.ui.custom_elements.AnimatedSegmentedControl
import com.rds.mews.ui.custom_elements.CustomDropdown
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.customHeader
import com.rds.mews.ui.theme.Shapes
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.rds.mews.ui.custom_elements.image_viewer.DynamicPagerIndicator
import com.rds.mews.ui.custom_elements.image_viewer.FullScreenImageViewer
import com.rds.mews.ui.custom_elements.image_viewer.RootViewOverlay
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TitlesCard(
    title: Title,
    titleSorting: TitleSorting = TitleSorting.NEWEST,
    sources: List<SourceMessages>? = null,
    changeSourceState: (Long, String) -> Unit = { _, _ -> },
    onBanTheme: (String) -> Unit,
    onSwitchStoryline: (Long) -> Unit = {},
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    noTime: Boolean = false,
    showSnippet: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    expandable: Boolean = true,
    markAsUnread: () -> Unit,
    markAsPinned: (Boolean) -> Unit,
    dynamicMediaUrls: List<MediaWithSource>? = null,
    onLoadMediaUrls: () -> Unit = {},
    onImageChanged: (Int) -> Unit,
    sanitizeCopiedText: Boolean = false,
    imagePagerState: PagerState,
    scrollState: ScrollState,
    clickedImageIndex: Int?,
    onImageClicked: (Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var collapsedBounds by remember { mutableStateOf<Rect?>(null) }
    var isPopupReady by remember { mutableStateOf(false) }
    val expansionAnim = remember { Animatable(if (isExpanded) 1f else 0f) }
    val isRead = title.isRead

    val dynamicMediaUrls = dynamicMediaUrls?.filter { it.message?.source?.showMedia ?: false }

    LaunchedEffect(isExpanded, expansionAnim.value == 0f) {
        if (!isExpanded && expansionAnim.value == 0f) {
            isPopupReady = false
        }
    }

    LaunchedEffect(isExpanded, isPopupReady) {
        if (isExpanded) {
            if (isPopupReady) {
                expansionAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        } else {
            expansionAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 750f)
            )
        }
    }

    val showPopup = expansionAnim.value > 0f || isExpanded

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                collapsedBounds = coordinates.boundsInWindow()
            }
            .alpha(if (showPopup && isPopupReady) 0f else 1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (expandable) onToggleExpanded() },
        shape = Shapes.large,
        color = backgroundColor,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TitlesHeaderContent(
                title = title,
                titleSorting = titleSorting,
                noTime = noTime,
                isRead = isRead,
                onClicked = {},
                clickableEnabled = false,
                expansionFraction = 0f
            )

            if (showSnippet && expandable) {
                SnippetText(
                    text = title.summary,
                    modifier = Modifier.padding(bottom = 12.dp),
                    alpha = if (isRead) 0.6f else 1f
                )
            }
        }
    }

    if (showPopup && collapsedBounds != null) {
        RootViewOverlay {
            HeroExpansionContent(
                title = title,
                titleSorting = titleSorting,
                sources = sources,
                changeSourceState = changeSourceState,
                onSwitchStoryline = onSwitchStoryline,
                progress = expansionAnim.value,
                collapsedBounds = collapsedBounds!!,
                onDismissRequest = onToggleExpanded,
                onReady = { isPopupReady = true },
                onBanTheme = onBanTheme,
                onMarkAsUnread = markAsUnread,
                onMarkAsPinned = markAsPinned,
                onImageChanged = onImageChanged,
                pagerState = pagerState,
                rememberPage = rememberPage,
                originalNoTime = noTime,
                showSnippet = showSnippet,
                isRead = isRead,
                backgroundColor = backgroundColor,
                dynamicMediaUrls = dynamicMediaUrls,
                onLoadMediaUrls = onLoadMediaUrls,
                sanitizeCopiedText = sanitizeCopiedText,
                imagePagerState = imagePagerState,
                scrollState = scrollState,
                clickedImageIndex = clickedImageIndex,
                onImageClicked = onImageClicked,
                setShowMedia = setShowMedia
            )
        }
    }
}

@Composable
private fun SnippetText(
    text: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val context = LocalContext.current
    val cleanText = remember(text) {
        SnippetExtractor.extractSnippet(context, text)
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .replace(Regex("[*_~#>`]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    Text(
        text = cleanText,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(alpha),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@SuppressLint("LocalContextResourcesRead")
@Composable
private fun HeroExpansionContent(
    title: Title,
    titleSorting: TitleSorting,
    sources: List<SourceMessages>?,
    changeSourceState: (Long, String) -> Unit,
    onSwitchStoryline: (Long) -> Unit,
    progress: Float,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    onReady: () -> Unit,
    onBanTheme: (String) -> Unit,
    onMarkAsUnread: () -> Unit,
    onMarkAsPinned: (Boolean) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    originalNoTime: Boolean,
    showSnippet: Boolean,
    isRead: Boolean,
    backgroundColor: Color,
    dynamicMediaUrls: List<MediaWithSource>?,
    onLoadMediaUrls: () -> Unit,
    onImageChanged: (Int) -> Unit,
    sanitizeCopiedText: Boolean,
    imagePagerState: PagerState,
    scrollState: ScrollState,
    clickedImageIndex: Int?,
    setShowMedia: (Long, Boolean) -> Unit,
    onImageClicked: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val verticalMarginDp = 50.dp
    val horizontalMarginDp = 10.dp
    val horizontalMarginPx = with(density) { horizontalMarginDp.toPx() }
    val verticalMarginPx = with(density) { verticalMarginDp.toPx() }
    val clampedProgress = progress.coerceIn(0f, 1f)

    BackHandler { onDismissRequest() }

    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val maxAvailableWidth = screenWidthPx - (horizontalMarginPx * 2)
    val maxAvailableHeight = screenHeightPx - (verticalMarginPx * 2)
    val maxAvailableHeightDp = with(density) { maxAvailableHeight.toDp() }
    val targetWidthDp = with(density) { maxAvailableWidth.toDp() }

    var contentHeight by remember { mutableStateOf<Float?>(null) }
    val scrimAlpha = clampedProgress * 0.6f
    val contentAlpha = ((clampedProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
    val containerColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = true
            ) { onDismissRequest() }
    ) {
        if (contentHeight == null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            horizontalMarginPx.roundToInt(),
                            verticalMarginPx.roundToInt()
                        )
                    }
                    .width(targetWidthDp)
                    .alpha(0f)
                    .onGloballyPositioned { coordinates ->
                        contentHeight = coordinates.size.height.toFloat()
                        onReady()
                    }
            ) {
                MeasureCardCompleteStructure(title, isRead, titleSorting)
            }
        }

        val currentContentHeight = contentHeight ?: collapsedBounds.height
        val targetHeight = min(currentContentHeight, maxAvailableHeight)
        val centeredTop = (screenHeightPx - targetHeight) / 2
        val expandedBounds = Rect(
            left = horizontalMarginPx,
            top = centeredTop,
            right = horizontalMarginPx + maxAvailableWidth,
            bottom = centeredTop + targetHeight
        )
        val currentRect: Rect = lerp(collapsedBounds, expandedBounds, progress)

        Surface(
            modifier = Modifier
                .width(targetWidthDp)
                .height(with(density) { targetHeight.toDp() })
                .graphicsLayer {
                    translationX = currentRect.left
                    translationY = currentRect.top
                    shape = object : Shape {
                        override fun createOutline(
                            size: Size,
                            layoutDirection: LayoutDirection,
                            density: Density
                        ): Outline {
                            val clipLeft = 0f
                            val clipTop = 0f
                            val clipRight = currentRect.width
                            val clipBottom = currentRect.height
                            return Outline.Rounded(
                                RoundRect(
                                    rect = Rect(clipLeft, clipTop, clipRight, clipBottom),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            )
                        }
                    }
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = true
                ) {},
            shape = Shapes.large,
            color = containerColor,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .width(targetWidthDp)
                    .fillMaxHeight()
            ) {
                ExpandedCardContent(
                    title = title,
                    titleSorting = titleSorting,
                    sources = sources,
                    changeSourceState = changeSourceState,
                    onSwitchStoryline = onSwitchStoryline,
                    onBanTheme = onBanTheme,
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsPinned = onMarkAsPinned,
                    pagerState = pagerState,
                    rememberPage = rememberPage,
                    onCollapse = onDismissRequest,
                    maxHeight = maxAvailableHeightDp,
                    contentAlpha = contentAlpha,
                    targetWidth = targetWidthDp,
                    expansionProgress = clampedProgress,
                    originalNoTime = originalNoTime,
                    showSnippet = showSnippet,
                    isRead = isRead,
                    headerStartColor = backgroundColor,
                    dynamicMediaUrls = dynamicMediaUrls,
                    onLoadMediaUrls = onLoadMediaUrls,
                    sanitizeCopiedText = sanitizeCopiedText,
                    imagePagerState = imagePagerState,
                    scrollState = scrollState,
                    onImageChanged = onImageChanged,
                    clickedImageIndex = clickedImageIndex,
                    onImageClicked = onImageClicked,
                    collapsedBounds = collapsedBounds,
                    setShowMedia = setShowMedia
                )
            }
        }
    }
}

@Composable
private fun MeasureCardCompleteStructure(title: Title, isRead: Boolean, titleSorting: TitleSorting) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()) {
        TitlesHeaderContent(title = title, titleSorting = titleSorting, noTime = false, isRead = isRead, onClicked = {}, expansionFraction = 1f)
        Spacer(modifier = Modifier.height(8.dp))
        MarkdownText(
            markdown = title.summary.trim(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.2.sp, lineHeight = 22.2.sp)
        )
        Spacer(modifier = Modifier.height(54.dp))
    }
}

@Composable
private fun StorylineBreadcrumb(depth: Int, hasChild: Boolean, isOldest: Boolean, modifier: Modifier = Modifier) {
    val totalDots = depth + (if (hasChild) 2 else 1)
    Column(
        modifier = modifier.padding(top = 4.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalDots) { index ->
            val displayDepth = if (isOldest) (totalDots - 1 - depth) else depth
            val isActive = index == displayDepth
            val size = if (isActive) 8.dp else 5.dp
            val color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun TitlesHeaderContent(
    modifier: Modifier = Modifier,
    title: Title,
    titleSorting: TitleSorting,
    noTime: Boolean,
    isRead: Boolean,
    onClicked: () -> Unit,
    clickableEnabled: Boolean = true,
    expansionFraction: Float = 0f,
    originalNoTime: Boolean = false
) {
    val baseTitleWeight = if (isRead) 400 else 800
    val expandedTitleWeight = 800
    val currentBaseTitleWeight by animateIntAsState(targetValue = baseTitleWeight, label = "titleWeight")
    val finalTitleWeight = currentBaseTitleWeight + ((expandedTitleWeight - currentBaseTitleWeight) * expansionFraction).roundToInt()

    val baseAlpha = if (isRead) 0.6f else 1.0f
    val currentBaseAlpha by animateFloatAsState(targetValue = baseAlpha, label = "contentAlpha")
    val finalAlpha = currentBaseAlpha + ((1.0f - currentBaseAlpha) * expansionFraction)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (clickableEnabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClicked() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (title.storyDepth > 0 || title.childId != null) {
            StorylineBreadcrumb(
                depth = title.storyDepth,
                hasChild = title.childId != null,
                isOldest = titleSorting == TitleSorting.OLDEST
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!noTime) {
                val heightFactor = if (originalNoTime) expansionFraction else 1f
                val timeAlpha = if (originalNoTime) (expansionFraction * 2f).coerceIn(0f, 1f) else 1f

                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val targetHeight = (placeable.height * heightFactor).roundToInt()
                            layout(placeable.width, targetHeight) {
                                placeable.place(0, targetHeight - placeable.height)
                            }
                        }
                        .alpha(timeAlpha)
                        .padding(bottom = (6 * heightFactor).dp)
                ) {
                    StatusTimeBadge(
                        eventTime = title.eventTime,
                        isRead = isRead,
                        isPinned = title.isPinned
                    )
                }
            }
            Text(
                text = title.title,
                textAlign = TextAlign.Left,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = finalAlpha),
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight(finalTitleWeight)
            )
        }
    }
}

@Composable
private fun PullToSwitchIndicator(text: String, progress: Float, modifier: Modifier = Modifier) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val alpha = (clampedProgress * 1.5f).coerceIn(0f, 1f)
    val scale = 0.85f + (0.15f * clampedProgress)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (clampedProgress >= 1f) stringResource(R.string.titles_card_release) else text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedKeywordTag(
    keyword: String,
    index: Int,
    isExpanded: Boolean,
    maxVisibleYPx: Float,
    onBanTheme: (String) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    shouldAnimate: Boolean = true
) {
    val offsetX = remember { Animatable(if (shouldAnimate) 200f else 0f) }
    val alpha = remember { Animatable(0f) }
    var isOffScreen by remember { mutableStateOf(false) }
    var isMeasured by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, isMeasured) {
        if (isExpanded) {
            if (isOffScreen) {
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

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ExpandedCardContent(
    title: Title,
    titleSorting: TitleSorting,
    sources: List<SourceMessages>?,
    changeSourceState: (Long, String) -> Unit,
    onSwitchStoryline: (Long) -> Unit,
    onBanTheme: (String) -> Unit,
    onMarkAsUnread: () -> Unit,
    onMarkAsPinned: (Boolean) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    onCollapse: () -> Unit,
    maxHeight: Dp,
    contentAlpha: Float,
    targetWidth: Dp,
    expansionProgress: Float,
    originalNoTime: Boolean,
    showSnippet: Boolean,
    isRead: Boolean,
    headerStartColor: Color,
    dynamicMediaUrls: List<MediaWithSource>?,
    onLoadMediaUrls: () -> Unit,
    onImageChanged: (Int) -> Unit,
    sanitizeCopiedText: Boolean,
    imagePagerState: PagerState,
    scrollState: ScrollState,
    collapsedBounds: Rect,
    clickedImageIndex: Int?,
    setShowMedia: (Long, Boolean) -> Unit,
    onImageClicked: (Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val handler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val imageBoundsMap = remember { mutableMapOf<Int, Rect>() }

    val shouldAnimate = remember { !isRead }

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }
    val bottomPanelHeight = 50.dp
    val bottomPanelItemsColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val headerEndColor = MaterialTheme.colorScheme.secondaryContainer
    val shouldAnimateHeader = headerStartColor != headerEndColor

    var pullOffset by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    LaunchedEffect(title.id, sources) {
        onLoadMediaUrls()
    }

    LaunchedEffect(imagePagerState.currentPage) {
        onImageChanged(imagePagerState.currentPage)
    }

    val pullThresholdPx = with(density) { 90.dp.toPx() }
    val maxPullPx = with(density) { 135.dp.toPx() }

    val hasRelatedNews = title.parentId != null || title.childId != null
    val isOldest = titleSorting == TitleSorting.OLDEST
    val topRelatedId = if (isOldest) title.childId else title.parentId
    val bottomRelatedId = if (isOldest) title.parentId else title.childId

    val nestedScrollConnection = remember(hasRelatedNews, isOldest) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!hasRelatedNews) return Offset.Zero

                if (pullOffset > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullOffset)
                    pullOffset += consumed
                    return Offset(0f, consumed)
                }
                if (pullOffset < 0f && available.y > 0f) {
                    val consumed = available.y.coerceAtMost(-pullOffset)
                    pullOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!hasRelatedNews || source != NestedScrollSource.UserInput) return Offset.Zero

                if (available.y > 0f && topRelatedId != null) {
                    pullOffset = (pullOffset + available.y * 0.4f).coerceAtMost(maxPullPx)

                    if (pullOffset >= pullThresholdPx && !hasTriggeredHaptic) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasTriggeredHaptic = true
                    } else if (pullOffset < pullThresholdPx) {
                        hasTriggeredHaptic = false
                    }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && bottomRelatedId != null) {
                    pullOffset = (pullOffset + available.y * 0.4f).coerceAtLeast(-maxPullPx)

                    if (pullOffset <= -pullThresholdPx && !hasTriggeredHaptic) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasTriggeredHaptic = true
                    } else if (pullOffset > -pullThresholdPx) {
                        hasTriggeredHaptic = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset != 0f) {
                    val switchId = if (pullOffset >= pullThresholdPx && topRelatedId != null) topRelatedId
                    else if (pullOffset <= -pullThresholdPx && bottomRelatedId != null) bottomRelatedId
                    else null

                    if (switchId != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCollapse()
                        onSwitchStoryline(switchId)
                    }

                    coroutineScope.launch {
                        hasTriggeredHaptic = false
                        animate(
                            initialValue = pullOffset,
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) { value, _ -> pullOffset = value }
                    }

                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val summary = if (sanitizeCopiedText) TextSanitizer.sanitize(title.summary, saveWhitespace = true) else title.summary
    val boldTitle = if (sanitizeCopiedText) "" else "**"
    val copiedText =
        "${boldTitle}${title.title}${boldTitle}\n\n${summary}\n\n${stringResource(R.string.titles_card_source)}: ${stringResource(R.string.app_name)}, ${title.sources}"
    fun copyText() { clipboardManager.setText(AnnotatedString(copiedText)) }
    fun shareText() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, copiedText)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }
    val isPinned = title.isPinned
    val buttons = listOf(
        TextButtonInputs(stringResource(R.string.share_btn_desc), ::shareText),
        TextButtonInputs(stringResource(if (!isPinned) R.string.pin_btn_desc else R.string.unpin_btn_desc), { onMarkAsPinned(!isPinned) }),
        TextButtonInputs(stringResource(R.string.mark_as_unread_btn_desc), onMarkAsUnread)
    )

    val nudgeOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (shouldAnimate &&sources?.isNotEmpty() == true) {
            delay(500.milliseconds)
            nudgeOffset.animateTo(
                targetValue = if (pagerState.targetPage == 0) -36f else 36f,
                animationSpec = tween(durationMillis = 200)
            )
            nudgeOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
        }
    }
    LaunchedEffect(pagerState.targetPage) { rememberPage(pagerState.targetPage) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxHeight)
        .wrapContentHeight()) {
        Surface(
            shape = Shapes.large,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Shapes.large)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCollapse() }
                .drawBehind {
                    if (!shouldAnimateHeader) drawRect(headerEndColor)
                    else {
                        drawRect(headerStartColor)
                        if (expansionProgress > 0f) {
                            drawRect(
                                color = headerEndColor,
                                topLeft = Offset.Zero,
                                size = Size(
                                    width = size.width * expansionProgress,
                                    height = size.height
                                )
                            )
                        }
                    }
                }
        ) {
            Surface(
                shape = Shapes.large,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.large)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCollapse() }
                    .drawBehind {
                        if (!shouldAnimateHeader) drawRect(headerEndColor)
                        else {
                            drawRect(headerStartColor)
                            if (expansionProgress > 0f) {
                                drawRect(
                                    color = headerEndColor,
                                    topLeft = Offset.Zero,
                                    size = Size(
                                        width = size.width * expansionProgress,
                                        height = size.height
                                    )
                                )
                            }
                        }
                    }
            ) {
                var realBadgeHeightPx by remember { mutableFloatStateOf(0f) }

                val snippetWidthDp = with(density) { collapsedBounds.width.toDp() }
                val currentHeaderWidthDp = with(density) {
                    (collapsedBounds.width + (targetWidth.toPx() - collapsedBounds.width) * expansionProgress).toDp()
                }

                Column {
                    Box(modifier = Modifier.width(currentHeaderWidthDp)) {
                        TitlesHeaderContent(
                            title = title,
                            titleSorting = titleSorting,
                            noTime = false,
                            isRead = isRead,
                            onClicked = {},
                            clickableEnabled = false,
                            expansionFraction = expansionProgress,
                            originalNoTime = originalNoTime
                        )
                    }

                    val timeBadgeOffsetPx = if (originalNoTime) -realBadgeHeightPx * (1f - expansionProgress) else 0f

                    if (showSnippet) {
                        val snippetAlpha = (1f - expansionProgress * 2.5f).coerceIn(0f, 1f)
                        if (snippetAlpha > 0f) {
                            Box(modifier = Modifier.width(snippetWidthDp)) {
                                SnippetText(
                                    text = title.summary,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = snippetAlpha * if (isRead) 0.6f else 1f
                                        translationY = timeBadgeOffsetPx - (10.dp.toPx() * expansionProgress)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .clipToBounds()
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = sources?.isNotEmpty() ?: false,
                verticalAlignment = Alignment.Top,
//                beyondViewportPageCount = 1,
                modifier = Modifier
                    .requiredWidth(targetWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationX = nudgeOffset.value
                    }
            ) { page ->
                when (page) {
                    0 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            if (topRelatedId != null && pullOffset > 0f) {
                                PullToSwitchIndicator(
                                    text = stringResource(R.string.related_news),
                                    progress = pullOffset / pullThresholdPx,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 24.dp)
                                )
                            }

                            if (bottomRelatedId != null && pullOffset < 0f) {
                                PullToSwitchIndicator(
                                    text = stringResource(R.string.related_news),
                                    progress = -pullOffset / pullThresholdPx,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 80.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { translationY = pullOffset }
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))

                                if (isOldest && title.childId != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.titles_card_continued),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = title.relatedTitle ?: "",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        if (title.relatedSnippet != null) {
                                            Text(
                                                text = title.relatedSnippet!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .alpha(0.7f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(modifier = Modifier.alpha(0.3f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    MarkdownText(
                                        markdown = title.summary.trim(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.2.sp, lineHeight = 22.2.sp, color = MaterialTheme.colorScheme.onSurface)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {},
                                                onLongClick = {
                                                    copyText()
                                                    Toast.makeText(
                                                        context,
                                                        R.string.titles_card_copied,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                    )
                                }

                                if (dynamicMediaUrls == null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (dynamicMediaUrls.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(250.dp)
                                            .clip(Shapes.large)
                                    ) {
                                        HorizontalPager(
                                            state = imagePagerState,
                                            modifier = Modifier.fillMaxSize(),
                                        ) { pageIndex ->
                                            val isImageFullOpened = clickedImageIndex == pageIndex
                                            val imageRequest = remember(dynamicMediaUrls[pageIndex].mediaLink) {
                                                ImageRequest.Builder(context)
                                                    .data(dynamicMediaUrls[pageIndex].mediaLink)
                                                    .diskCachePolicy(CachePolicy.ENABLED)
                                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                                    .build()
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .onGloballyPositioned { coordinates ->
                                                        imageBoundsMap[pageIndex] =
                                                            coordinates.boundsInWindow()
                                                    }
                                                    .clickable(enabled = !isImageFullOpened) {
                                                        onImageClicked(
                                                            true
                                                        )
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .blur(24.dp)
                                                        .alpha(0.6f),
                                                    contentScale = ContentScale.Crop
                                                )

                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            alpha =
                                                                if (isImageFullOpened) 0f else 1f
                                                        },
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }

                                        DynamicPagerIndicator(
                                            currentPage = imagePagerState.currentPage,
                                            pageCount = dynamicMediaUrls.size,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 8.dp)
                                        )
                                    }

                                    if (clickedImageIndex != null) {
                                        FullScreenImageViewer(
                                            initialPage = clickedImageIndex,
                                            dynamicMediaUrls = dynamicMediaUrls,
                                            imageBoundsMap = imageBoundsMap,
                                            onClose = { onImageClicked(false) },
                                            onPageChanged = { page ->
                                                coroutineScope.launch {
                                                    imagePagerState.scrollToPage(page)
                                                }
                                            },
                                            setShowImages = setShowMedia
                                        )
                                    }
                                }

                                if (title.keywords.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val maxVisibleYPx = with(density) {
                                        (collapsedBounds.top + maxHeight.toPx())
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        title.keywords.forEachIndexed { index, keyword ->
                                            AnimatedKeywordTag(
                                                keyword = keyword,
                                                index = index,
                                                isExpanded = expansionProgress > 0.5f,
                                                maxVisibleYPx = maxVisibleYPx,
                                                onBanTheme = onBanTheme,
                                                haptics = haptics,
                                                shouldAnimate = shouldAnimate
                                            )
                                        }
                                    }
                                }

                                if (!isOldest && title.childId != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(modifier = Modifier.alpha(0.3f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.titles_card_background),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = title.relatedTitle ?: "",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        if (title.relatedSnippet != null) {
                                            Text(
                                                text = title.relatedSnippet!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .alpha(0.7f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(bottomPanelHeight + 4.dp))
                            }
                        }
                    }
                    1 -> {
                        if (sources == null) {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = title.sources, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                                Text(text = title.ids, modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp))
                                Spacer(modifier = Modifier.height(bottomPanelHeight + 4.dp))
                            }
                        } else {
                            val allMessages = remember(sources) { sources.flatMap { it.messages } }
                            val minLength = remember(allMessages) { allMessages.minOfOrNull { it.originalText.length } ?: 0 }
                            val maxLength = remember(allMessages) { allMessages.maxOfOrNull { it.originalText.length } ?: 1 }
                            val headerColor = MaterialTheme.colorScheme.secondaryContainer
                            LazyVerticalGrid(modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth(), columns = GridCells.Fixed(1)) {
                                sources.forEach { pack ->
                                    val source = pack.source
                                    val state = pack.state
                                    val messages = pack.messages
                                    val sourceName = if (source != null) source.currentName ?: source.originalName else "null"
                                    customHeader(
                                        text = sourceName,
                                        isExpanded = state,
                                        onHeaderClick = { changeSourceState(title.id, sourceName) },
                                        onTextClick = { if (source != null) handler.openUri(source.websiteUrl) },
                                        fontSize = 16.sp,
                                        buttonsColor = headerColor,
                                        modifier = Modifier.height(55.dp)
                                    )
                                    item(key = "${title.id}_$source") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ExpandableContainer(visible = state) {
                                                Box(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)) {
                                                    FlowRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        maxItemsInEachRow = 5
                                                    ) {
                                                        messages.forEach { item ->
                                                            val padding = remember(
                                                                item.originalText.length,
                                                                minLength,
                                                                maxLength
                                                            ) {
                                                                if (maxLength == minLength) 12.dp + (48.dp * (item.originalText.length / 200f).coerceIn(
                                                                    0f,
                                                                    1f
                                                                ))
                                                                else 12.dp + (48.dp * ((item.originalText.length - minLength).toFloat() / (maxLength - minLength)))
                                                            }
                                                            CustomTextButton(
                                                                inputs = TextButtonInputs(
                                                                    getFormattedTimeUnix(item.time),
                                                                    { handler.openUri(item.link) }),
                                                                horizontalPadding = padding,
                                                                defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.9f),
                                                                shape = Shapes.large
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier
                                    .height(bottomPanelHeight - 8.dp)
                                    .fillMaxWidth()) }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(0.7f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedSegmentedControl(
                        items = listOf(
                            stringResource(R.string.titles_card_text) to {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        0
                                    )
                                }
                            },
                            stringResource(R.string.titles_card_source) to {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }),
                        selectedIndex = pagerState.currentPage,
                        modifier = Modifier.padding(bottom = 8.dp),
                        backgroundColor = bottomPanelItemsColor,
                        enabled = sources?.isNotEmpty() ?: false,
                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        itemPadding = 2.dp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CustomIconButton(
                        inputs = IconButtonInputs(
                            Icons.Default.MoreVert,
                            {
                                dropdownTransitionState.targetState =
                                    !dropdownTransitionState.currentState
                            }),
                        modifier = Modifier
                            .size(40.dp)
                            .padding(bottom = 6.dp)
                            .align(Alignment.CenterVertically)
                            .onGloballyPositioned {
                                buttonBounds = it.boundsInWindow().roundToIntRect()
                            },
                        iconModifier = Modifier.size(18.dp),
                        defaultBackgroundColor = bottomPanelItemsColor,
                        transitionBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                            alpha = 0.7f
                        ),
                        transitionState = dropdownTransitionState,
                        shape = Shapes.large
                    )
                    if (dropdownTransitionState.currentState || dropdownTransitionState.targetState) {
                        CustomDropdown(
                            transitionState = dropdownTransitionState,
                            buttons = buttons,
                            inputBounds = buttonBounds,
                            config = config,
                            density = density,
                            onDismissRequest = { dropdownTransitionState.targetState = false },
                            arrowPosition = ArrowPosition.BottomRight,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }
            }
        }
    }
}