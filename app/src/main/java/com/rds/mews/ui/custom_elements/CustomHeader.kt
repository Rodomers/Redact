package com.rds.mews.ui.custom_elements

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.getStringsFromDate
import com.rds.mews.ui.theme.Shapes

@Composable
fun LegacyTextDivider(text: String? = null, dateString: String? = null, date: Boolean = false) {
    val formattedText = when (date) {
        true -> {
            val ints = getStringsFromDate(dateString ?: "null")
            when (ints) {
                null -> stringResource(R.string.wrong_date)
                else -> stringResource(ints[0], ints[1])
            }
        }
        else -> text ?: "null"
    }

    Text(
        text = formattedText,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 4.dp, end = 50.dp)
    )
}

@Composable
fun CustomHeaderContent(
    text: String = "null",
    textId: Int? = null,
    isExpanded: Boolean = false,
    onHeaderClick: () -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    expandable: Boolean = true,
    fontSize: TextUnit = 26.sp,
    buttonsColor: Color? = null,
    bottomPadding: Dp = 6.dp,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val titleText = textId?.let { stringResource(it) } ?: text
    val btnColor = (buttonsColor ?: MaterialTheme.colorScheme.secondaryContainer).copy(alpha = 0.95f)
    val shape = Shapes.large

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250),
        label = "ArrowRotation"
    )

    val animatedBottomPadding by animateDpAsState(
        targetValue = if (isExpanded) (bottomPadding.value * 1.5f).dp else bottomPadding,
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
                action = onTextClick ?: onHeaderClick
            ),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            enabled = expandable,
            defaultBackgroundColor = btnColor,
            shape = shape,
            modifier = Modifier.fillMaxHeight(),
            blurIfDisabled = false
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
                modifier = Modifier
                    .rotate(rotation)
                    .fillMaxHeight()
                    .aspectRatio(1f),
                shape = CircleShape
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.customHeader(
    text: String = "null",
    textId: Int? = null,
    isExpanded: Boolean = false,
    onHeaderClick: () -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    expandable: Boolean = true,
    fontSize: TextUnit = 26.sp,
    buttonsColor: Color? = null,
    bottomPadding: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    stickyHeader {
        CustomHeaderContent(
            text = text,
            textId = textId,
            isExpanded = isExpanded,
            onHeaderClick = onHeaderClick,
            onTextClick = onTextClick,
            expandable = expandable,
            fontSize = fontSize,
            buttonsColor = buttonsColor,
            bottomPadding = bottomPadding,
            modifier = modifier
        )
    }
}

fun LazyStaggeredGridScope.customHeader(
    text: String = "null",
    textId: Int? = null,
    isExpanded: Boolean = false,
    onHeaderClick: () -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    expandable: Boolean = true,
    fontSize: TextUnit = 26.sp,
    buttonsColor: Color? = null,
    bottomPadding: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    item(span = StaggeredGridItemSpan.FullLine) {
        CustomHeaderContent(
            text = text,
            textId = textId,
            isExpanded = isExpanded,
            onHeaderClick = onHeaderClick,
            onTextClick = onTextClick,
            expandable = expandable,
            fontSize = fontSize,
            buttonsColor = buttonsColor,
            bottomPadding = bottomPadding,
            modifier = modifier
        )
    }
}