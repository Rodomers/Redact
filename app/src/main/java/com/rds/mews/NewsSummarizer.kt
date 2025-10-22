package com.rds.mews

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.mutableListOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import kotlinx.coroutines.sync.withPermit


private data class SummaryResult(
    val originalTitle: String,
    val summary: String,
    val time: Long,
    val sources: List<String>,
    val links: List<String>
)

// ====== Класс для работы с OpenRouter API ======
// Апи ключ пока захардкожен, потом спокойно можно подвязать другой ключ
// "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"
// "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"
// "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"
// ---------------------------------------------------------------------
// сейчас стоит ключ для cloud ru
// api key d2a02527c52b1e4c1da6b640308b2170
// key ID M2I1MTEyMGQtYmRmYS00MDE4LThhNTItMzhjMTE3ZWVhZmQ4.3979e60d1ecbaa29e535571a7199a4f5
// secret key e586ffd44e5801b67a89adcbf4c1cfd9
// ------------------------------------------------------------------------
@Serializable
class LLMClient(
    // gemini api keys
    // AIzaSyBwT2sBtNulYoVFDpxq4uHPx-S-LCq7aAw
    // AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk
    val apiKey: String = "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk",
    val MODEL: String = "gemini-2.0-flash-lite",
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/${if (MODEL != "") MODEL else "gemini-2.0-flash"}:generateContent"
) {

    // Отправляем запрос к Gemini, получаем текст ответа
    suspend fun sendPrompt(prompt: String): String? {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 60 секунд
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 60000
            }
        }

        // Создаём тело запроса
        val requestBody = GeminiRequest(
            contents = listOf(
                ContentInput(
                    parts = listOf(PartInput(prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.5,
                maxOutputTokens = 8192
            )
        )

        // Отправка POST запроса
        val responseString: String = client.post(URL) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        client.close()

        // Парсим JSON и достаем текст
        val json = Json { ignoreUnknownKeys = true }
        val geminiResponse = json.decodeFromString<GeminiResponse>(responseString)
        geminiResponse.candidates?.let {
            if (!it.isEmpty()) {
                return geminiResponse.candidates
                    .flatMap { it.content.parts }
                    .joinToString("\n") { it.text }
            } else {
                return null
            }
        }
        return null
    }

    // --- Сериализуемые классы для запроса ---
    @Serializable
    data class GeminiRequest(
        val contents: List<ContentInput>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Double,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    data class ContentInput(
        val parts: List<PartInput>
    )

    @Serializable
    data class PartInput(
        val text: String
    )

    // --- Сериализуемые классы для ответа ---
    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    @Serializable
    data class Candidate(
        val content: Content
    )

    @Serializable
    data class Content(
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String
    )
}

// ====== Логика суммаризации ======
class NewsSummarizer(
    private val db: DbHelper,
    private val llm: LLMClient
) {

    /**
     * Вспомогательный класс для хранения информации о теме.
     */
    data class Topics(
        val title: String,
        val ids: List<Long>?
    )

    /**
     * Вспомогательный класс для хранения финального результата суммаризации.
     */
    private data class SummaryResult(
        val originalTitle: String,
        val summary: String,
        val time: Long,
        val sources: List<String>,
        val links: List<String>
    )

    // Определяем размер пакета. Можете настроить это значение.
    // 50-100 новостей - безопасный размер, чтобы не превысить лимит токенов LLM.
    private val NEWS_BATCH_SIZE = 70

    /**
     * Основная публичная функция, которую нужно вызывать для запуска всего процесса.
     * Она управляет извлечением, фильтрацией и последующей суммаризацией тем.
     */
    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200, // 168 дней ~ 6 месяцев
        readyFunc: () -> Unit,
        settingsManager: SettingsManager,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            // Получаем все сообщения и сортируем от старых к новым
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            // Проверяем, есть ли незавершенные темы с прошлого раза
            val unfinishedTitles = db.getTitles().filter {
                it.text == "<промежуточный текст>" && it.time.toInt() == 0
            }

            if (unfinishedTitles.isEmpty()) {
                // Если незавершенных тем нет, запускаем полный цикл извлечения
                settingsManager.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                // Запускаем новый, безопасный процесс пакетной обработки
                val success = processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics)

                if (!success) {
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            } else {
                println("Найдены незавершенные темы с прошлого запуска. Продолжаем суммаризацию.")
            }

            // Получаем список тем для суммаризации (либо только что созданные, либо незавершенные)
            val titlesToSummarize = db.getTitles().filter {
                it.text == "<промежуточный текст>"
            }.map {
                Topics(it.title, db.dbUnpack(it.links).mapNotNull { id -> id.toLongOrNull() })
            }

            if (titlesToSummarize.isEmpty()) {
                readyFunc()
                return SummarizationResult.Success // Нет тем для суммаризации
            }

            // --- Начало блока непосредственной суммаризации тем ---

            var counter = 0
            val titlesCounter = titlesToSummarize.size
            var emptyAnswer = false
            val semaphore = Semaphore(1) // Ограничиваем до 2 одновременных запросов к LLM

            val summarizedResults = coroutineScope {
                titlesToSummarize.map { title ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                delay(200L) // Небольшая задержка между запросами
                                counter++
                                settingsManager.saveString(MewsRepository.UPDATING_STATE, "${counter}/${titlesCounter}")
                                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                                val suitableMessages = title.ids?.mapNotNull { id -> messages.find { it.id == id } } ?: emptyList()
                                val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }

                                if (newsText.isBlank()) return@withPermit null

                                val bannedNews = "Таких тем нет"

                                val response = withTimeout(40000L) {
                                    sumTopic(llm, title.title, bannedNews, newsText, currentLanguage)
                                }

                                if (response.isBlank()) {
                                    println("Пустой ответ от LLM для темы: ${title.title}")
                                    emptyAnswer = true
                                    return@withPermit null
                                }

                                val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
                                val obj = JSONObject(cleanResponse)
                                val summary = obj.getString("summary")

                                val ids = title.ids ?: return@withPermit null
                                val time = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()
                                val links = suitableMessages.map { it.link }
                                val sources = suitableMessages.map { it.source }

                                SummaryResult(title.title, summary, time, sources.distinct(), links.distinct())

                            } catch (e: Exception) {
                                println("Ошибка при обработке темы '${title.title}': ${e.message}")
                                e.printStackTrace()
                                return@withPermit null
                            }
                        }
                    }
                }.mapNotNull { it.await() }
            }

            if (summarizedResults.isNotEmpty()) {
                summarizedResults.forEach { result ->
                    println("Обновление в БД: ${result.originalTitle}")
                    db.updateTitle(
                        name = result.originalTitle,
                        newTime = result.time,
                        newText = result.summary,
                        newSources = db.dbPack(*result.sources.toTypedArray()),
                        newLinks = db.dbPack(*result.links.toTypedArray())
                    )
                }
            }

            // Проверяем, все ли темы удалось обработать
            if (summarizedResults.size != titlesToSummarize.size) {
                return if (emptyAnswer) {
                    SummarizationResult.Failure(SummarizationErrorType.EMPTY_ANSWER)
                } else {
                    SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
                }
            }

            readyFunc()
            return SummarizationResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            readyFunc()
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    /**
     * Этап 1: Функция-оркестратор. Разбивает новости на пакеты, извлекает из них темы
     * и отправляет на финальное объединение.
     */
    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, currentLanguage: String, filterTopics: Boolean = false): Boolean {
        // 1. Разбиваем все сообщения на пакеты (чанки)
        val messageBatches = messages.chunked(NEWS_BATCH_SIZE)
        val allExtractedTopics = mutableListOf<Topics>()

        try {
            // 2. Асинхронно извлекаем темы из каждого пакета
            coroutineScope {
                messageBatches.map { batch ->
                    async(Dispatchers.IO) {
                        extractTopicsFromBatch(batch, maxTopics, currentLanguage)
                    }
                }.forEach { deferred ->
                    deferred.await()?.let { topicsFromBatch ->
                        allExtractedTopics.addAll(topicsFromBatch)
                    }
                }
            }

            if (allExtractedTopics.isEmpty()) {
                println("Не удалось извлечь ни одной темы из новостей.")
                return false
            }

            // 3. Отправляем все извлеченные темы на финальное объединение и фильтрацию
            return if (filterTopics) mergeAndFilterTopics(allExtractedTopics, maxTopics, currentLanguage) else true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Этап 1.1: Извлекает темы из ОДНОГО пакета новостей.
     */
    private suspend fun extractTopicsFromBatch(messagesBatch: List<Message>, max: Int, currentLanguage: String): List<Topics>? {
        val combinedNews = messagesBatch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
        val bannedNews = "Таких тем нет"

        val prompt = """
            Твоя задача — проанализировать список новостей и сгруппировать их по ключевым событиям.

            Инструкции:
            1.  Выдели от 1 до $max главных событий из предоставленного списка.
            2.  Сортируй события по важности.
            3.  Объединяй связанные новости в одну тему.
            4.  Каждая новость (id) может относиться только к ОДНОЙ теме.
            5.  Игнорируй запрещённые темы: $bannedNews.
            6.  Текущее время (Unix millis) - ${System.currentTimeMillis()}.

            Формат ответа:
            -   Верни ответ СТРОГО в виде JSON-массива. Никакого текста до или после.
            -   Язык заголовков: ${currentLanguage}.

            Пример: [{"title": "Новые санкции", "id": [101, 105]}, {"title": "Запуск метро", "id": [102, 108]}]

            Новости для анализа:
            $combinedNews
        """.trimIndent()

        try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return null

            val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
            val jsonArray = JSONArray(cleanResponse)
            val topics = mutableListOf<Topics>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val idsArray = obj.getJSONArray("id")
                val ids = (0 until idsArray.length()).map { idsArray.getLong(it) }
                topics.add(Topics(title, ids))
            }
            return topics
        } catch (e: Exception) {
            println("Ошибка при извлечении тем из пакета: ${e.message}")
            return null
        }
    }

    /**
     * Этап 2: Объединяет, фильтрует и ранжирует темы, полученные со всех пакетов.
     */
    private suspend fun mergeAndFilterTopics(topics: List<Topics>, maxTopics: Int, currentLanguage: String): Boolean {
        val titlesJsonForPrompt = JSONArray(
            topics.map { topic ->
                JSONObject().apply {
                    put("title", topic.title)
                    put("ids", JSONArray(topic.ids))
                }
            }
        ).toString()

        val bannedNews = "Таких тем нет"

        val prompt = """
            Твоя задача — проанализировать список новостных тем, объединить дубликаты, отфильтровать и отсортировать их.

            Инструкции:
            1.  **Объедини очень похожие темы.** Например, "Атаки дронов на РФ" и "Налеты БПЛА на регионы России" должны стать одной темой. При объединении сохрани все уникальные ID сообщений из обеих тем.
            2.  **Удали темы, связанные с запрещёнными категориями:** $bannedNews.
            3.  **Оставь ровно $maxTopics самых важных тем.** Если тем изначально меньше, оставь все.
            4.  **Отсортируй итоговый список по убыванию важности.**
            5.  Сохраняй оригинальные `ids` для каждой темы.

            Формат ответа:
            -   Верни результат в виде JSON-массива в ТОМ ЖЕ ФОРМАТЕ, что и на входе.
            -   Ответ должен быть только валидным JSON, без лишних слов.
            -   Язык заголовков: ${currentLanguage}.

            Массив тем для обработки:
            $titlesJsonForPrompt
        """.trimIndent()

        try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return false

            val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
            val jsonArray = JSONArray(cleanResponse)
            val iterEnd = min(jsonArray.length(), maxTopics)

            // Очищаем старые промежуточные заголовки перед добавлением новых
            db.titlesTimeKill(0)

            for (i in 0 until iterEnd) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val idsArray = obj.getJSONArray("ids")
                val idsStr = (0 until idsArray.length()).map { idsArray.getLong(it).toString() }

                db.addTitle(
                    titleTime = 0,
                    title = title,
                    text = "<промежуточный текст>",
                    sources = "<промежуточный текст>",
                    links = db.dbPack(*idsStr.toTypedArray())
                )
            }
            return true
        } catch (e: JSONException) {
            println("Ошибка парсинга JSON при финальном объединении тем: ${e.message}")
            println("Невалидный ответ от LLM")
            return false
        } catch (e: Exception) {
            println("Общая ошибка при финальном объединении тем: ${e.message}")
            return false
        }
    }

    /**
     * Этап 3: Создает детальное резюме для одной конкретной темы.
     */
    @SuppressLint("SuspiciousIndentation")
    private suspend fun sumTopic(llm: LLMClient, title: String, bannedNews: String, newsText: String, currentLanguage: String = "russian"): String {
        val prompt = """
            Твоя задача — составить краткое, но информативное резюме по заданной новостной теме.

            **Тема:** "$title"

            **Инструкции:**
            1.  **Напиши подробное резюме.** Изложи суть события, избегая "воды" и общих фраз.
            2.  **Структурируй текст.** Если в рамках одной темы есть несколько подсобытий, раздели их переносом строки (`\n`).
            3.  **Отражай разные точки зрения.** Если источники предоставляют противоречивую информацию, упомяни обе позиции.
            4.  **Игнорируй запрещённые темы:** $bannedNews.
            5.  **Учитывай временной контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

            **Формат ответа:**
            -   Верни СТРОГО один JSON-объект.
            -   Ответ должен начинаться с `{` и заканчиваться `}`. Никаких пояснений или другого текста вне JSON.
            -   Не используй Markdown-форматирование (жирный шрифт, курсив и т.д.).
            -   Язык ответа: ${currentLanguage}.

            **Пример формата ответа:**
            {
              "title": "$title",
              "summary": "Резюме новостного события. Описание ключевых деталей, действующих лиц и последствий.\nВторое подсобытие в рамках этой же темы."
            }
            
            **Новости для составления резюме:**
            $newsText
        """.trimIndent()

        try {
            return llm.sendPrompt(prompt) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}



