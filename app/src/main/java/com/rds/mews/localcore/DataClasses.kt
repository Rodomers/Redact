package com.rds.mews.localcore

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import com.rds.mews.MainActivity
import com.rds.mews.settings_manager.SummarizationErrorType

data class Message(
    var id: Long,
    var time: Long,
    var link: String,
    var source: String,
    var originalText: String,
    var cleanText: String
)
data class RSS(var id: Long, var source: String, var link: String)
data class Title(
    var id: Long,
    var title: String,
    var summary: String,
    var eventTime: Long,
    var updateTime: Long? = null,
    val status: Int? = null,
    val isRead: Boolean = false,
    val isPinned: Boolean = false,
    var sources: String,
    var ids: String
)

data class TitleCardStates(
    var id: Long,
    var expanded: Boolean = false,
    var currentPage: Int = 0,
    var sources: List<SourceMessages>? = null,
    val read: Boolean = false
)

enum class SourceType {
    RSS_FEED,
    TELEGRAM_CHANNEL
}

enum class ArrowPosition {
    TopLeft, TopCenter, TopRight,
    BottomLeft, BottomCenter, BottomRight,
    LeftTop, LeftCenter, LeftBottom,
    RightTop, RightCenter, RightBottom,
    None
}

enum class ScreenQuadrant {
    TopLeft, TopRight, BottomLeft, BottomRight
}

sealed interface SummarizationResult {
    data object Success : SummarizationResult
    data class Failure(
        val type: SummarizationErrorType,
        val cause: Throwable? = null
    ) : SummarizationResult
}

data class SettingsUiState(
    val autoUpdateScreenOpened: Boolean,
    val bannedNewsScreenOpened: Boolean,
    val geminiKeyScreenOpened: Boolean,
    val showDates: Boolean,
    val expandSources: Boolean,
    val compactTab: Boolean,
    val darkTheme: DarkTheme,
    val appTheme: AppTheme,
    val filterTopics: Boolean,
    val titlesSorting: TitleSorting,
    val headersNum: HeadersNum,
    val geminiApiText: String,
    val currentLlmModel: GeminiModelOption,
    val titlesPeriod: TitlesPeriod,
    val titlesKeeping: TitlesKeeping,
    val rssUpdateInterval: Int,
    val innerTime: Boolean,
    val showSnippets: Boolean,
    val titlesAlarmUpdate: Boolean,
    val alarmMins: Int,
    val alarmFrequency: AutoUpdateFrequency,
    val bannedNews: Set<String>,
    val proxyEnabled: Boolean,
    val showAlarmsSheet: Boolean,
    val showNotificationsSheet: Boolean,
    val defaultApiCheck: Boolean,
    val defaultGeminiModel: GeminiModelOption,
    val geminiApiBuffer: String,
    val isApiKeyCorrect: Boolean,
    val geminiModels: List<GeminiModelOption>,
    val darkThemes: List<DarkTheme>,
    val appThemes: List<AppTheme>,
    val titleSortingList: List<TitleSorting>,
    val headersNumList: List<HeadersNum>,
    val titlesPeriods: List<TitlesPeriod>,
    val titlesKeepings: List<TitlesKeeping>,
    val autoUpdateFrequencies: List<AutoUpdateFrequency>,
)

data class SettingsUiFunctions(
    val setAutoupdateScreen: (Boolean) -> Unit,
    val setBannedNewsScreen: (Boolean) -> Unit,
    val setGeminiScreenOpened: (Boolean) -> Unit,
    val setCompactTab: (Boolean) -> Unit,
    val setAppTheme: (AppTheme) -> Unit,
    val setDarkTheme: (DarkTheme) -> Unit,
    val setShowDates: (Boolean) -> Unit,
    val setExpandSources: (Boolean) -> Unit,
    val setInnerTime: (Boolean) -> Unit,
    val setShowSnippets: (Boolean) -> Unit,
    val setTitlesSorting: (TitleSorting) -> Unit,
    val setTitlesNum: (HeadersNum) -> Unit,
    val setTitlesPeriod: (TitlesPeriod) -> Unit,
    val setTitlesKeeping: (TitlesKeeping) -> Unit,
    val setRssUpdateInterval: (Context, Int) -> Unit,
    val setFilterTopics: (Boolean) -> Unit,
    val setBannedNews: (Set<String>) -> Unit,
    val delBannedNews: (String) -> Unit,
    val setCurrentLlm: (GeminiModelOption) -> Unit,
    val setUserGeminiApi: (String) -> Unit,
    val resetUserGeminiApi: () -> Unit,
    val setProxyEnabled: (Boolean) -> Unit,
    val setTitlesAlarmUpdate: (Context, () -> Unit, () -> Unit, Boolean, MainActivity) -> Unit,
    val setTitlesAlarmMins: (Context, Int) -> Unit,
    val setTitlesUpdFrequency: (Context, AutoUpdateFrequency) -> Unit,
    val setAlarmsAllowed: (Boolean) -> Unit,
    val planTitlesAutoUpdate: (Context) -> Unit,
    val setShowAlarmsSheet: (Boolean) -> Unit,
    val setShowNotificationsSheet: (Boolean) -> Unit,
    val addGroupState: (Int, Boolean) -> Unit,
    val changeGroupState: (Int) -> Unit,
    val setGeminiBuffer: (String) -> Unit,
    val clearFeed: () -> Unit
)

data class SourcesGroupState(val group: SourceType, val expanded: Boolean)
data class TitlesGroupState(val group: TimeDate, val expanded: Boolean)
data class SettingsGroupState(val group: Int, val expanded: Boolean)

data class SourceMessages(val source: String, val state: Boolean, val messages: List<Message>)
data class TimeDate(val number: Int? = null, val date: Int, val time: String)

data class TextButtonInputs(
    val text: String,
    override val action: () -> Unit,
    override val toast: String? = null
) : ButtonInputs
data class IconButtonInputs(
    val icon: ImageVector,
    override val action: () -> Unit,
    override val toast: String? = null
) : ButtonInputs