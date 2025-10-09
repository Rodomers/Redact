package com.rds.mews

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@Composable
fun SettingsGrid(gridState: LazyGridState, modifier: Modifier, settingsModel: SettingsViewModel) {
    val clipboardManager = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    val showDates by settingsModel.showDates.collectAsStateWithLifecycle()
    val compactTab by settingsModel.compactTabBar.collectAsStateWithLifecycle()
    val monetColors by settingsModel.isMonetColors.collectAsStateWithLifecycle()
    val filterTopics by settingsModel.filterTopics.collectAsStateWithLifecycle()
    val titlesNum by settingsModel.titlesNum.collectAsStateWithLifecycle()
    val defaultGeminiApiKey by remember { mutableStateOf(settingsModel.defaultApiKey) }
    val geminiApiText by settingsModel.userApi.collectAsStateWithLifecycle()
    val currentLlmModel by settingsModel.currentLlm.collectAsStateWithLifecycle()
    val titlesPeriod by settingsModel.titlesPeriod.collectAsStateWithLifecycle()
    val rssUpdateInterval by settingsModel.rssUpdateInterval.collectAsStateWithLifecycle()
    val endureTime by settingsModel.endureTime.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val colorSchemeDropdownVisible = remember { MutableTransitionState(false) }
    val currentTheme by settingsModel.currentTheme.collectAsStateWithLifecycle()
    val colorSchemeDropdownItems = mutableListOf(
        stringResource(R.string.settings_system_theme) to { settingsModel.setCurrentTheme("system") },
        stringResource(R.string.settings_light_theme) to { settingsModel.setCurrentTheme("light") },
        stringResource(R.string.settings_dark_theme) to { settingsModel.setCurrentTheme("dark") }
    )

    val titlesDropdownVisible = remember { MutableTransitionState(false) }
    val titlesDropdownItems = mutableListOf(
        pluralStringResource(R.plurals.titles, count = 10, 10) to { settingsModel.setTitlesNum(10) },
        pluralStringResource(R.plurals.titles, count = 20, 20) to { settingsModel.setTitlesNum(20) },
    )
    if (geminiApiText != defaultGeminiApiKey) {
        titlesDropdownItems.addAll(listOf(
            pluralStringResource(R.plurals.titles, count = 30, 30) to { settingsModel.setTitlesNum(30) },
            pluralStringResource(R.plurals.titles, count = 40, 40) to { settingsModel.setTitlesNum(40) },
            pluralStringResource(R.plurals.titles, count = 50, 50) to { settingsModel.setTitlesNum(50) })
        )
    }
    else if (titlesNum > 20) settingsModel.setTitlesNum(20)

    val limitationDropdownVisible = remember { MutableTransitionState(false) }
    val limitationDropdownItems = listOf(
        pluralStringResource(R.plurals.hours, count = 12, 12) to { settingsModel.setTitlesPeriod(12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { settingsModel.setTitlesPeriod(24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { settingsModel.setTitlesPeriod(48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { settingsModel.setTitlesPeriod(72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { settingsModel.setTitlesPeriod(96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { settingsModel.setTitlesPeriod(120) }
    )

    val rssUpdateDropdownVisible = remember { MutableTransitionState(false) }
    val rssUpdateDropdownItems = listOf(
        pluralStringResource(R.plurals.minutes, count = 15, 15) to { settingsModel.setRssUpdateInterval(context, 15) },
        pluralStringResource(R.plurals.minutes, count = 30, 30) to { settingsModel.setRssUpdateInterval(context, 30) },
        pluralStringResource(R.plurals.minutes, count = 60, 60) to { settingsModel.setRssUpdateInterval(context, 60) }
    )

    val geminiModelDropdownVisible = remember { MutableTransitionState(false) }
    var geminiModelText by remember { mutableStateOf("") }
    geminiModelText = when (currentLlmModel) {
        "gemini-2.5-flash" -> "2.5 Flash"
        "gemini-2.0-flash" -> "2.0 Flash"
        "gemini-2.0-flash-lite" -> "2.0 Flash Lite"
        else -> "2.5 Flash Lite"
    }
    val geminiModelDropdownItems = listOf(
        "2.5 Flash" to { settingsModel.setCurrentLlm("gemini-2.5-flash") },
        "2.5 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.5-flash-lite") },
        "2.0 Flash" to { settingsModel.setCurrentLlm("gemini-2.0-flash") },
        "2.0 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.0-flash-lite") }
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState
    ) {
        stickyHeader() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextDivider(text = stringResource(R.string.settings_chapter_appearance))
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_compact_tab)) {
                Switch(
                    checked = compactTab,
                    onCheckedChange = { settingsModel.setCompactTab(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                        uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_endure_time)) {
                Switch(
                    checked = endureTime,
                    onCheckedChange = { settingsModel.setEndureTime(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                        uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_monet_colors)) {
                    Switch(
                        checked = monetColors,
                        onCheckedChange = { settingsModel.setMonetColors(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                            uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_color_scheme)) {
                Box {
                    Button(
                        modifier = Modifier
                            .wrapContentSize()
                            .width(150.dp),
                        onClick = {
                            colorSchemeDropdownVisible.targetState =
                                !colorSchemeDropdownVisible.currentState
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = when (currentTheme) {
                                "light" -> stringResource(R.string.settings_light_theme)
                                "dark" -> stringResource(R.string.settings_dark_theme)
                                else -> stringResource(R.string.settings_system_theme)
                            }, color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (colorSchemeDropdownVisible.currentState || colorSchemeDropdownVisible.targetState) {
                        Popup(
                            onDismissRequest = { colorSchemeDropdownVisible.targetState = false },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = colorSchemeDropdownVisible,
                                buttons = colorSchemeDropdownItems.map {(text, action) ->
                                    text to { action() }
                                }
                            )
                        }
                    }
                }
            }
        }

        stickyHeader() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextDivider(text = stringResource(R.string.settings_chapter_titles))
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_show_dates)) {
                Switch(
                    checked = showDates,
                    onCheckedChange = { settingsModel.setShowDates(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                        uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        uncheckedTrackColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_maximum_headers)) {
                Box {
                    Button(
                        modifier = Modifier
                            .wrapContentSize()
                            .width(150.dp),
                        onClick = {
                            titlesDropdownVisible.targetState = !titlesDropdownVisible.currentState
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.titles,
                                count = titlesNum,
                                titlesNum
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (titlesDropdownVisible.currentState || titlesDropdownVisible.targetState) {
                        Popup(
                            onDismissRequest = { titlesDropdownVisible.targetState = false },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = titlesDropdownVisible,
                                buttons = titlesDropdownItems.map {(text, action) ->
                                    text to { action() }
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_news_period)) {
                Box {
                    Button(
                        modifier = Modifier
                            .wrapContentSize()
                            .width(150.dp),
                        onClick = {
                            limitationDropdownVisible.targetState =
                                !limitationDropdownVisible.currentState
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.hours,
                                count = titlesPeriod,
                                titlesPeriod
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (limitationDropdownVisible.currentState || limitationDropdownVisible.targetState) {
                        Popup(
                            onDismissRequest = { limitationDropdownVisible.targetState = false },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = limitationDropdownVisible,
                                buttons = limitationDropdownItems.map {(text, action) ->
                                    text to { action() }
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_news_update_frequency)) {
                Box {
                    Button(
                        modifier = Modifier
                            .wrapContentSize()
                            .width(150.dp),
                        onClick = {
                            rssUpdateDropdownVisible.targetState =
                                !rssUpdateDropdownVisible.currentState
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.minutes,
                                count = rssUpdateInterval,
                                rssUpdateInterval
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (rssUpdateDropdownVisible.currentState || rssUpdateDropdownVisible.targetState) {
                        Popup(
                            onDismissRequest = { rssUpdateDropdownVisible.targetState = false },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = rssUpdateDropdownVisible,
                                buttons = rssUpdateDropdownItems
                            )
                        }
                    }
                }
            }
        }

        stickyHeader() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                CustomTextDivider(text = stringResource(R.string.settings_chapter_llm))
            }
        }
        if (geminiApiText != defaultGeminiApiKey) {
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_filter_topics)) {
                    Switch(
                        checked = filterTopics,
                        onCheckedChange = { settingsModel.setFilterTopics(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                            uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
        if (geminiApiText != defaultGeminiApiKey) {
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_gemini_model)) {
                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .width(150.dp),
                            onClick = {
                                geminiModelDropdownVisible.targetState =
                                    !geminiModelDropdownVisible.currentState
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = geminiModelText,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (geminiModelDropdownVisible.currentState || geminiModelDropdownVisible.targetState) {
                            Popup(
                                onDismissRequest = {
                                    geminiModelDropdownVisible.targetState = false
                                },
                                alignment = Alignment.TopEnd
                            ) {
                                CustomDropdown(
                                    transitionState = geminiModelDropdownVisible,
                                    buttons = geminiModelDropdownItems.map {(text, action) ->
                                        text to { action() }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            CustomSettingsItem(text = stringResource(R.string.settings_gemini_api_key)) {
                Box {
                    Button(
                        modifier = Modifier
                            .wrapContentSize()
                            .widthIn(min = 150.dp, max = 250.dp),
                        onClick = {
                            when (geminiApiText) {
                                defaultGeminiApiKey -> {
                                    val clipboardText: AnnotatedString? = clipboardManager.getText()

                                    clipboardText?.let {
                                        text += it.text
                                    }

                                    settingsModel.setUserGeminiApi(text)
                                    text = ""
                                }

                                else -> {
                                    settingsModel.setUserGeminiApi(defaultGeminiApiKey)
                                    settingsModel.setCurrentLlm("gemini-2.0-flash")
                                    settingsModel.setFilterTopics(false)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = if (geminiApiText != defaultGeminiApiKey) stringResource(R.string.settings_reset) else stringResource(
                                R.string.settings_paste
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {CustomBottomFootnote(stringResource(R.string.settings_footnote_text, stringResource(R.string.app_version)))}
    }
}