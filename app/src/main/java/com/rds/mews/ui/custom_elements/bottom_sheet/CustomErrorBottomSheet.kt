package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomErrorBottomSheet(
    title: String,
    text: String,
    confBtnText: String,
    cancelBtnText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = title
    ) { closeSheet ->
        listOf(
            {
                Text(
                    text = text,
                    textAlign = TextAlign.Start,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .fillMaxWidth()
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
                            text = cancelBtnText,
                            action = { closeSheet() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = confBtnText,
                            action = {
                                onConfirm()
                                closeSheet()
                            }
                        ),
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
        )
    }
}