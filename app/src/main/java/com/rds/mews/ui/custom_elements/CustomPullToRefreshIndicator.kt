package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.R
import com.rds.mews.ui.theme.Shapes
import kotlin.text.split

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
                val isHeightKnown = indicatorHeight > 0
                scaleY = scale
                alpha = if (isHeightKnown) scale else 0f

                if (isHeightKnown) translationY = state.distanceFraction * refreshThresholdPx - indicatorHeight
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