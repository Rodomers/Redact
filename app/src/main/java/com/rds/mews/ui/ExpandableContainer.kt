package com.rds.mews.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

@Composable
fun ExpandableContainer(
    visible: Boolean,
    animationDuration: Int = 250,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(animationDuration)
        ) + fadeIn(animationSpec = tween(animationDuration)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(animationDuration)
        ) + fadeOut(animationSpec = tween(animationDuration)),
        content = content
    )
}