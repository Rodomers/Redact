package com.rds.mews.ui.custom_elements

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.rds.mews.R
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes

@Composable
fun SourcesCard(
    source: String,
    buttons: List<TextButtonInputs>
) {
    val transitionState = remember { MutableTransitionState(false) }
    val toggleDropdown = { transitionState.targetState = !transitionState.currentState }
    var bounds by remember { mutableStateOf<IntRect?>(null) }

    val primaryColor = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = Shapes.large,
        color = Color.Transparent,
        border = BorderStroke(4.dp, primaryColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(primaryColor)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = source,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
            CustomIconButton(
                inputs = IconButtonInputs(Icons.Default.MoreVert, { toggleDropdown() }),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = 4.dp, top = 4.dp, end = 4.dp)
                    .width(50.dp)
                    .aspectRatio(1f)
                    .onGloballyPositioned { bounds = it.boundsInWindow().roundToIntRect() },
                transitionState = transitionState,
                transitionBackgroundColor = primaryColor
            )

            if (transitionState.currentState || transitionState.targetState) {
                CustomDropdown(
                    transitionState = transitionState,
                    buttons = buttons,
                    inputBounds = bounds,
                    config = LocalConfiguration.current,
                    density = LocalDensity.current,
                    onDismissRequest = { transitionState.targetState = false }
                )
            }
        }
    }
}

@Composable
fun SourcesAddCard(
    action: () -> Unit,
    transitionState: Boolean? = null
) {
    val buttonTransitionState = remember { MutableTransitionState(transitionState == true) }
    buttonTransitionState.targetState = transitionState == true

    val primaryColor = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = Shapes.large,
        color = Color.Transparent,
        border = BorderStroke(4.dp, primaryColor),
        shadowElevation = 0.dp
    ) {
        CustomTextButton(
            inputs = TextButtonInputs(stringResource(R.string.sources_add_text), action),
            modifier = Modifier.fillMaxSize(),
            transitionState = buttonTransitionState,
            transitionBackgroundColor = primaryColor,
            defaultBackgroundColor = Color.Transparent,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}