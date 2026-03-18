package com.rds.mews.ui.grids

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.localcore.*
import com.rds.mews.ui.custom_elements.ApiKeyBottomSheet
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.SettingsItem
import com.rds.mews.ui.custom_elements.CustomSwitch
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.custom_elements.DropdownButton
import com.rds.mews.ui.custom_elements.SettingsListBottomSheet
import com.rds.mews.ui.custom_elements.customHeader
import com.rds.mews.ui.theme.Shapes
import com.rds.mews.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    gridState: LazyGridState,
    modifier: Modifier,
    viewModel: SettingsViewModel,
    mainActivity: MainActivity,
    bottomSpacer: Dp
) {
    val density = LocalDensity.current

    val autoupdateScreenOpened by viewModel.autoUpdateScreenOpened.collectAsStateWithLifecycle()
    val bannedNewsScreenOpened by viewModel.bannedNewsScreenOpened.collectAsStateWithLifecycle()
    val geminiScreenOpened by viewModel.geminiScreenOpened.collectAsStateWithLifecycle()

    val geminiModels = viewModel.geminiModels
    val defaultGeminiModel = viewModel.defaultGeminiModel

    val geminiBuffer by viewModel.geminiKeyBuffer.collectAsStateWithLifecycle()
    val isApiKeyValid by viewModel.isApiKeyCorrect.collectAsStateWithLifecycle()

    val groupStates by viewModel.groupStates.collectAsStateWithLifecycle()

    val showDates by viewModel.showDates.collectAsStateWithLifecycle()
    val compactTab by viewModel.compactTabBar.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val filterTopics by viewModel.filterTopics.collectAsStateWithLifecycle()
    val titlesSorting by viewModel.titlesSorting.collectAsStateWithLifecycle()
    val titlesNum by viewModel.titlesNum.collectAsStateWithLifecycle()
    val geminiApiText by viewModel.userApi.collectAsStateWithLifecycle()
    val currentLlmModel by viewModel.currentLlm.collectAsStateWithLifecycle()
    val titlesPeriod by viewModel.titlesPeriod.collectAsStateWithLifecycle()
    val titlesKeeping by viewModel.titlesKeeping.collectAsStateWithLifecycle()
    val rssUpdateInterval by viewModel.rssUpdateInterval.collectAsStateWithLifecycle()
    val innerTime by viewModel.innerTime.collectAsStateWithLifecycle()
    val showSnippets by viewModel.showSnippets.collectAsStateWithLifecycle()
    val titlesAlarmUpdate by viewModel.titlesAlarmUpdate.collectAsStateWithLifecycle()
    val alarmMins by viewModel.titlesAlarmMins.collectAsStateWithLifecycle()
    val alarmFrequency by viewModel.titlesUpdateFrequency.collectAsStateWithLifecycle()
    val bannedNews by viewModel.bannedNews.collectAsStateWithLifecycle()
    val proxyEnabled by viewModel.proxyEnabled.collectAsStateWithLifecycle()
    val showAlarmsSheet by viewModel.showAlarmsSheet.collectAsStateWithLifecycle()
    val showNotificationsSheet by viewModel.showNotificationSheet.collectAsStateWithLifecycle()
    val isApiKeyDefault by viewModel.isKeyDefault.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val expandSources by viewModel.expandSources.collectAsStateWithLifecycle()

    val state = SettingsUiState(
        autoUpdateScreenOpened = autoupdateScreenOpened,
        bannedNewsScreenOpened = bannedNewsScreenOpened,
        geminiKeyScreenOpened = geminiScreenOpened,
        showDates = showDates,
        expandSources = expandSources,
        compactTab = compactTab,
        darkTheme = darkTheme,
        appTheme = appTheme,
        filterTopics = filterTopics,
        titlesSorting = titlesSorting,
        headersNum = titlesNum,
        geminiApiText = geminiApiText,
        currentLlmModel = currentLlmModel,
        titlesPeriod = titlesPeriod,
        titlesKeeping = titlesKeeping,
        rssUpdateInterval = rssUpdateInterval,
        innerTime = innerTime,
        showSnippets = showSnippets,
        titlesAlarmUpdate = titlesAlarmUpdate,
        alarmMins = alarmMins,
        alarmFrequency = alarmFrequency,
        bannedNews = bannedNews,
        proxyEnabled = proxyEnabled,
        showAlarmsSheet = showAlarmsSheet,
        showNotificationsSheet = showNotificationsSheet,
        defaultApiCheck = isApiKeyDefault,
        geminiModels = geminiModels,
        defaultGeminiModel = defaultGeminiModel,
        geminiApiBuffer = geminiBuffer,
        isApiKeyCorrect = isApiKeyValid,
        darkThemes = viewModel.darkThemes,
        appThemes = viewModel.appThemes,
        titleSortingList = viewModel.titleSortingList,
        headersNumList = viewModel.headersNumList,
        titlesPeriods = viewModel.titlesPeriods,
        titlesKeepings = viewModel.titlesKeepings,
        autoUpdateFrequencies = viewModel.autoUpdateFrequencies,
    )

    val functions = remember {
        SettingsUiFunctions(
            setAutoupdateScreen = viewModel::setAutoupdateScreen,
            setBannedNewsScreen = viewModel::setBannedNewsScreen,
            setGeminiScreenOpened = viewModel::setGeminiScreen,
            setCompactTab = viewModel::setCompactTab,
            setAppTheme = viewModel::setAppTheme,
            setDarkTheme = viewModel::setDarkTheme,
            setShowDates = viewModel::setShowDates,
            setExpandSources = viewModel::setExpandSources,
            setInnerTime = viewModel::setInnerTime,
            setShowSnippets = viewModel::setShowSnippets,
            setTitlesSorting = viewModel::setTitlesSorting,
            setTitlesNum = viewModel::setTitlesNum,
            setTitlesKeeping = viewModel::setTitlesKeeping,
            setTitlesPeriod = viewModel::setTitlesPeriod,
            setRssUpdateInterval = viewModel::setRssUpdateInterval,
            setFilterTopics = viewModel::setFilterTopics,
            setBannedNews = viewModel::setBannedNews,
            delBannedNews = viewModel::delBannedNews,
            setCurrentLlm = viewModel::setCurrentLlm,
            setUserGeminiApi = viewModel::setUserGeminiApi,
            resetUserGeminiApi = viewModel::resetApiKey,
            setProxyEnabled = viewModel::setProxyEnabled,
            setTitlesAlarmUpdate = viewModel::setTitlesAlarmUpdate,
            setTitlesAlarmMins = viewModel::setTitlesAlarmMins,
            setTitlesUpdFrequency = viewModel::setTitlesUpdFrequency,
            setAlarmsAllowed = viewModel::setAlarmsAllowed,
            planTitlesAutoUpdate = viewModel::planTitlesAutoUpdate,
            setShowAlarmsSheet = viewModel::setShowAlarmsSheet,
            setShowNotificationsSheet = viewModel::setShowNotificationsSheet,
            addGroupState = viewModel::addGroupState,
            changeGroupState = viewModel::changeGroupState,
            setGeminiBuffer = viewModel::setGeminiKeyBuffer,
            clearFeed = viewModel::clearFeed
        )
    }

    SettingsGrid(
        gridState = gridState,
        groupStates = groupStates,
        modifier = modifier,
        mainActivity = mainActivity,
        density = density,
        state = state,
        functions = functions,
        bottomSpacer = bottomSpacer
    )
}

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGrid(
    gridState: LazyGridState,
    groupStates: List<SettingsGroupState>,
    modifier: Modifier,
    mainActivity: MainActivity,
    density: Density,
    state: SettingsUiState,
    functions: SettingsUiFunctions,
    bottomSpacer: Dp
) {
    val verticalArrangement by remember { mutableStateOf(8.dp) }
    val appearanceChapterId by remember { mutableIntStateOf(R.string.settings_chapter_appearance) }
    val titlesChapterId by remember { mutableIntStateOf(R.string.settings_chapter_titles) }
    val llmChapterId by remember { mutableIntStateOf(R.string.settings_chapter_llm) }
    val additionalChapterId by remember { mutableIntStateOf(R.string.settings_chapter_additional) }

    val autoUpdateBtnState = remember { MutableTransitionState(state.autoUpdateScreenOpened) }
    val autoUpdateBtnText = stringResource(R.string.setup)
    val autoUpdateButtonText = remember { mutableStateOf(autoUpdateBtnText) }
    val autoUpdateButtonInputs = remember {
        TextButtonInputs(
            autoUpdateButtonText.value,
            action = {
                functions.setAutoupdateScreen(true)
                autoUpdateBtnState.targetState = true
            }
        )
    }

    val filterBtnState = remember { MutableTransitionState(state.bannedNewsScreenOpened) }
    val filterBtnText = stringResource(R.string.setup)
    val filterButtonText = remember { mutableStateOf(filterBtnText) }
    val filterButtonInputs = remember {
        TextButtonInputs(
            filterButtonText.value,
            action = {
                functions.setBannedNewsScreen(true)
                filterBtnState.targetState = true
            }
        )
    }

    val apiBtnState = remember { MutableTransitionState(state.geminiKeyScreenOpened) }
    val apiBtnText = stringResource(R.string.change)
    val apiButtonText = remember { mutableStateOf(apiBtnText) }
    val apiButtonInputs = remember {
        TextButtonInputs(
            apiButtonText.value,
            action = {
                functions.setGeminiScreenOpened(true)
                apiBtnState.targetState = true
            }
        )
    }

    var alarmHrsText by remember { mutableIntStateOf(state.alarmMins / 60) }
    var alarmMinsText by remember { mutableIntStateOf(state.alarmMins % 60) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val screensState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        functions.addGroupState(appearanceChapterId, true)
        functions.addGroupState(titlesChapterId, true)
        functions.addGroupState(llmChapterId, true)
        functions.addGroupState(additionalChapterId, true)
    }

    LaunchedEffect(state.alarmMins) {
        alarmHrsText = state.alarmMins / 60
        alarmMinsText = state.alarmMins % 60
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val themesDropdownItems = state.appThemes
        .filter { theme ->
            !(theme == AppTheme.MATERIAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        }
        .map { theme ->
            stringResource(theme.themeName) to { functions.setAppTheme(theme) }
    }

    val colorSchemeDropdownItems = state.darkThemes.map { theme ->
        stringResource(theme.displayedName) to { functions.setDarkTheme(theme) }
    }

    val sortingDropdownItems = state.titleSortingList.map { sorting ->
        stringResource(sorting.stringId) to { functions.setTitlesSorting(sorting) }
    }

    var titlesDropdownItems = state.headersNumList.map { num ->
        pluralStringResource(num.stringId, num.num, num.num) to { functions.setTitlesNum(num) }
    }
    if (state.defaultApiCheck) {
        titlesDropdownItems = titlesDropdownItems.subList(0, 4)
        if (state.headersNum.num > 20) functions.setTitlesNum(HeadersNum.NUM_20)
    }

    val limitationDropdownItems = state.titlesPeriods.map { period ->
        val text = when (period) {
            TitlesPeriod.ADAPTIVE -> stringResource(R.string.adaptive)
            else -> pluralStringResource(period.stringId, period.num ?: 0, period.num ?: 0)
        }
        text to { functions.setTitlesPeriod(period) }
    }
    val keepingsDropdownItems = state.titlesKeepings.map { keeping ->
        val text = pluralStringResource(keeping.stringId, count = keeping.num, keeping.num)
        text to { functions.setTitlesKeeping(keeping) }
    }

    val modelListItemsBuffer = mutableListOf<Pair<String, () -> Unit>>()
    state.geminiModels.forEach {
        modelListItemsBuffer += it.displayedName to { functions.setCurrentLlm(it) }
    }
    val geminiModelDropdownItems by remember { mutableStateOf(modelListItemsBuffer) }

    val alarmHrsItems = (0..23).toList().map { num ->
        intTimeToStr(num) to {
            alarmHrsText = num
            functions.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }
    val alarmMinsItems = (0..59).toList().map {num ->
        intTimeToStr(num) to {
            alarmMinsText = num
            functions.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }

    val titlesFrequencyItems = state.autoUpdateFrequencies.map { freq ->
        pluralStringResource(freq.stringId, freq.num, freq.num) to { functions.setTitlesUpdFrequency(context, freq) }
    }

    val autoUpdateItems: List<@Composable () -> Unit> = listOf(
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_time), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownButton(
                            buttons = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(state.alarmFrequency.num == 12 && text.toInt() > 12)) text to { action() } else null
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            width = 75.dp,
                            initialSelectedIndex = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(state.alarmFrequency.num == 12 && text.toInt() > 12)) text to { action() } else null
                            }.indexOfFirst { it.first.contains(alarmHrsText.toString()) }
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
                            initialSelectedIndex = alarmMinsItems.indexOfFirst { it.first.contains(alarmMinsText.toString()) }
                        )
                    }
                }
            }
        },
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_frequency), modifier = Modifier.padding(vertical = 8.dp)) {
                DropdownButton(
                    buttons = titlesFrequencyItems,
                    density = density,
                    cornerShape = Shapes.large,
                    initialSelectedIndex = state.autoUpdateFrequencies.indexOfFirst(state.alarmFrequency::equals),
                    width = 160.dp
                )
            }
        },
        {
            SettingsItem(
                text = stringResource(R.string.settings_titles_alarm_update),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            ) {
                CustomSwitch(
                    checked = state.titlesAlarmUpdate,
                    onCheckedChange = {
                        functions.setTitlesAlarmUpdate(
                            context,
                            { functions.setShowNotificationsSheet(true) },
                            { functions.setShowAlarmsSheet(true) },
                            it,
                            mainActivity
                        )
                    }
                )
            }
        }
    )

    if (state.autoUpdateScreenOpened) {
        val itemsToShow = if (state.titlesAlarmUpdate) {
            autoUpdateItems
        } else {
            autoUpdateItems.subList(2, autoUpdateItems.size)
        }

        SettingsListBottomSheet(
            title = stringResource(R.string.settings_titles_auto_update),
            items = itemsToShow,
            onDismissRequest = {
                functions.setAutoupdateScreen(false)
                autoUpdateBtnState.targetState = false
            },
            sheetState = screensState,
            scope = screenScope
        )
    }

    LaunchedEffect(state.bannedNews) {
        if (state.bannedNews.none { it.isNotBlank() } && state.bannedNewsScreenOpened) {
            screenScope.launch { screensState.hide() }
                .invokeOnCompletion {
                    if (!screensState.isVisible) {
                        functions.setBannedNewsScreen(false)
                    }
                }
        }
    }

    if (state.bannedNewsScreenOpened) {
        val bannedItems = remember(state.bannedNews) {
            val list = mutableListOf<@Composable () -> Unit>()
            state.bannedNews.forEach { link ->
                if (link.isNotBlank()) {
                    list.add {
                        SettingsItem(
                            text = link,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CustomIconButton(
                                inputs = IconButtonInputs(
                                    icon = Icons.Default.Close,
                                    action = { functions.delBannedNews(link) }
                                ),
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = Shapes.large
                                    )
                            )
                        }
                    }
                }
            }
            list
        }

        SettingsListBottomSheet(
            title = stringResource(R.string.settings_banned_news),
            items = bannedItems,
            onDismissRequest = {
                functions.setBannedNewsScreen(false)
                filterBtnState.targetState = false
            },
            sheetState = screensState,
            scope = screenScope
        )
    }

    if (state.geminiKeyScreenOpened) {
        ApiKeyBottomSheet(
            apiKeyValue = state.geminiApiBuffer,
            onApiKeyChange = functions.setGeminiBuffer,
            confirmBtnInputs = TextButtonInputs(
                text = stringResource(R.string.settings_sheet_api_save),
                action = {
                    functions.setUserGeminiApi(state.geminiApiBuffer)

                    apiBtnState.targetState = false

                    screenScope.launch {
                        screensState.hide()
                    }.invokeOnCompletion {
                        if (!screensState.isVisible) {
                            functions.setGeminiScreenOpened(false)
                        }
                        functions.setGeminiBuffer("")
                    }
                }
            ),
            cancelBtnInputs = TextButtonInputs(
                text = stringResource(R.string.settings_sheet_api_cancel),
                action = {
                    apiBtnState.targetState = false

                    screenScope.launch {
                        screensState.hide()
                    }.invokeOnCompletion {
                        if (!screensState.isVisible) {
                            functions.setGeminiScreenOpened(false)
                        }
                        functions.setGeminiBuffer("")
                    }
                }
            ),
            resetBtnInputs = TextButtonInputs(
                text = stringResource(R.string.settings_reset),
                action = {
                    functions.resetUserGeminiApi()
                    functions.setGeminiBuffer("")

                    apiBtnState.targetState = false

                    screenScope.launch {
                        screensState.hide()
                    }.invokeOnCompletion {
                        if (!screensState.isVisible) {
                            functions.setGeminiScreenOpened(false)
                        }
                    }
                }
            ),
            sheetState = screensState,
            scope = screenScope,
            isApiKeyCorrect = state.isApiKeyCorrect
        )
    }

    if (state.showAlarmsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_alarms_sheet_title),
            text = stringResource(R.string.settings_alarms_sheet_text),
            confBtnText = stringResource(R.string.settings_alarms_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_alarms_sheet_cancel),
            onDismissRequest = { functions.setShowAlarmsSheet(false) },
            onConfirm = {
                functions.setShowAlarmsSheet(false)
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

    if (state.showNotificationsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_notification_sheet_title),
            text = stringResource(R.string.settings_notification_sheet_text),
            confBtnText = stringResource(R.string.settings_notification_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_notification_sheet_cancel),
            onDismissRequest = { functions.setShowNotificationsSheet(false) },
            onConfirm = {
                functions.setShowNotificationsSheet(false)
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
            state = gridState
        ) {
            customHeader(
                textId = appearanceChapterId,
                isExpanded = groupStates.find { it.group == appearanceChapterId }?.expanded ?: true,
                onHeaderClick = { functions.changeGroupState(appearanceChapterId) }
            )
            item {
                ExpandableContainer(
                    visible = groupStates.find { it.group == appearanceChapterId }?.expanded ?: true
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsItem(
                            text = stringResource(R.string.settings_compact_tab),
                            modifier = Modifier.padding(vertical = verticalArrangement),
                        ) {
                            CustomSwitch(
                                checked = state.compactTab,
                                onCheckedChange = { functions.setCompactTab(it) }
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_show_snippets),
                            modifier = Modifier.padding(vertical = verticalArrangement),
                        ) {
                            CustomSwitch(
                                checked = state.showSnippets,
                                onCheckedChange = { functions.setShowSnippets(it) }
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_expand_sources),
                            modifier = Modifier.padding(vertical = verticalArrangement),
                        ) {
                            CustomSwitch(
                                checked = state.expandSources,
                                onCheckedChange = { functions.setExpandSources(it) }
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_endure_time),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            CustomSwitch(
                                checked = state.innerTime,
                                onCheckedChange = { functions.setInnerTime(it) }
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_app_themes),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = themesDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = themesDropdownItems.indexOfFirst{ it.first == stringResource(state.appTheme.themeName) }
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_color_scheme),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = colorSchemeDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = state.darkThemes.indexOfFirst(state.darkTheme::equals)
                            )
                        }
                    }
                }
            }

            customHeader(
                textId = titlesChapterId,
                isExpanded = groupStates.find { it.group == titlesChapterId }?.expanded ?: true,
                onHeaderClick = { functions.changeGroupState(titlesChapterId) }
            )
            item {
                ExpandableContainer(
                    visible = groupStates.find { it.group == titlesChapterId }?.expanded ?: true
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsItem(
                            text = stringResource(R.string.settings_titles_sorting),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = sortingDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = state.titleSortingList.indexOfFirst(state.titlesSorting::equals)
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_maximum_headers),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = titlesDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = state.headersNumList.indexOfFirst(state.headersNum::equals)
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_news_period),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = limitationDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = state.titlesPeriods.indexOfFirst(state.titlesPeriod::equals)
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_news_keeping),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            DropdownButton(
                                buttons = keepingsDropdownItems,
                                density = density,
                                cornerShape = Shapes.large,
                                initialSelectedIndex = state.titlesKeepings.indexOfFirst(state.titlesKeeping::equals)
                            )
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_titles_auto_update),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            CustomTextButton(
                                inputs = autoUpdateButtonInputs,
                                modifier = Modifier
                                    .wrapContentSize()
                                    .widthIn(min = 150.dp, max = 250.dp),
                                shape = Shapes.large,
                                defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                    alpha = 0.98f
                                ),
                                transitionBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                transitionState = autoUpdateBtnState
                            )
                        }
                    }
                }
            }

            customHeader(
                textId = llmChapterId,
                isExpanded = groupStates.find { it.group == llmChapterId }?.expanded ?: true,
                onHeaderClick = { functions.changeGroupState(llmChapterId) }
            )
            item {
                ExpandableContainer(
                    visible = groupStates.find { it.group == llmChapterId }?.expanded ?: true
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!state.defaultApiCheck) {
                            SettingsItem(
                                text = stringResource(R.string.settings_filter_topics),
                                modifier = Modifier.padding(vertical = verticalArrangement)
                            ) {
                                CustomSwitch(
                                    checked = state.filterTopics,
                                    onCheckedChange = { functions.setFilterTopics(it) }
                                )
                            }
                            SettingsItem(
                                text = stringResource(R.string.settings_gemini_model),
                                modifier = Modifier.padding(vertical = verticalArrangement)
                            ) {
                                DropdownButton(
                                    buttons = geminiModelDropdownItems.map { (text, action) ->
                                        text to { action() }
                                    },
                                    density = density,
                                    cornerShape = Shapes.large,
                                    initialSelectedIndex = geminiModelDropdownItems.indexOfFirst { item ->
                                        state.geminiModels.find { it.displayedName == item.first } == state.currentLlmModel
                                    }.coerceAtLeast(0)
                                )
                            }
                        }
                        SettingsItem(
                            text = stringResource(R.string.settings_gemini_api_key),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            CustomTextButton(
                                inputs = apiButtonInputs,
                                modifier = Modifier
                                    .wrapContentSize()
                                    .widthIn(min = 150.dp, max = 250.dp),
                                shape = Shapes.large,
                                defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                    alpha = 0.98f
                                ),
                                transitionBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                transitionState = apiBtnState
                            )
                        }
                        if (state.bannedNews.any { it.isNotBlank() }) {
                            SettingsItem(
                                text = stringResource(R.string.settings_banned_news),
                                modifier = Modifier.padding(vertical = verticalArrangement)
                            ) {
                                CustomTextButton(
                                    inputs = filterButtonInputs,
                                    modifier = Modifier
                                        .wrapContentSize()
                                        .widthIn(min = 150.dp, max = 250.dp),
                                    shape = Shapes.large,
                                    defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                        alpha = 0.98f
                                    ),
                                    transitionBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    transitionState = filterBtnState
                                )
                            }
                        }
                    }
                }
            }

            customHeader(
                textId = additionalChapterId,
                isExpanded = groupStates.find { it.group == additionalChapterId }?.expanded ?: true,
                onHeaderClick = { functions.changeGroupState(additionalChapterId) }
            )
            item {
                ExpandableContainer(
                    visible = groupStates.find { it.group == additionalChapterId }?.expanded ?: true
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
//                        val clearBtnInputs = TextButtonInputs(
//                            text = stringResource(R.string.settings_db_clear_btn),
//                            action = { functions.clearFeed() },
//                            toast = stringResource(R.string.settings_db_cleared)
//                        )

                        SettingsItem(
                            text = stringResource(R.string.settings_enable_proxy),
                            modifier = Modifier.padding(vertical = verticalArrangement)
                        ) {
                            CustomSwitch(
                                checked = state.proxyEnabled,
                                onCheckedChange = { functions.setProxyEnabled(it) }
                            )
                        }
//                        SettingsItem(
//                            text = stringResource(R.string.settings_db_clear),
//                            modifier = Modifier.padding(vertical = verticalArrangement)
//                        ) {
//                            CustomTextButton(
//                                inputs = clearBtnInputs,
//                                modifier = Modifier
//                                    .wrapContentSize()
//                                    .widthIn(min = 150.dp, max = 250.dp),
//                                shape = Shapes.large,
//                                defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
//                                    alpha = 0.98f
//                                )
//                            )
//                        }
                    }
                }
            }

            item {
                CustomBottomFootnote(
                    stringResource(
                        R.string.settings_footnote_text,
                        stringResource(R.string.app_version)
                    ),
                    modifier = Modifier.padding(vertical = verticalArrangement)
                )
            }

            item {
                Spacer(modifier = Modifier.height(bottomSpacer))
            }
        }
    }
}