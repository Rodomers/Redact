package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rds.mews.ArrowPosition
import com.rds.mews.R
import com.rds.mews.Title
import com.rds.mews.localcore.getFormattedTimeUnix
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TitlesCard(
    title: Title,
    onBanTheme: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    noTime: Boolean = false
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
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    val showPopup = expansionAnim.value > 0.001f || isExpanded

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                collapsedBounds = coordinates.boundsInWindow()
            }
            .alpha(if (isPopupReady && expansionAnim.value > 0.02f) 0f else 1f),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 0.dp
    ) {
        TitlesHeaderContent(
            title = title,
            noTime = noTime,
            onClicked = onToggleExpanded,
            clickableEnabled = true
        )
    }

    if (showPopup && collapsedBounds != null) {
        RootViewOverlay(
            onDismissRequest = onToggleExpanded
        ) {
            HeroExpansionContent(
                title = title,
                progress = expansionAnim.value,
                collapsedBounds = collapsedBounds!!,
                onDismissRequest = onToggleExpanded,
                onReady = { isPopupReady = true },
                onBanTheme = onBanTheme,
                pagerState = pagerState,
                rememberPage = rememberPage,
                originalNoTime = noTime
            )
        }
    }
}

@Composable
fun RootViewOverlay(
    onDismissRequest: () -> Unit,
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
    progress: Float,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    onReady: () -> Unit,
    onBanTheme: (String) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    originalNoTime: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val verticalMarginDp = 50.dp
    val horizontalMarginDp = 8.dp
    val horizontalMarginPx = with(density) { horizontalMarginDp.toPx() }
    val verticalMarginPx = with(density) { verticalMarginDp.toPx() }

    val clampedProgress = progress.coerceIn(0f, 1f)

    BackHandler {
        onDismissRequest()
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = true
            ) {
                onDismissRequest()
            }
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
                MeasureCardCompleteStructure(title, onBanTheme)
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

        val currentContainerColor = lerp(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.surface,
            progress
        )
        val currentCorner = 25.dp

        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationX = currentRect.left
                    translationY = currentRect.top
                    shape = RoundedCornerShape(currentCorner)
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .layout { measurable, constraints ->
                    val currentW = currentRect.width.roundToInt()
                    val currentH = currentRect.height.roundToInt()

                    val placeable = measurable.measure(
                        Constraints.fixed(currentW, currentH)
                    )

                    layout(currentW, currentH) {
                        placeable.place(0, 0)
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = true
                ) {},
            shape = RoundedCornerShape(currentCorner),
            color = currentContainerColor,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .width(targetWidthDp)
                    .fillMaxHeight()
            ) {
                ExpandedCardContent(
                    title = title,
                    onBanTheme = onBanTheme,
                    pagerState = pagerState,
                    rememberPage = rememberPage,
                    onCollapse = onDismissRequest,
                    maxHeight = maxAvailableHeightDp,
                    contentAlpha = contentAlpha,
                    targetWidth = targetWidthDp,
                    expansionProgress = clampedProgress,
                    originalNoTime = originalNoTime
                )
            }
        }
    }
}

@Composable
private fun MeasureCardCompleteStructure(
    title: Title,
    onBanTheme: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        TitlesHeaderContent(
            title = title,
            noTime = false,
            onClicked = {},
            animationProgress = 1f
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title.text,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.titles_card_text),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterVertically)
                    .padding(top = 6.dp, bottom = 6.dp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.titles_card_source),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentHeight()
                    .align(Alignment.CenterVertically)
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .align(Alignment.CenterVertically)
            )
        }
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = clickableEnabled
            ) { onClicked() }
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
    onBanTheme: (String) -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    onCollapse: () -> Unit,
    maxHeight: Dp,
    contentAlpha: Float,
    targetWidth: Dp,
    expansionProgress: Float,
    originalNoTime: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

    val source = stringResource(R.string.titles_card_source)
    val toastText = stringResource(R.string.titles_card_copied)

    fun copyText() {
        val copiedText = "${title.title}\n\n${title.text}\n\n${source}: ${title.sources}"
        clipboardManager.setText(AnnotatedString(copiedText))
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
    fun banNew() {
        onBanTheme(title.title)
        Toast.makeText(context, R.string.titles_card_banned, Toast.LENGTH_SHORT).show()
    }
    val buttons = listOf(
        Pair(stringResource(R.string.share_btn_desc), ::copyText),
        Pair(stringResource(R.string.ban_btn_desc), ::banNew)
    )

    val headerAnimProgress = if (originalNoTime) {
        expansionProgress
    } else {
        1f
    }

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
            shape = RoundedCornerShape(25.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            TitlesHeaderContent(
                title = title,
                noTime = false,
                onClicked = onCollapse,
                animationProgress = headerAnimProgress
            )
        }

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .requiredWidth(targetWidth)
                .weight(1f, fill = false)
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
                        Text(
                            text = title.text,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                1 -> {
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
                            text = title.links,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .graphicsLayer { alpha = contentAlpha }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Text(
                text = stringResource(R.string.titles_card_text),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterVertically)
                    .padding(top = 6.dp, bottom = 6.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                fontWeight = if (pagerState.targetPage == 0) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = stringResource(R.string.titles_card_source),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentHeight()
                    .align(Alignment.CenterVertically)
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                fontWeight = if (pagerState.targetPage == 1) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.weight(1f))
            CustomIconButton(
                icon = Icons.Default.MoreVert,
                onClick = { dropdownTransitionState.targetState = !dropdownTransitionState.currentState },
                modifier = Modifier
                    .height(24.dp)
                    .align(Alignment.CenterVertically)
                    .onGloballyPositioned { buttonBounds = it.boundsInWindow().roundToIntRect() },
                iconModifier = Modifier.size(16.dp)
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