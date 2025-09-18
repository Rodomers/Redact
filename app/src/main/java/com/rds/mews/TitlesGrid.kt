package com.rds.mews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitlesGrid(itemsList: List<Title>,
               modifier: Modifier,
               isRefreshing: Boolean,
               onRefresh: () -> Unit,
               settingsViewModel: SettingsViewModel,
               closeIndicator: () -> Unit,
               scope: CoroutineScope
) {
    val showDates by remember { mutableStateOf(settingsViewModel.showDates.value) }
    val pullToRefreshState = rememberPullToRefreshState()

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val errState by settingsViewModel.lastError.collectAsStateWithLifecycle()

    LaunchedEffect(errState) {
        when (errState) {
            null -> bottomSheetState.show()
            else -> if (bottomSheetState.isVisible) bottomSheetState.hide()
        }
    }

    if (errState != null) {
        val err = errState!!
        val resources = remember(err) { mapResultToUiResources(err) }

        closeIndicator()

        CustomErrorBottomSheet(
            title = stringResource(resources[0]),
            text = stringResource(resources[1]),
            confBtnText = stringResource(resources[2]),
            cancelBtnText = stringResource(R.string.cancel),
            onDismissRequest = {
                settingsViewModel.clearError()
                settingsViewModel.setUpdatingState("off")
                               },
            onConfirm = {
                when (err.type) {
                    in listOf(SummarizationErrorType.EXTRACT_TOPICS_FAILED,
                        SummarizationErrorType.SUMMARIZE_TOPICS_FAILED) -> {
                        onRefresh()
                    }
                    else -> { onRefresh() }
                }

                scope.launch { if (bottomSheetState.isVisible) bottomSheetState.hide() }
                    .invokeOnCompletion { if (!bottomSheetState.isVisible) settingsViewModel.clearError() }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = { CustomPullToRefreshIndicator(
            state = pullToRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter),
            isRefreshing = isRefreshing
        ) }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (itemsList.isEmpty() && !isRefreshing) {
                item {
                    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.titles_update_text),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(40.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            items(items = itemsList, key = { item -> item.id }) { item ->
                TitlesCard(item, showDates)
            }
        }
    }
}