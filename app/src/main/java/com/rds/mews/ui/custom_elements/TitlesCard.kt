package com.rds.mews.ui.custom_elements

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.rds.mews.R
import com.rds.mews.Title
import com.rds.mews.getFormattedTimeUnix
import kotlinx.coroutines.launch

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