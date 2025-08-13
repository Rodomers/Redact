package com.rds.mews

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch

@Composable
fun CustomDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<Pair<String, () -> Unit>>,
    animDuration: Int = 200
) {Surface(
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = expandVertically(
                expandFrom = Alignment.Top,
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
            Column {
                buttons.forEachIndexed { index, button ->
                    CustomDropdownMenuItem(button.first, button.second, onDismiss = onDismiss)
                    if (index < buttons.size - 1) {
                        HorizontalDivider(
                            color = Color.Gray,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
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
            .clickable {
                onClick()
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
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
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.LightGray,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
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
                    .background(color = Color.Gray)
                    .fillMaxHeight()
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Меню")
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
fun TitlesCard(title: Title) {
    var expanded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f, pageCount = {2})
    val coroutineScope = rememberCoroutineScope()
    var pagerContentHeight by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(25.dp),
        color = Color.LightGray
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(interactionSource = interactionSource, indication = null) {expanded = !expanded},
            ) {
                Text(
                    text = "14:88",
                    textAlign = TextAlign.Left,
                    modifier = Modifier.padding(8.dp).width(40.dp),
                    color = Color.Black
                )
                Text(
                    text = title.title,
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .padding(8.dp)
                        .wrapContentHeight(),
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                val pagerModifier = pagerContentHeight?.let {
                    Modifier.height(with(density) { it.toDp() })
                } ?: Modifier.wrapContentHeight()

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 10.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = pagerModifier,
                    verticalAlignment = Alignment.Top
                ) {
                        page ->
                    when (page) {
                        0 -> {
                            Text(text = title.text,
                                modifier = Modifier.onSizeChanged { newSize ->
                                    pagerContentHeight = newSize.height
                                })
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(
                                    text = title.sources, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = title.links, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                ) {
                    Text(
                        text = "Текст",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .height(20.dp)
                            .wrapContentWidth()
                            .padding(horizontal = 4.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "Источник",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .height(20.dp)
                            .wrapContentWidth()
                            .padding(horizontal = 4.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 1 ) FontWeight.Bold else FontWeight.Normal
                    )
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
        shape = RoundedCornerShape(25.dp),
        color = Color.LightGray
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

@Preview
@Composable
fun TitleTest() {
    val title = Title(
        id = 5,
        time = 1755152804,
        title = "\"Зелёный оазис\": Новый дизайн-проект обещает преобразить центр города",
        text = "Архитектурная компания \"ГринВью\" представила инновационный дизайн-проект \"Зелёный оазис\", который призван превратить заброшенный сквер в самом центре города в современное и экологически чистое общественное пространство. Проект включает в себя установку вертикальных садов, использование энергоэффективного освещения и создание зон для отдыха с доступом к бесплатному Wi-Fi. Ожидается, что реализация проекта начнётся уже в следующем году. Представители городской администрации выразили полную поддержку инициативе, отметив её важность для повышения качества жизни горожан и улучшения экологической обстановки. \"Это не просто парк, а полноценная экосистема, которая будет служить примером для будущих городских проектов\", — заявил главный архитектор города.",
        sources = "\"ГринВью\", Городская администрация, Пресс-служба мэрии, \"Вестник города\"",
        links = "https://greenview.arch/project_oasis\nhttps://mayor.city/press/green_oasis\nhttps://gorod.vestnik/news/zeleniy_oazis"
    )
    TitlesCard(title)
}