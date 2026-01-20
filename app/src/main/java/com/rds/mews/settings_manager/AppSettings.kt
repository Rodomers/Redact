package com.rds.mews.settings_manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

// 1. Помечаем Enum как сериализуемый
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

// 2. Вложенный класс для ошибки теперь хранит ТИП, а не строку
@Serializable
data class SavedError(
    val type: SummarizationErrorType, // Прямая типизация
    val message: String
)

@Serializable
data class AppSettings(
    // ... остальные поля без изменений (currentTheme, isMonet и т.д.) ...
    val currentTheme: String = "system",
    val isMonet: Boolean = false,
    val compactTabBar: Boolean = false,
    val showDates: Boolean = false,
    val innerTimestamps: Boolean = false,
    val showSnippets: Boolean = false,
    val currentLanguage: String? = null,
    val titlesNum: Int = 10,
    val titlesPeriod: Int = 24,
    val filterTopics: Boolean = false,
    val bannedNews: Set<String> = emptySet(),
    val userApiKey: String = "",
    val currentLlmModel: String = "gemini-2.0-flash",
    val enableProxy: Boolean = false,
    val rssUpdateInterval: Int = 30,
    val lastRssUpdate: Long = 0L,
    val titlesAutoUpdate: Boolean = false,
    val titlesAutoUpdateFrequency: Int = 24,
    val titlesAlarmTimeMins: Int = 540,
    val alarmsAllowed: Boolean = false,
    val notificationsGranted: Boolean = false,
    val updatingTitles: Boolean = false,
    val updatingState: String = "off",
    val updatingProgress: Float = 0f,
    val lastTitlesUpdate: Long = 0L,

    // 3. Храним типизированный объект ошибки
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