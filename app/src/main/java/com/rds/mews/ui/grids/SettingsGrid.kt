package com.rds.mews.ui.grids

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.localcore.intTimeToStr
import com.rds.mews.localcore.requestNotificationPermission
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.SettingsItem
import com.rds.mews.ui.custom_elements.CustomSwitch
import com.rds.mews.ui.custom_elements.CustomTextDivider
import com.rds.mews.ui.custom_elements.DeferredUpdateTab
import com.rds.mews.ui.custom_elements.DropdownButton
import com.rds.mews.ui.theme.Shapes


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGrid(gridState: LazyGridState, modifier: Modifier, settingsModel: SettingsViewModel, mainActivity: MainActivity) {
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

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
    val titlesAlarmUpdate by settingsModel.titlesAlarmUpdate.collectAsStateWithLifecycle()
    val alarmMins by settingsModel.titlesAlarmMins.collectAsStateWithLifecycle()
    val alarmFrequency by settingsModel.titlesUpdateFrequency.collectAsStateWithLifecycle()
    val bannedNews by settingsModel.bannedNews.collectAsStateWithLifecycle()
    val proxyEnabled by settingsModel.proxyEnabled.collectAsStateWithLifecycle()
    var alarmHrsText by remember { mutableIntStateOf(alarmMins / 60) }
    var alarmMinsText by remember { mutableIntStateOf(alarmMins % 60) }
    var showAlarmsSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(alarmMins) {
        alarmHrsText = alarmMins / 60
        alarmMinsText = alarmMins % 60
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current


    val currentTheme by settingsModel.currentTheme.collectAsStateWithLifecycle()
    val colorSchemeDropdownItems = mutableListOf(
        stringResource(R.string.settings_system_theme) to { settingsModel.setCurrentTheme("system") },
        stringResource(R.string.settings_light_theme) to { settingsModel.setCurrentTheme("light") },
        stringResource(R.string.settings_dark_theme) to { settingsModel.setCurrentTheme("dark") }
    )

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

    val limitationDropdownItems = listOf(
        pluralStringResource(R.plurals.hours, count = 12, 12) to { settingsModel.setTitlesPeriod(12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { settingsModel.setTitlesPeriod(24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { settingsModel.setTitlesPeriod(48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { settingsModel.setTitlesPeriod(72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { settingsModel.setTitlesPeriod(96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { settingsModel.setTitlesPeriod(120) }
    )

    val rssUpdateDropdownItems = listOf(
        pluralStringResource(R.plurals.minutes, count = 15, 15) to { settingsModel.setRssUpdateInterval(context, 15) },
        pluralStringResource(R.plurals.minutes, count = 30, 30) to { settingsModel.setRssUpdateInterval(context, 30) },
        pluralStringResource(R.plurals.minutes, count = 60, 60) to { settingsModel.setRssUpdateInterval(context, 60) }
    )

    val geminiModelDropdownItems = listOf(
        "2.5 Flash" to { settingsModel.setCurrentLlm("gemini-2.5-flash") },
        "2.5 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.5-flash-lite") },
        "2.0 Flash" to { settingsModel.setCurrentLlm("gemini-2.0-flash") },
        "2.0 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.0-flash-lite") }
    )

    val alarmHrsItems = (0..23).toList().map { num ->
        intTimeToStr(num) to {
            alarmHrsText = num
            settingsModel.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }
    val alarmMinsItems = (0..59).toList().map {num ->
        intTimeToStr(num) to {
            alarmMinsText = num
            settingsModel.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }

    val titlesFrequencyItems = listOf (
        pluralStringResource(R.plurals.hours, count = 12, 12) to { settingsModel.setTitlesUpdFrequency(context, 12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { settingsModel.setTitlesUpdFrequency(context, 24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { settingsModel.setTitlesUpdFrequency(context, 48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { settingsModel.setTitlesUpdFrequency(context, 72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { settingsModel.setTitlesUpdFrequency(context, 96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { settingsModel.setTitlesUpdFrequency(context, 120) }
    )

    val autoUpdateScreenState = remember { MutableTransitionState(false) }
    val autoUpdateIndexes = remember { mutableStateOf<List<Int>?>(listOf(0)) }
    LaunchedEffect(titlesAlarmUpdate) {
        autoUpdateIndexes.value = if (titlesAlarmUpdate) null else listOf(0)
    }
    val autoUpdateItems: List<@Composable () -> Unit> = listOf(
        {
            Spacer(modifier = Modifier.height(12.dp))

            SettingsItem(text = stringResource(R.string.settings_titles_alarm_update)) {
                CustomSwitch(
                    checked = titlesAlarmUpdate,
                    onCheckedChange = {
                        settingsModel.setTitlesAlarmUpdate(
                            context = context,
                            activity = mainActivity,
                            onShowNotificationsSheet = { showNotificationsSheet = true },
                            onShowAlarmsSheet = { showAlarmsSheet = true },
                            value = it
                        )
                    }
                )
            }
        },
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_frequency)) {
                DropdownButton(
                    buttons = titlesFrequencyItems.map { (text, action) ->
                        text to { action() }
                    },
                    density = density,
                    cornerShape = Shapes.large,
                    initialSelectedIndex = titlesFrequencyItems.indexOfFirst { it.first.contains(alarmFrequency.toString()) }
                )
            }
        },
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_time)) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownButton(
                            buttons = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(alarmFrequency == 12 && text.toInt() > 12)) text to { action() } else null
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            width = 75.dp,
                            initialSelectedIndex = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(alarmFrequency == 12 && text.toInt() > 12)) text to { action() } else null
                            }.indexOfFirst { it.first.contains(intTimeToStr(alarmHrsText)) }
                        )
                        Text(
                            text = ":",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        DropdownButton(
                            buttons = alarmMinsItems.map { (text, action) ->
                                text to { action() }
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            width = 75.dp,
                            initialSelectedIndex = alarmHrsItems.indexOfFirst { it.first.contains(intTimeToStr(alarmMinsText)) }
                        )
                    }
                }
            }
        }
    )

    val bannedNewsScreenState = remember { MutableTransitionState(false) }
    val bannedNewsItems: MutableList<@Composable () -> Unit> = mutableListOf()
    bannedNews.forEach {
        if (it != ""){
            bannedNewsItems.add(
                {
                    SettingsItem(it) {
                        IconButton(
                            onClick = { settingsModel.delBannedNews(it) }
                        ) {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.ban_btn_desc)
                            )
                        }
                    }
                }
            )
        }
    }
    LaunchedEffect(bannedNewsItems) {
        if (bannedNewsItems.isEmpty()) bannedNewsScreenState.targetState = false
    }

    if (showAlarmsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_alarms_sheet_title),
            text = stringResource(R.string.settings_alarms_sheet_text),
            confBtnText = stringResource(R.string.settings_alarms_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_alarms_sheet_cancel),
            onDismissRequest = { showAlarmsSheet = false },
            onConfirm = {
                showAlarmsSheet = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { intent ->
                        mainActivity.startActivity(intent)
                    }
                }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    if (showNotificationsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_notification_sheet_title),
            text = stringResource(R.string.settings_notification_sheet_text),
            confBtnText = stringResource(R.string.settings_notification_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_notification_sheet_cancel),
            onDismissRequest = { showNotificationsSheet = false },
            onConfirm = {
                showAlarmsSheet = false
                requestNotificationPermission(mainActivity)
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                SettingsItem(text = stringResource(R.string.settings_compact_tab)) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_monet_colors)) {
                        CustomSwitch(
                            checked = monetColors,
                            onCheckedChange = { settingsModel.setMonetColors(it) }
                        )
                    }
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_color_scheme)) {
                    DropdownButton(
                        buttons = colorSchemeDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = when (currentTheme) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                    )
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
                SettingsItem(text = stringResource(R.string.settings_show_dates)) {
                    CustomSwitch(
                        checked = showDates,
                        onCheckedChange = { settingsModel.setShowDates(it) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_endure_time)) {
                    CustomSwitch(
                        checked = endureTime,
                        onCheckedChange = { settingsModel.setEndureTime(it) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_maximum_headers)) {
                    DropdownButton(
                        buttons = titlesDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = titlesDropdownItems.indexOfFirst { it.first.contains(titlesNum.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_news_period)) {
                    DropdownButton(
                        buttons = limitationDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = limitationDropdownItems.indexOfFirst { it.first.contains(titlesPeriod.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_news_update_frequency)) {
                    DropdownButton(
                        buttons = rssUpdateDropdownItems,
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = rssUpdateDropdownItems.indexOfFirst { it.first.contains(rssUpdateInterval.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_titles_auto_update)) {
                    IconButton(
                        modifier = Modifier
                            .wrapContentSize(),
                        onClick = {
                            autoUpdateScreenState.targetState = !autoUpdateScreenState.currentState
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc)
                        )
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
                    SettingsItem(text = stringResource(R.string.settings_filter_topics)) {
                        CustomSwitch(
                            checked = filterTopics,
                            onCheckedChange = { settingsModel.setFilterTopics(it) }
                        )
                    }
                }
            }
            if (geminiApiText != defaultGeminiApiKey) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_gemini_model)) {
                        DropdownButton(
                            buttons = geminiModelDropdownItems.map { (text, action) ->
                                text to { action() }
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            initialSelectedIndex = geminiModelDropdownItems.indexOfFirst {
                                "gemini-${
                                    it.first.split(" ")
                                        .joinToString("-") { item -> item.lowercase() }
                                }" == currentLlmModel
                            }
                        )
                    }
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_gemini_api_key)) {
                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .widthIn(min = 150.dp, max = 250.dp),
                            onClick = {
                                when (geminiApiText) {
                                    defaultGeminiApiKey -> {
                                        val clipboardText: AnnotatedString? =
                                            clipboardManager.getText()

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
                            shape = Shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=0.98f)
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
            if (bannedNewsItems.isNotEmpty()) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_banned_news)) {
                        IconButton(
                            modifier = Modifier
                                .wrapContentSize(),
                            onClick = {
                                bannedNewsScreenState.targetState =
                                    !bannedNewsScreenState.currentState
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc)
                            )
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
                    CustomTextDivider(text = stringResource(R.string.settings_chapter_additional))
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_enable_proxy)) {
                    CustomSwitch(
                        checked = proxyEnabled,
                        onCheckedChange = { settingsModel.setProxyEnabled(it) }
                    )
                }
            }

            item {
                CustomBottomFootnote(
                    stringResource(
                        R.string.settings_footnote_text,
                        stringResource(R.string.app_version)
                    )
                )
            }
        }

        DeferredUpdateTab(
            transitionState = autoUpdateScreenState,
            onDismissRequest = {},
            items = autoUpdateItems,
            indexes = autoUpdateIndexes.value,
            header = stringResource(R.string.settings_titles_auto_update)
        )

        DeferredUpdateTab(
            transitionState = bannedNewsScreenState,
            onDismissRequest = {},
            items = bannedNewsItems,
            header = stringResource(R.string.settings_banned_news)
        )
    }
}