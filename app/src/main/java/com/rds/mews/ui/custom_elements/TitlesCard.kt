package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rds.mews.localcore.ArrowPosition
import com.rds.mews.R
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun TitlesCard(
    title: Title,
    sources: List<SourceMessages>? = null,
    changeSourceState: (Long, String) -> Unit = { _, _ -> },
    onBanTheme: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    noTime: Boolean = false,
    showSnippet: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    expandable: Boolean = true,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var collapsedBounds by remember { mutableStateOf<Rect?>(null) }
    var isPopupReady by remember { mutableStateOf(false) }
    val expansionAnim = remember { Animatable(if (isExpanded) 1f else 0f) }

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
                noTime = noTime,
                onClicked = {},
                clickableEnabled = false
            )

            if (showSnippet && expandable) {
                SnippetText(
                    text = title.text,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }

    if (showPopup && collapsedBounds != null) {
        RootViewOverlay {
            HeroExpansionContent(
                title = title,
                sources = sources,
                changeSourceState = changeSourceState,
                progress = expansionAnim.value,
                collapsedBounds = collapsedBounds!!,
                onDismissRequest = onToggleExpanded,
                onReady = { isPopupReady = true },
                onBanTheme = onBanTheme,
                pagerState = pagerState,
                rememberPage = rememberPage,
                originalNoTime = noTime,
                showSnippet = showSnippet,
                backgroundColor = backgroundColor
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
    Text(
        text = text,
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

@Composable
fun RootViewOverlay(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val parentComposition = rememberCompositionContext()
    val overlayId = rememberSaveable { UUID.randomUUID() }

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

        onDispose {
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

@SuppressLint("LocalContextResourcesRead")
@Composable
private fun HeroExpansionContent(
    title: Title,
    sources: List<SourceMessages>?,
    changeSourceState: (Long, String) -> Unit,
    progress: Float,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    onReady: () -> Unit,
    onBanTheme: (String) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    originalNoTime: Boolean,
    showSnippet: Boolean,
    backgroundColor: Color
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val verticalMarginDp = 50.dp
    val horizontalMarginDp = 8.dp
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
                MeasureCardCompleteStructure(title)
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
                .graphicsLayer {
                    translationX = currentRect.left
                    translationY = currentRect.top
                    shape = Shapes.large
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .layout { measurable, _ ->
                    val currentW = currentRect.width.roundToInt()
                    val currentH = currentRect.height.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(currentW, currentH))
                    layout(currentW, currentH) { placeable.place(0, 0) }
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
                    sources = sources,
                    changeSourceState = changeSourceState,
                    onBanTheme = onBanTheme,
                    pagerState = pagerState,
                    rememberPage = rememberPage,
                    onCollapse = onDismissRequest,
                    maxHeight = maxAvailableHeightDp,
                    contentAlpha = contentAlpha,
                    targetWidth = targetWidthDp,
                    expansionProgress = clampedProgress,
                    originalNoTime = originalNoTime,
                    showSnippet = showSnippet,
                    headerStartColor = backgroundColor
                )
            }
        }
    }
}

@Composable
private fun MeasureCardCompleteStructure(
    title: Title
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
    ) {
        TitlesHeaderContent(
            title = title,
            noTime = false,
            onClicked = {},
            animationProgress = 1f
        )

        Spacer(modifier = Modifier.height(8.dp))

        MarkdownText(
            markdown = title.text.trim(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.2.sp,
                lineHeight = 22.2.sp
            )
        )

        Spacer(modifier = Modifier.height(54.dp))
    }
}

@Composable
private fun TitlesHeaderContent(
    modifier: Modifier = Modifier,
    title: Title,
    noTime: Boolean,
    onClicked: () -> Unit,
    clickableEnabled: Boolean = true,
    animationProgress: Float = 1f
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (clickableEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClicked() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!noTime) {
            val alpha = (animationProgress * 2f).coerceIn(0f, 1f)

            Text(
                text = getFormattedTimeUnix(title.time),
                textAlign = TextAlign.Left,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (placeable.width * animationProgress).roundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .padding(end = (8 * animationProgress).dp, top = 8.dp, bottom = 8.dp)
                    .wrapContentWidth()
                    .alpha(alpha)
            )
        }
        Text(
            text = title.title,
            textAlign = TextAlign.Left,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .weight(1f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ExpandedCardContent(
    title: Title,
    sources: List<SourceMessages>?,
    changeSourceState: (Long, String) -> Unit,
    onBanTheme: (String) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    onCollapse: () -> Unit,
    maxHeight: Dp,
    contentAlpha: Float,
    targetWidth: Dp,
    expansionProgress: Float,
    originalNoTime: Boolean,
    showSnippet: Boolean,
    headerStartColor: Color
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val handler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

    val source = stringResource(R.string.titles_card_source)
    val bottomPanelHeight = 50.dp
    val bottomPanelItemsColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)

    val headerEndColor = MaterialTheme.colorScheme.secondaryContainer

    val shouldAnimateHeader = headerStartColor != headerEndColor

    val copiedText = "${title.title}\n\n${title.text}\n\n${source}: Mews, ${title.sources}"
    fun copyText() {
        clipboardManager.setText(AnnotatedString(copiedText))
    }
    fun shareText() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, copiedText)
        }

        val shareIntent = Intent.createChooser(sendIntent, null)

        context.startActivity(shareIntent)
    }
    fun banNew() {
        onBanTheme(title.title)
    }
    val buttons = listOf(
        TextButtonInputs(stringResource(R.string.share_btn_desc), ::shareText),
        TextButtonInputs(stringResource(R.string.ban_btn_desc), ::banNew, stringResource(R.string.titles_card_banned))
    )

    val headerAnimProgress = if (originalNoTime) expansionProgress else 1f

    LaunchedEffect(pagerState.targetPage) {
        rememberPage(pagerState.targetPage)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .wrapContentHeight()
    ) {
        Surface(
            shape = Shapes.large,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Shapes.large)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onCollapse() }
                .drawBehind {
                    if (!shouldAnimateHeader) {
                        drawRect(headerEndColor)
                    } else {
                        drawRect(headerStartColor)

                        if (expansionProgress > 0f) {
                            drawRect(
                                color = headerEndColor,
                                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(
                                    width = size.width * expansionProgress,
                                    height = size.height
                                )
                            )
                        }
                    }
                }
        ) {
            Column {
                TitlesHeaderContent(
                    title = title,
                    noTime = false,
                    onClicked = {},
                    clickableEnabled = false,
                    animationProgress = headerAnimProgress
                )

                if (showSnippet) {
                    val snippetAlpha = (1f - expansionProgress).coerceIn(0f, 1f)
                    SnippetText(
                        text = title.text,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val visibleHeight = (placeable.height * snippetAlpha).roundToInt()
                                layout(placeable.width, visibleHeight) {
                                    val yOffset = (placeable.height * expansionProgress).roundToInt() * -1
                                    placeable.place(0, yOffset)
                                }
                            },
                        alpha = snippetAlpha
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .requiredWidth(targetWidth)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = contentAlpha }
            ) { page ->
                when (page) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                MarkdownText(
                                    markdown = title.text.trim(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.2.sp,
                                        lineHeight = 22.2.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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
                            Spacer(modifier = Modifier.height(bottomPanelHeight + 4.dp))
                        }
                    }
                    1 -> {
                        if (sources == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = title.sources,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = title.ids,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(bottomPanelHeight + 4.dp))
                            }
                        } else {
                            val allMessages = remember(sources) { sources.flatMap { it.messages } }

                            val minLength = remember(allMessages) { allMessages.minOfOrNull { it.mess.length } ?: 0 }
                            val maxLength = remember(allMessages) { allMessages.maxOfOrNull { it.mess.length } ?: 1 }

                            LazyVerticalGrid(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .fillMaxWidth(),
                                columns = GridCells.Fixed(1)
                            ) {
                                sources.forEach { pack ->
                                    val source = pack.source
                                    val state = pack.state
                                    val messages = pack.messages

                                    customHeader(
                                        text = source,
                                        isExpanded = state,
                                        onHeaderClick = { changeSourceState(title.id, source) },
                                        fontSize = 18.sp
                                    )

                                    item(key = "${title.id}_$source") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ExpandableContainer(visible = state) {
                                                Box(modifier = Modifier.padding(bottom = 16.dp)) {

                                                    FlowRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        maxItemsInEachRow = 5
                                                    ) {
                                                        messages.forEach { item ->
                                                            val padding = remember(item.mess.length, minLength, maxLength) {
                                                                if (maxLength == minLength) {
                                                                    val absoluteMaxChars = 200f
                                                                    val fraction = (item.mess.length / absoluteMaxChars).coerceIn(0f, 1f)
                                                                    12.dp + (48.dp * fraction)
                                                                } else {
                                                                    val fraction = (item.mess.length - minLength).toFloat() / (maxLength - minLength)
                                                                    12.dp + (48.dp * fraction)
                                                                }
                                                            }

                                                            CustomTextButton(
                                                                inputs = TextButtonInputs(
                                                                    getFormattedTimeUnix(item.time),
                                                                    { handler.openUri(item.link) }
                                                                ),
                                                                horizontalPadding = padding,

                                                                defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                                                shape = Shapes.large
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(modifier = Modifier
                                        .height(bottomPanelHeight - 8.dp)
                                        .fillMaxWidth())
                                }
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
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    )
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedSegmentedControl(
                        items = listOf(
                            stringResource(R.string.titles_card_text) to { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            stringResource(R.string.titles_card_source) to { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                        ),
                        selectedIndex = pagerState.currentPage,
                        modifier = Modifier.padding(bottom = 8.dp),
                        backgroundColor = bottomPanelItemsColor,
                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        itemPadding = 2.dp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CustomIconButton(
                        inputs = IconButtonInputs(Icons.Default.MoreVert, {
                            dropdownTransitionState.targetState = !dropdownTransitionState.currentState
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
                        transitionBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
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