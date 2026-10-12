package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListBottomSheet(
    title: String,
    items: List<@Composable () -> Unit>,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = title,
        maxHeight = 600.dp,
        content = { _ -> items },
        footer = { closeSheet ->
            Row {
                Spacer(modifier = Modifier.weight(1f))
                CustomTextButton(
                    inputs = TextButtonInputs(
                        text = stringResource(R.string.settings_close),
                        action = { closeSheet() }
                    ),
                    shape = Shapes.large,
                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }
    )
}