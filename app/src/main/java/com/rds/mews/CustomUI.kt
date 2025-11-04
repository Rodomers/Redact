package com.rds.mews

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class CenteredPopupPositionProvider(
    private val inputBounds: IntRect
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val anchorCenterX = inputBounds.left + inputBounds.width / 2
        val anchorCenterY = inputBounds.top + inputBounds.height / 2

        val popupX = anchorCenterX - popupContentSize.width / 2
        val popupY = anchorCenterY - popupContentSize.height / 2

        return IntOffset(popupX, popupY)
    }
}

@Composable
fun CustomDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<Pair<String, () -> Unit>>,
    animDuration: Int = 200,
    timeList: Boolean = false
) {
    val surfaceWidth = if (timeList) 70.dp else 150.dp
    val maxHeight = if (timeList) 180.dp else 360.dp

    Surface(
        modifier = Modifier.width(surfaceWidth).heightIn(max = maxHeight),
        shape = Shapes.small,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = expandVertically(
                expandFrom = Alignment.CenterVertically,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeOut()
        ) {
            val onDismiss = remember(transitionState) { { transitionState.targetState = false } }
            LazyColumn {
                buttons.forEachIndexed { index, button ->
                    item { CustomDropdownMenuItem(button.first, button.second, onDismiss = onDismiss) }
                    if (index < buttons.size - 1) {
                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 4.dp)
            .clickable {
                onClick()
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun CustomCardWithMenu(
    text: String,
    buttons: List<Pair<String, () -> Unit>>
) {
    val transitionState = remember { MutableTransitionState(false) }
    val toggleDropdown = { transitionState.targetState = !transitionState.currentState }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {toggleDropdown()},
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc))
            }

            if (transitionState.currentState || transitionState.targetState) {
                Popup(
                    onDismissRequest = { transitionState.targetState = false },
                    alignment = Alignment.TopEnd
                ) {
                    CustomDropdown(transitionState = transitionState, buttons = buttons)
                }
            }
        }
    }
}

@Composable
fun SourcesAddCard(
    action: () -> Unit
) { Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sources_add_text),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = action,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxHeight()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.sources_add_icon_desc))
            }
        }
    }
}

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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var page0Height by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val textSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.onSecondaryContainer,
        backgroundColor = MaterialTheme.colorScheme.onSecondary.copy(alpha=0.8f)
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

    val dropdownTransitionState = remember { MutableTransitionState(false) }
    val toggleDropdown = { dropdownTransitionState.targetState = !dropdownTransitionState.currentState }
    val buttons = listOf(
        Pair(stringResource(R.string.share_btn_desc), ::copyText),
        Pair(stringResource(R.string.ban_btn_desc), ::banNew)
    )


    val pagerHeight: Dp? = remember(page0Height) {
        page0Height?.let{
            with(density) { it.toDp() }
        }
    }

    LaunchedEffect(pagerState.targetPage) {
        rememberPage(pagerState.targetPage)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onToggleExpanded() },
            ) {
                if (!noTime) {
                    Text(
                        text = getFormattedTimeUnix(title.time, showDates),
                        textAlign = TextAlign.Left,
                        modifier = Modifier
                            .padding(8.dp)
                            .wrapContentWidth()
                    )
                }
                Text(
                    text = title.title,
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .wrapContentHeight()
                        .align(Alignment.CenterVertically),
                    fontWeight = FontWeight.Bold
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp))

                HorizontalPager(
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .heightIn(max = 6000.dp)
                        .fillMaxWidth()
                        .let { baseModifier ->
                            if (pagerHeight != null) baseModifier.height(pagerHeight)
                            else baseModifier.alpha(0f)
                        }
                ) {page ->
                    when (page) {
                        0 -> {
                            CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                                SelectionContainer(
                                    modifier = Modifier.onSizeChanged { size -> page0Height = size.height }
                                ) {
                                    Text(
                                        text = title.text
                                    )
                                }
                            }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(
                                    text = title.sources, modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    fontWeight = FontWeight.Bold
                                )

                                CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                                    SelectionContainer {
                                        Text(text = title.links, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = stringResource(R.string.titles_card_text),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.CenterVertically)
                            .padding(top = 6.dp, bottom = 6.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = stringResource(R.string.titles_card_source),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentHeight()
                            .align(Alignment.CenterVertically)
                            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 1 ) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = toggleDropdown,
                        modifier = Modifier.height(24.dp).align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.moreVert_btn_desc)
                        )
                    }

                    if (dropdownTransitionState.currentState || dropdownTransitionState.targetState) {
                        Popup(
                            onDismissRequest = { dropdownTransitionState.targetState = false },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = dropdownTransitionState,
                                buttons = buttons
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomSettingsItem(text: String, item: @Composable () -> Unit) {
     Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = Shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Left,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            item()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPullToRefreshIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean
) {
    var indicatorHeight by remember { mutableIntStateOf(0) }

    val refreshThreshold = 80.dp
    val refreshThresholdPx = with(LocalDensity.current) { refreshThreshold.toPx() }
    val scale = if (isRefreshing) 1f else lerp(0f, 1f, state.distanceFraction.coerceIn(0f, 1f))

    val currentUpdatingState by MewsRepository.updatingState.collectAsStateWithLifecycle()
    val text = when {
        currentUpdatingState == "summarizing_topics" -> stringResource(R.string.summarizing, 0, 0)
        currentUpdatingState?.contains("/") ?: false -> {
            val args = currentUpdatingState!!.split("/").map { it.toInt() }
            stringResource(R.string.summarizing, args[0], args[1])
        }
        currentUpdatingState == "extracting_topics" -> stringResource(R.string.extracting_topics)
        currentUpdatingState == "updating" -> stringResource(R.string.updating)
        currentUpdatingState == "filtering_topics" -> stringResource(R.string.filtering_topics)
        else -> stringResource(R.string.update)
    }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .onSizeChanged { size ->
                indicatorHeight = size.height
            }
            .graphicsLayer {
                scaleY = scale
                alpha = scale

                translationY = state.distanceFraction * refreshThresholdPx - indicatorHeight
            }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .statusBarsPadding()
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = Shapes.large,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp)
            )
//            TextButton(onClick = stopFunc) {
//                Text(stringResource(R.string.restart_updating))
//            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomChangeBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (Pair<String, String>) -> Unit,
    add: Boolean = false,
    source: String = stringResource(R.string.change_dialog_source),
    scope: CoroutineScope,
    sheetState: SheetState,
    sourceLink: String? = null
) {
    val title = if (add) stringResource(R.string.change_dialog_add_source) else stringResource(R.string.change_dialog_change_source)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        val closeSheet = {
            scope.launch { sheetState.hide() }
                .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .fillMaxWidth()
            )

            var rssText by remember { mutableStateOf("") }
            var sourceText by remember { mutableStateOf(if (!add) source else "") }
            var validRss by remember { mutableStateOf(!add) }

            if (add) {
                OutlinedTextField(
                    value = rssText,
                    onValueChange = {
                        rssText = it
                        validRss = false
                        scope.launch(Dispatchers.IO) {
                            val res = RSSName(linkTransform(rssText))
                            if (res != null) {
                                sourceText = res
                                validRss = true
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_link)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }

            OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it },
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.change_dialog_source)) },
                placeholder = { Text(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                )
            )

            if (add) {
                Text(
                    text = if (validRss) stringResource(R.string.valid_link) else stringResource(R.string.enter_correct_link),
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (!add && sourceLink != null) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = buildAnnotatedString {
                        append(stringResource(R.string.link))

                        withLink(
                            link = LinkAnnotation.Url(
                                url = sourceLink,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(source)
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { closeSheet() }
                ) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }

                if ((validRss && rssText.isNotBlank() && sourceText.isNotBlank() && add)) {
                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val finalSourceName = sourceText.ifEmpty { source }
                            onConfirm(Pair(finalSourceName, linkTransform(rssText.trim())))
                            closeSheet()
                        },
                        modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                    ) {
                        Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (!add && sourceText.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val finalSourceName = sourceText.ifEmpty { source }
                            onConfirm(Pair(source, finalSourceName))
                            closeSheet()
                        },
                        modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                    ) {
                        Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomErrorBottomSheet(
    title: String,
    text: String,
    confBtnText: String,
    cancelBtnText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    scope: CoroutineScope,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        val closeSheet = {
            scope.launch { sheetState.hide() }
                .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .fillMaxWidth()
            )
            Text(
                text = text,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        closeSheet()
                    }
                ) {
                    Text(cancelBtnText, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = {
                        onConfirm()
                        closeSheet()
                    },
                    modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                ) {
                    Text(confBtnText, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun CustomTextDivider(text: String? = null, dateString: String? = null, date: Boolean = false) {
    val text = when (date) {
        true -> {
            val formattedDate = dateString?.split(".") ?: "00".split(".")

            when (formattedDate.last().toInt()) {
                1 -> stringResource(R.string.date_01, formattedDate.first().toInt())
                2 -> stringResource(R.string.date_02, formattedDate.first().toInt())
                3 -> stringResource(R.string.date_03, formattedDate.first().toInt())
                4 -> stringResource(R.string.date_04, formattedDate.first().toInt())
                5 -> stringResource(R.string.date_05, formattedDate.first().toInt())
                6 -> stringResource(R.string.date_06, formattedDate.first().toInt())
                7 -> stringResource(R.string.date_07, formattedDate.first().toInt())
                8 -> stringResource(R.string.date_08, formattedDate.first().toInt())
                9 -> stringResource(R.string.date_09, formattedDate.first().toInt())
                10 -> stringResource(R.string.date_10, formattedDate.first().toInt())
                11 -> stringResource(R.string.date_11, formattedDate.first().toInt())
                12 -> stringResource(R.string.date_12, formattedDate.first().toInt())
                else -> stringResource(R.string.wrong_date)
            }
        }
        else -> text ?: "null"
    }

    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 4.dp, end = 50.dp))
}

@Composable
fun CustomBottomFootnote(text: String) {
    Text(text = text, fontWeight = FontWeight.Normal, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp))
}

@Composable
fun CustomTimeMark(time: Long) {
    Text(text = getFormattedTimeUnix(time).split(":").joinToString("\n"), fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Start,
        modifier = Modifier
            .padding(top = 4.dp, end = 10.dp)
            .width(30.dp))
}

@Composable
fun DeferredUpdateTab(
    transitionState: MutableTransitionState<Boolean>,
    onDismissRequest: () -> Unit,
    animDuration: Int = 200,
    items: List<@Composable () -> Unit>,
    indexes: List<Int>? = null,
    header: String? = null
) {
    LaunchedEffect(transitionState.targetState) {
        if (transitionState.currentState != transitionState.targetState && !transitionState.targetState) {
            delay(timeMillis = animDuration.toLong())
            onDismissRequest()
        }
    }

    if (transitionState.targetState || transitionState.currentState) {
        Dialog(
            onDismissRequest = {
                transitionState.targetState = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(animDuration)),

                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = animDuration)
                ) + fadeOut(animationSpec = tween(durationMillis = animDuration))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                    shape = Shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.wrapContentSize()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 360.dp)
                        ) {
                            if (header != null) {
                                stickyHeader() {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CustomTextDivider(text = header)
                                    }
                                }
                            }

                            items.forEachIndexed { id, it ->
                                if (indexes?.contains(id) ?: true) item { it() }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(4.dp))
                        Row(
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .padding()
                                .heightIn(max = 35.dp)
                        ) {
                            IconButton(onClick = { transitionState.targetState = false },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, bottom = 4.dp, top = 0.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc))
                            }
                            Spacer(modifier = Modifier
                                .heightIn(max = 40.dp)
                                .weight(1f))
                        }
                    }
                }
            }
        }
    }
}