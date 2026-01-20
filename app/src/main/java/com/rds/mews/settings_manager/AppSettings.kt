package com.rds.mews.settings_manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import androidx.datastore.core.Serializer
import com.rds.mews.localcore.AppTheme
import com.rds.mews.localcore.AutoUpdateFrequency
import com.rds.mews.localcore.DarkTheme
import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.localcore.HeadersNum
import com.rds.mews.localcore.TitlesPeriod
import java.io.InputStream
import java.io.OutputStream

@Serializable
enum class SummarizationErrorType {
    EXTRACT_TOPICS_FAILED,
    SUMMARIZE_TOPICS_FAILED,
    CRITICAL_SUMMARIZATION_ERROR,
    JSON_PARSING_FAILED,
    NETWORK_TIMEOUT,
    EMPTY_ANSWER,
    NO_NEWS_TO_ANALYZE,
    FILTER_FAILED,
    JOB_CANCELLED,
    RATE_LIMIT_EXCEEDED,
    NO_NETWORK,
    UNPROCESSED_ITEMS,
    UNKNOWN_ERROR
}

@Serializable
data class SavedError(
    val type: SummarizationErrorType,
    val message: String
)

@Serializable
data class AppSettings(
    // Theme
    val darkTheme: DarkTheme = DarkTheme.SYSTEM, // Было String "current_theme"
    val appTheme: AppTheme = AppTheme.DEFAULT,   // Было Boolean "is_monet"

    // Content
    val titlesNum: HeadersNum = HeadersNum.NUM_10, // Было Int
    val titlesPeriod: TitlesPeriod = TitlesPeriod.ADAPTIVE, // Было Int

    // Network & AI
    val userApiKey: String = "",
    val llmModel: GeminiModelOption = GeminiModelOption.FLASH_LITE_LATEST, // Было String
    val enableProxy: Boolean = false,

    // Updates
    val titlesAutoUpdate: Boolean = false,
    val titlesAutoUpdateFrequency: AutoUpdateFrequency = AutoUpdateFrequency.FREQ_24, // Было Int
    val titlesAlarmTimeMins: Int = 540,
    val alarmsAllowed: Boolean = false,
    val notificationsGranted: Boolean = false,
    val rssUpdateInterval: Int = 30,
    val lastRssUpdate: Long = 0L,

    // UI Toggles
    val compactTabBar: Boolean = false,
    val filterTopics: Boolean = false,
    val innerTimestamps: Boolean = false,
    val showSnippets: Boolean = false,
    val showDates: Boolean = false,
    val currentLanguage: String? = null,

    // Technical State
    val updatingTitles: Boolean = false,
    val updatingState: String = "off",
    val updatingProgress: Float = 0f,
    val lastTitlesUpdate: Long = 0L,
    val bannedNews: Set<String> = emptySet(),
    val lastError: SavedError? = null
)

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            Json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
        } catch (exception: SerializationException) {
            exception.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(Json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}