package com.rds.mews

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup


@Composable
fun SettingsGrid(modifier: Modifier, settingsModel: SettingsViewModel) {
//    var darkTheme by remember { mutableStateOf(false) }
//    var adaptiveColors by remember {mutableStateOf(false)}
    val titlesDropdownVisible = remember { MutableTransitionState(false) }
    val titlesDropdownItems = listOf(
        "10 заголовков" to { settingsModel.titlesNum.intValue = 10 },
        "25 заголовков" to { settingsModel.titlesNum.intValue = 25 },
        "50 заголовков" to { settingsModel.titlesNum.intValue = 50 },
        "75 заголовков" to { settingsModel.titlesNum.intValue = 75 },
        "100 заголовков" to { settingsModel.titlesNum.intValue = 100 }
    )
    val limitationDropdownVisible = remember { MutableTransitionState(false) }
    val limitationDropdownItems = listOf(
        "24 часа" to { settingsModel.titlesPeriod.intValue = 24 },
        "48 часов" to { settingsModel.titlesPeriod.intValue = 48 },
        "72 часа" to { settingsModel.titlesPeriod.intValue = 72 },
        "96 часов" to { settingsModel.titlesPeriod.intValue = 96 },
        "120 часов" to { settingsModel.titlesPeriod.intValue = 120 }
    )

    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
//        CustomSettingsItem(text = "Тёмная тема") {
//            Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
//        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            CustomSettingsItem(text = "Адаптивные цвета") {
//                Switch(
//                    checked = adaptiveColors, onCheckedChange = { adaptiveColors = it }
//                )
//            }
//        }
        CustomSettingsItem(text = "Количество заголовков") {
            Box() {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { titlesDropdownVisible.targetState = !titlesDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = "${settingsModel.titlesNum.intValue} заголовков")
                }

                if (titlesDropdownVisible.currentState || titlesDropdownVisible.targetState) {
                    Popup(
                        onDismissRequest = { titlesDropdownVisible.targetState = false },
                        alignment = Alignment.TopEnd
                    ) {
                        CustomDropdown(transitionState = titlesDropdownVisible, buttons = titlesDropdownItems)
                    }
                }
            }
        }
        CustomSettingsItem(text = "Срок давности новостей") {
            Box() {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { limitationDropdownVisible.targetState = !limitationDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = "${settingsModel.titlesPeriod.intValue} часа")
                }

                if (limitationDropdownVisible.currentState || limitationDropdownVisible.targetState) {
                    Popup(
                        onDismissRequest = { limitationDropdownVisible.targetState = false },
                        alignment = Alignment.TopEnd
                    ) {
                        CustomDropdown(transitionState = limitationDropdownVisible, buttons = limitationDropdownItems)
                    }
                }
            }
        }
    }
}