package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.getStringsFromDate
import androidx.compose.ui.unit.TextUnit
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes

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
    fontSize: TextUnit = 26.sp,
    buttonsColor: Color? = null,
    bottomPadding: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    stickyHeader {
        val titleText = textId?.let { stringResource(it) } ?: text
        val btnColor = (buttonsColor ?: MaterialTheme.colorScheme.secondaryContainer).copy(alpha = 0.95f)
        val shape = Shapes.large

        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 0f else -90f,
            animationSpec = tween(durationMillis = 250),
            label = "ArrowRotation"
        )

        val animatedBottomPadding by animateDpAsState(
            targetValue = if (isExpanded) bottomPadding * 1.5.toInt() else bottomPadding,
            animationSpec = tween(durationMillis = 250),
            label = "PaddingAnimation"
        )

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(start = 2.dp, top = 8.dp, bottom = animatedBottomPadding, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(
                inputs = TextButtonInputs(
                    text = titleText,
                    action = onHeaderClick
                ),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                enabled = expandable,
                defaultBackgroundColor = btnColor,
                shape = shape,
                modifier = Modifier.fillMaxHeight()
            )

            Spacer(modifier = Modifier.weight(1f))

            if (expandable) {
                CustomIconButton(
                    inputs = IconButtonInputs(
                        icon = Icons.Default.KeyboardArrowDown,
                        action = onHeaderClick
                    ),
                    enabled = true,
                    defaultBackgroundColor = btnColor,
                    modifier = Modifier.rotate(rotation).fillMaxHeight().aspectRatio(1f),
                    shape = CircleShape
                )
            }
        }
    }
}