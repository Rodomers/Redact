package com.rds.mews.core

import android.annotation.SuppressLint
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SettingsManager
import com.rds.mews.localcore.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.mutableListOf
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import kotlinx.coroutines.sync.withPermit
import java.io.Closeable


private data class SummaryResult(
    val originalTitle: String,
    val summary: String,
    val time: Long,
    val sources: List<String>,
    val msgIds: List<Long>
)

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = MewsRepository.defaultModel.key,
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/${if (MODEL != "") MODEL else MewsRepository.defaultModel.key}:generateContent",
    enableProxy: Boolean = false
) : Closeable {
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.PROXY_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = enableProxy)
    private val jsonParser = SharedHttpClient.jsonParser

    suspend fun sendPrompt(prompt: String): String? {
        val requestBody = GeminiRequest(
            contents = listOf(
                ContentInput(parts = listOf(PartInput(prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.5,
                maxOutputTokens = 8192
            )
        )

        try {
            val response = httpClient.post(URL) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseString = response.body<String>()

            println("--- RAW GEMINI RESPONSE ---")
            println("Status Code: ${response.status}")
            println("Body: $responseString")
            println("---------------------------")

            val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(responseString)

            if (geminiResponse.error != null) {
                println("API ERROR: ${geminiResponse.error.message} (Code: ${geminiResponse.error.code})")
                return null
            }

            if (geminiResponse.promptFeedback?.blockReason != null) {
                println("PROMPT BLOCKED: ${geminiResponse.promptFeedback.blockReason}")
                return null
            }

            return geminiResponse.candidates
                ?.takeIf { it.isNotEmpty() }
                ?.flatMap { candidate -> candidate.content?.parts ?: emptyList() }
                ?.joinToString("\n") { part -> part.text }

        } catch (e: Exception) {
            println("Error inside sendPrompt logic: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    data class SafetySetting(
        val category: String,
        val threshold: String
    )

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

    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>? = null,
        val error: GeminiError? = null,
        val promptFeedback: PromptFeedback? = null
    )

    @Serializable
    data class GeminiError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null
    )

    @Serializable
    data class PromptFeedback(
        val blockReason: String? = null
    )

    @Serializable
    data class Candidate(
        val content: Content?,
        val finishReason: String? = null
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

suspend fun validateGeminiKey(
    apiKey: String,
    proxyIp: String,
    proxyKey: String,
    enableProxy: Boolean
): Boolean {
    val client = SharedHttpClient.createInstance(
        serverIp = proxyIp,
        rssHubKey = proxyKey,
        enableProxy = enableProxy
    )

    return try {
        val response = client.get("https://generativelanguage.googleapis.com/v1beta/models") {
            parameter("key", apiKey)
        }

        response.status == HttpStatusCode.OK
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        client.close()
    }
}

class NewsSummarizer(
    private val db: DbHelper,
    private val llm: LLMClient,
) {
    data class Topics(
        val title: String,
        val ids: List<Long>?
    )

    private data class SummaryResult(
        val originalTitle: String,
        val summary: String,
        val time: Long,
        val sources: List<String>,
        val msgIds: List<Long>
    )

    private val SUMMARY_BATCH_SIZE = 5

    private val NEWS_BATCH_SIZE = 70

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        settingsManager: SettingsManager,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            val unfinishedTitles = db.getTitles().filter {
                it.text == "<промежуточный текст>" && it.time.toInt() == 0
            }

            if (unfinishedTitles.isEmpty()) {
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                val success = processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics, settingsManager)

                if (!success) {
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            } else {
                println("Найдены незавершенные темы с прошлого запуска. Продолжаем суммаризацию.")
            }

            val titlesToSummarize = db.getTitles().filter {
                it.text == "<промежуточный текст>"
            }.map {
                Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() })
            }

            if (titlesToSummarize.isEmpty()) {
                readyFunc()
                return SummarizationResult.Success
            }

            val titleBatches = titlesToSummarize.chunked(SUMMARY_BATCH_SIZE)

            var processedBatches = 0
            val totalBatches = titleBatches.size
            var emptyAnswer = false
            val semaphore = Semaphore(1)

            val currentProgress = settingsManager.getFloat(MewsRepository.UPDATING_PROGRESS, 0f)
            val remainingProgress = 0.95f - currentProgress

            val summarizedResults = coroutineScope {
                titleBatches.map { batch ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                processedBatches++
                                if (processedBatches > 1) delay(2000L)

                                settingsManager.saveString(MewsRepository.UPDATING_STATE, "summarizing_topics")
                                settingsManager.saveFloat(
                                    MewsRepository.UPDATING_PROGRESS,
                                    (currentProgress + remainingProgress * processedBatches / totalBatches).coerceIn(
                                        0f,
                                        0.95f
                                    )
                                )
                                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                                println("Обработка пачки из ${batch.size} тем...")

                                val topicsData = batch.mapNotNull { topic ->
                                    val suitableMessages = topic.ids?.mapNotNull { id ->
                                        var msg = messages.find { it.id == id }
                                        if (msg == null) msg = db.getMessage(id)
                                        msg
                                    } ?: emptyList()
                                    val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }

                                    if (newsText.isBlank()) null
                                    else Triple(topic, suitableMessages, newsText)
                                }

                                if (topicsData.isEmpty()) return@withPermit emptyList<SummaryResult>()

                                val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

                                val response = withTimeout(90000L) {
                                    sumTopicsBatch(llm, topicsData, bannedNews, currentLanguage)
                                }

                                if (response.isBlank()) {
                                    println("Пустой ответ от LLM для пачки тем")
                                    emptyAnswer = true
                                    return@withPermit emptyList<SummaryResult>()
                                }

                                val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
                                val jsonArray = JSONArray(cleanResponse)
                                val results = mutableListOf<SummaryResult>()

                                for (i in 0 until jsonArray.length()) {
                                    try {
                                        val obj = jsonArray.getJSONObject(i)
                                        val topicTitle = obj.getString("title")
                                        val summary = obj.getString("summary")

                                        val originalData = topicsData.find { it.first.title == topicTitle }

                                        if (originalData != null) {
                                            val (topic, suitableMessages, _) = originalData
                                            val time = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()

                                            val msgIds = suitableMessages.map { it.id }
                                            val sources = suitableMessages.map { it.source }

                                            results.add(SummaryResult(
                                                originalTitle = topic.title,
                                                summary = summary,
                                                time = time,
                                                sources = sources.distinct(),
                                                msgIds = msgIds.distinct()
                                            ))
                                        }
                                    } catch (e: Exception) {
                                        println("Ошибка парсинга одного элемента из пачки: ${e.message}")
                                    }
                                }
                                return@withPermit results

                            } catch (e: Exception) {
                                println("Ошибка при обработке пачки тем: ${e.message}")
                                e.printStackTrace()
                                return@withPermit emptyList<SummaryResult>()
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            if (summarizedResults.isNotEmpty()) {
                summarizedResults.forEach { result ->
                    println("Обновление в БД: ${result.originalTitle}")
                    db.updateTitle(
                        name = result.originalTitle,
                        newTime = result.time,
                        newText = result.summary,
                        newSources = db.dbPack(*result.sources.toTypedArray()),
                        newLinks = db.dbPack(*result.msgIds.map { it.toString() }.toTypedArray())
                    )
                }
            }

            if (summarizedResults.isEmpty() && titlesToSummarize.isNotEmpty()) {
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

    private suspend fun processNewsInBatches(
        maxTopics: Int,
        messages: List<Message>,
        currentLanguage: String,
        filterTopics: Boolean = false,
        settingsManager: SettingsManager
    ): Boolean {
        val messageBatches = messages.chunked(NEWS_BATCH_SIZE)
        val allExtractedTopics = mutableListOf<Topics>()

        try {
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
            settingsManager.saveFloat(
                MewsRepository.UPDATING_PROGRESS, (settingsManager.getFloat(
                    MewsRepository.UPDATING_PROGRESS, 0.1f
                ) + 0.1f).coerceIn(0f, 0.2f)
            )

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

            return if (filterTopics) {
                settingsManager.saveString(MewsRepository.UPDATING_STATE, "filtering_topics")
                settingsManager.saveFloat(
                    MewsRepository.UPDATING_PROGRESS, (settingsManager.getFloat(
                        MewsRepository.UPDATING_PROGRESS, 0.1f
                    ) + 0.1f).coerceIn(0f, 0.3f)
                )
                mergeAndFilterTopics(allExtractedTopics, maxTopics, currentLanguage)
            } else {
                allExtractedTopics.forEachIndexed { index, batch ->
                    if (index < maxTopics) {
                        val idsStr = batch.ids?.map { it.toString() } ?: listOf("")

                        db.addTitle(
                            title = batch.title,
                            titleTime = 0,
                            text = "<промежуточный текст>",
                            sources = "<промежуточный текст>",
                            links = db.dbPack(*idsStr.toTypedArray())
                        )
                    }
                }
                true
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun extractTopicsFromBatch(messagesBatch: List<Message>, max: Int, currentLanguage: String): List<Topics>? {
        val combinedNews = messagesBatch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
        val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

        val prompt = """
    Ты — опытный новостной редактор. Твоя задача — проанализировать список новостей и сгруппировать их по ключевым событиям.

    Инструкции:
    1.  **Выдели от 1 до $max главных событий.** Группируй новости, описывающие одно и то же событие или тесно связанные происшествия.
    2.  **Сортируй темы по важности.** Важность определяется влиянием события на большое количество людей, его масштабом и значимостью.
    3.  **Формулируй заголовки тем.** Каждый заголовок должен быть информативным и состоять из 5-7 слов.
    4.  **Обработка мелких новостей:** Новости, которые не относятся ни к одному крупному событию (мелкие происшествия, локальные анонсы), сгруппируй в одну общую тему под названием "Другие новости и короткие события". Эта тема должна содержать примерно столько же новостей, сколько и другие важные темы.
    5.  **Уникальность:** Каждая новость (id) может относиться только к ОДНОЙ теме.
    6.  **Запрещённые темы:** Если группа новостей целиком посвящена теме из списка запрещенных, НЕ включай её в ответ. Список запрещённых тем: '$bannedNews'.
    7.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.
    8.  **Ограничения:** Запрещено группировать в одну тему более 75 новостей.

    Формат ответа:
    -   Верни ответ СТРОГО в виде JSON-массива. Никакого текста до или после.
    -   Язык заголовков: ${currentLanguage}.

    Пример: [{"title": "Введены новые экономические санкции против банков", "id": [101, 105]}, {"title": "Запуск новой линии метро в столице", "id": [102, 108]}]

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

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, maxTopics: Int, currentLanguage: String): Boolean {
        val titlesJsonForPrompt = JSONArray(
            topics.map { topic ->
                JSONObject().apply {
                    put("title", topic.title)
                    put("ids", JSONArray(topic.ids))
                }
            }
        ).toString()

        val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

        val prompt = """
    Ты — главный редактор, твоя задача — проанализировать предложенный список новостных тем, объединить дубликаты, отфильтровать маловажные и привести заголовки к единому стандарту.

    Инструкции:
    1.  **Объедини схожие темы.** Например, "Атаки дронов на РФ" и "Налеты БПЛА на регионы России" должны стать одной темой. При объединении сохрани все уникальные ID сообщений из обеих тем.
    2.  **Запрещённые темы**: Если тема совпадает со списком, исключи её. Список: '$bannedNews'.
    3.  **Отфильтруй и оставь ровно $maxTopics самых важных тем.** Приоритет темам с большим количеством новостей.
    4.  **Отформатируй заголовки:** 5-7 слов, ёмко.
    5.  **Отсортируй по убыванию важности.**

    Формат ответа:
    -   Верни результат в виде JSON-массива в ТОМ ЖЕ ФОРМАТЕ (title, ids), что и на входе.
    -   Ответ должен быть только валидным JSON.
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
            return false
        } catch (e: Exception) {
            println("Общая ошибка при финальном объединении тем: ${e.message}")
            return false
        }
    }

    /**
     * Этап 3 (Новый): Создает резюме для ПАКЕТА (batch) тем.
     */
    @SuppressLint("SuspiciousIndentation")
    private suspend fun sumTopicsBatch(
        llm: LLMClient,
        batchData: List<Triple<Topics, List<Message>, String>>,
        bannedNews: String,
        currentLanguage: String
    ): String {

        val jsonInput = JSONArray()
        batchData.forEach { (topic, _, text) ->
            val item = JSONObject()
            item.put("title", topic.title)
            item.put("news_content", text)
            jsonInput.put(item)
        }

        val prompt = """
    Ты — беспристрастный новостной аналитик. Твоя задача — составить структурированные резюме для списка новостных тем.

    Вот список тем и материалов к ним в формате JSON. Для КАЖДОГО элемента списка напиши отдельное резюме.

    **Инструкции для каждого резюме:**
    1.  **Объем:** до 250 слов на тему.
    2.  **Суть:** Опиши событие, причины и последствия. Избегай воды.
    3.  **Структура:** Используй абзацы (`\n`) для разделения подсобытий внутри темы.
    4.  **Разные точки зрения:** Если есть противоречия, укажи их.
    5.  **Запрещенные темы:** Проверь каждую тему по списку заголовков: '$bannedNews'. Если тема попадает под запрет, верни пустую строку в поле summary или текст "Тема пропущена".
    6.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

    **Формат ответа:**
    -   Верни СТРОГО JSON-массив объектов.
    -   Структура объекта: `{"title": "Исходный заголовок темы", "summary": "Текст резюме..."}`.
    -   Ключ `title` должен В ТОЧНОСТИ совпадать с тем, что был в запросе, чтобы я мог сопоставить ответ.
    -   Язык ответа: ${currentLanguage}.
    -   Не используй Markdown форматирование внутри JSON, только простой текст.

    **Входные данные:**
    $jsonInput
""".trimIndent()

        try {
            return llm.sendPrompt(prompt) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
