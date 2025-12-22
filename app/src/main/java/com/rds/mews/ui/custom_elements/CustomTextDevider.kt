package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.getStringsFromDate

@Composable
fun LegacyTextDivider(text: String? = null, dateString: String? = null, date: Boolean = false) {
    val text = when (date) {
        true -> {
            val ints = getStringsFromDate(dateString ?: "null")
            when (ints) {
                null -> stringResource(R.string.wrong_date)
                else -> stringResource(ints[0], ints[1])
            }
        }
        else -> text ?: "null"
    }

    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 30.sp,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 4.dp, end = 50.dp))
}

fun LazyListScope.customTextDivider(
    text: String? = null,
    dateString: String? = null,
    date: Boolean = false,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    contentList: List<@Composable () -> Unit>? = null,
    expandable: Boolean = true
) {
    stickyHeader {
        // 1. Формируем текст заголовка (логика сохранена)
        val titleText = when (date) {
            true -> {
                val ints = getStringsFromDate(dateString ?: "null")
                when (ints) {
                    null -> stringResource(R.string.wrong_date)
                    else -> stringResource(ints[0], ints[1])
                }
            }
            else -> text ?: "null"
        }

        // Анимация поворота: 0f - смотрит вниз (развернуто), -90f - смотрит вправо (свернуто)
        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 0f else -90f,
            animationSpec = tween(durationMillis = 300),
            label = "ArrowRotation"
        )

        val modifier = if (expandable) Modifier.clickable { onHeaderClick() } else Modifier
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 2.dp, top = 8.dp, bottom = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            if (expandable) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, // Исходная иконка "Вниз"
                    contentDescription = "Expand/Collapse",
                    modifier = Modifier.rotate(rotation) // Применяем анимацию
                )
            }
        }
    }

    // 3. Если развернуто, показываем переданные функции как элементы списка
    if (expandable && isExpanded && contentList != null) {
        items(contentList) { composableContent ->
            composableContent()
        }
    }
}

// --- Пример использования ---

@Preview(showBackground = true)
@Composable
fun PreviewCollapsibleSection() {
    // Состояние храним здесь, так как LazyListScope не может хранить состояние (remember)
    var isSection1Expanded by remember { mutableStateOf(true) }
    var isSection2Expanded by remember { mutableStateOf(false) }

    LazyColumn {
        // Первая секция
        customTextDivider(
            text = "Сегодня",
            isExpanded = isSection1Expanded,
            onHeaderClick = { isSection1Expanded = !isSection1Expanded },
            contentList = listOf(
                { Text("Задача 1", modifier = Modifier.padding(16.dp)) },
                { Text("Задача 2", modifier = Modifier.padding(16.dp)) }
            )
        )

        // Вторая секция (с датой)
        customTextDivider(
            dateString = "10 Октября",
            date = true,
            isExpanded = isSection2Expanded,
            onHeaderClick = { isSection2Expanded = !isSection2Expanded },
            contentList = listOf(
                { Text("Задача на будущее 1", modifier = Modifier.padding(16.dp)) },
                { Text("Задача на будущее 2", modifier = Modifier.padding(16.dp)) },
                { Text("Задача на будущее 3", modifier = Modifier.padding(16.dp)) }
            )
        )
    }
}