package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.getStringsFromDate
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit

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

fun LazyGridScope.customHeader(
    text: String = "null",
    textId: Int? = null,
    isExpanded: Boolean = false,
    onHeaderClick: () -> Unit = {},
    expandable: Boolean = true,
    fontSize: TextUnit = 30.sp
) {
    stickyHeader {
        val titleText = textId?.let { stringResource(it) } ?: text

        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 0f else -90f,
            animationSpec = tween(durationMillis = 250),
            label = "ArrowRotation"
        )

        val animatedBottomPadding by animateDpAsState(
            targetValue = if (isExpanded) 8.dp else 4.dp,
            animationSpec = tween(durationMillis = 250),
            label = "PaddingAnimation"
        )

        val interactionSource = remember { MutableInteractionSource() }
        val modifier = if (expandable) Modifier
            .clickable(
                onClick = onHeaderClick,
                indication = null,
                interactionSource = interactionSource
            ) else Modifier
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(0.95f)
                )
                .padding(start = 2.dp, top = 16.dp, bottom = animatedBottomPadding, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )

            Spacer(modifier = Modifier.weight(1f))

            if (expandable) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}