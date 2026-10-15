package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceBottomSheet(
    rssLinkValue: String,
    onRssLinkChange: (String) -> Unit,
    sourceNameValue: String,
    onSourceNameChange: (String) -> Unit,
    isRssValid: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    val textFieldsColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer

    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = stringResource(R.string.change_dialog_add_source)
    ) { closeSheet ->
        listOf(
            {
                TextField(
                    value = rssLinkValue,
                    onValueChange = onRssLinkChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_link)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = textFieldsColor,
                        unfocusedContainerColor = textFieldsColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = onTextFieldColor,
                        focusedLabelColor = onTextFieldColor,
                        unfocusedLabelColor = onTextFieldColor,
                    ),
                    trailingIcon = {
                        if (rssLinkValue.isNotEmpty()) {
                            if (isRssValid) {
                                Icon(
                                    Icons.Default.Check,
                                    "Valid",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    Icons.Default.Close,
                                    "Invalid",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            },
            {
                TextField(
                    value = sourceNameValue,
                    onValueChange = onSourceNameChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_source)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = textFieldsColor,
                        unfocusedContainerColor = textFieldsColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = onTextFieldColor,
                        focusedLabelColor = onTextFieldColor,
                        unfocusedLabelColor = onTextFieldColor,
                    ),
                    singleLine = true
                )
            },
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.cancel),
                            action = { closeSheet() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canAdd =
                        isRssValid && rssLinkValue.isNotBlank() && sourceNameValue.isNotBlank()

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.add),
                            action = {
                                if (canAdd) {
                                    onConfirm()
                                    closeSheet()
                                }
                            }
                        ),
                        shape = Shapes.large,
                        enabled = canAdd,
                        defaultBackgroundColor = if (canAdd) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                }
            }
        )
    }
}