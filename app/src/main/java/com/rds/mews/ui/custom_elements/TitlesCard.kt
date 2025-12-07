package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.rds.mews.ArrowPosition
import com.rds.mews.R
import com.rds.mews.Title
import com.rds.mews.localcore.getFormattedTimeUnix
import kotlinx.coroutines.launch

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TitlesCard(
    title: Title,
    onBanTheme: (String) -> Unit,
    showDates: Boolean = false,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    pagerState: PagerState,
    rememberPage: (Int) -> Unit,
    noTime: Boolean = false
) {
    var collapsedBounds by remember { mutableStateOf<Rect?>(null) }

    val expansionAnim = remember { Animatable(0f) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            expansionAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
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
                if (expansionAnim.value < 0.001f && !isExpanded) {
                    collapsedBounds = coordinates.boundsInWindow()
                }
            }
            .alpha((1f - expansionAnim.value).coerceIn(0f, 1f)),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        TitlesHeaderContent(
            title = title,
            showDates = showDates,
            noTime = noTime,
            onClicked = onToggleExpanded,
            clickableEnabled = true
        )
    }

    if (showPopup && collapsedBounds != null) {
        HeroExpansionPopup(
            progress = expansionAnim.value,
            collapsedBounds = collapsedBounds!!,
            onDismissRequest = onToggleExpanded,
            content = { maxHeight, contentAlpha, targetWidth ->
                ExpandedCardContent(
                    title = title,
                    onBanTheme = onBanTheme,
                    pagerState = pagerState,
                    rememberPage = rememberPage,
                    onCollapse = onToggleExpanded,
                    maxHeight = maxHeight,
                    contentAlpha = contentAlpha,
                    targetWidth = targetWidth
                )
            }
        )
    }
}

@SuppressLint("LocalContextResourcesRead")
@Composable
private fun HeroExpansionPopup(
    progress: Float,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    content: @Composable (maxHeight: Dp, contentAlpha: Float, targetWidth: Dp) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics

    val screenWidthPx = displayMetrics.widthPixels
    val screenHeightPx = displayMetrics.heightPixels

    val screenWidthDp = with(density) { screenWidthPx.toDp() }
    val screenHeightDp = with(density) { screenHeightPx.toDp() }

    val verticalMarginDp = 50.dp
    val horizontalMarginDp = 16.dp

    val verticalMarginPx = with(density) { verticalMarginDp.toPx() }
    val horizontalMarginPx = with(density) { horizontalMarginDp.toPx() }

    val maxAvailableWidth = screenWidthPx - (horizontalMarginPx * 2)
    val maxAvailableHeight = screenHeightPx - (verticalMarginPx * 2)

    var contentHeight by remember { mutableStateOf<Float?>(null) }

    val maxAvailableHeightDp = with(density) { maxAvailableHeight.toDp() }
    val targetWidthDp = with(density) { maxAvailableWidth.toDp() }

    val clampedProgress = progress.coerceIn(0f, 1f)

    Popup(
        popupPositionProvider = WindowOriginProvider,
        properties = PopupProperties(
            focusable = true,
            clippingEnabled = false
        ),
        onDismissRequest = onDismissRequest
    ) {
        val scrimAlpha = clampedProgress * 0.6f

        val contentAlpha = ((clampedProgress - 0.1f) / 0.9f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .width(screenWidthDp)
                .height(screenHeightDp)
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
                        .offset { IntOffset(horizontalMarginPx.roundToInt(), verticalMarginPx.roundToInt()) }
                        .width(targetWidthDp)
                        .alpha(0f)
                        .onGloballyPositioned { coordinates ->
                            contentHeight = coordinates.size.height.toFloat()
                        }
                ) {
                    content(maxAvailableHeightDp, 0f, targetWidthDp)
                }
            }

            if (contentHeight != null) {
                val targetHeight = min(contentHeight!!, maxAvailableHeight)
                val centeredTop = (screenHeightPx - targetHeight) / 2

                val expandedBounds = Rect(
                    left = horizontalMarginPx,
                    top = centeredTop,
                    right = horizontalMarginPx + maxAvailableWidth,
                    bottom = centeredTop + targetHeight
                )

                val currentRect: Rect = lerp(collapsedBounds, expandedBounds, progress)

                val currentCorner = 25.dp

                Surface(
                    modifier = Modifier
                        .width(with(density) { currentRect.width.toDp() })
                        .height(with(density) { currentRect.height.toDp() })
                        .graphicsLayer {
                            translationX = currentRect.left
                            translationY = currentRect.top

                            shape = RoundedCornerShape(currentCorner)
                            clip = true
                            shadowElevation = 8.dp.toPx() * clampedProgress
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = true
                        ) {},
                    shape = RoundedCornerShape(currentCorner),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .width(targetWidthDp)
                            .fillMaxHeight()
                    ) {
                        content(maxAvailableHeightDp, contentAlpha, targetWidthDp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TitlesHeaderContent(
    modifier: Modifier = Modifier,
    title: Title,
    showDates: Boolean,
    noTime: Boolean,
    onClicked: () -> Unit,
    clickableEnabled: Boolean = true
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
            Text(
                text = getFormattedTimeUnix(title.time, showDates),
                textAlign = TextAlign.Left,
                modifier = Modifier
                    .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .wrapContentWidth()
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

private object WindowOriginProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
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
    targetWidth: Dp
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    var buttonBounds by remember { mutableStateOf<IntRect?>(null) }

    val textSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.onSecondaryContainer,
        backgroundColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
    )

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

    LaunchedEffect(pagerState.targetPage) {
        rememberPage(pagerState.targetPage)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .wrapContentHeight()
    ) {
        TitlesHeaderContent(
            title = title,
            showDates = false,
            noTime = false,
            onClicked = onCollapse
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
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
                        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                            SelectionContainer {
                                Text(
                                    text = title.text,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = title.sources,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold
                        )
                        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                            SelectionContainer {
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
            }
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
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Text(
                text = stringResource(R.string.titles_card_text),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterVertically)
                    .padding(top = 6.dp, bottom = 6.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
                    arrowPosition = ArrowPosition.BottomRight
                )
            }
        }
    }
}