package com.rds.mews

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.mutableListOf
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    val links: List<String>
)

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = "gemini-2.0-flash-lite",
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/${if (MODEL != "") MODEL else "gemini-2.0-flash"}:generateContent",
    enableProxy: Boolean = false
) : Closeable {
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.SERVER_IP, MewsRepository.RSS_HUB_KEY, enableProxy = enableProxy)
    private val jsonParser = SharedHttpClient.jsonParser

    suspend fun sendPrompt(prompt: String): String? {
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

        try {
            val responseString: String = httpClient.post(URL) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(responseString)

            return geminiResponse.candidates
                ?.takeIf { it.isNotEmpty() }
                ?.flatMap { candidate -> candidate.content.parts }
                ?.joinToString("\n") { part -> part.text }

        } catch (e: Exception) {
            println("Error during API call: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    override fun close() {
        httpClient.close()
    }

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
        val links: List<String>
    )

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
                settingsManager.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                val success = processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics)

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
                Topics(it.title, db.dbUnpack(it.links).mapNotNull { id -> id.toLongOrNull() })
            }

            if (titlesToSummarize.isEmpty()) {
                readyFunc()
                return SummarizationResult.Success
            }

            var counter = 0
            val titlesCounter = titlesToSummarize.size
            var emptyAnswer = false
            val semaphore = Semaphore(2)

            val summarizedResults = coroutineScope {
                titlesToSummarize.map { title ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                counter++
                                if (counter > 1) delay(6000L)
                                settingsManager.saveString(MewsRepository.UPDATING_STATE, "${counter}/${titlesCounter}")
                                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")
                                println(title.title)

                                val suitableMessages = title.ids?.mapNotNull { id -> messages.find { it.id == id } } ?: emptyList()
                                val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }

                                if (newsText.isBlank()) return@withPermit null

                                val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

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

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, currentLanguage: String, filterTopics: Boolean = false): Boolean {
        val messageBatches = messages.chunked(NEWS_BATCH_SIZE)
        val allExtractedTopics = mutableListOf<Topics>()

        try {
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
    4.  **Обработка мелких новостей:** Новости, которые не относятся ни к одному крупному событию (мелкие происшествия, локальные анонсы), сгруппируй в одну общую тему под названием "Другие новости и короткие события". Эта тема должна содержать примерно столько же новостей, сколько и другие важные темы, и быть одной из многих, а не собирать всё подряд.
    5.  **Уникальность:** Каждая новость (id) может относиться только к ОДНОЙ теме. Если новость подходит к двум темам, выбери наиболее подходящую.
    6.  **Запрещённые темы:** Тебе дан список заголовков, темы которых нельзя поднимать. ВЫДЕЛИ ИЗ КАЖДОГО ЗАГОЛОВКА ТЕМУ И, ЕСЛИ НОВОСТЬ СОВПАДАЕТ С ОДНОЙ ИЗ ТЕМ ЗАГОЛОВКА - НЕ ПИШИ ЕЁ. Список запрещённых тем (заголовков): '$bannedNews'.
    7.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.
    8.  **Ограничения:** Запрещено группировать в одну тему более 75 новостей.

    Формат ответа:
    -   Верни ответ СТРОГО в виде JSON-массива. Никакого текста до или после.
    -   Язык заголовков: ${currentLanguage}.

    Пример: [{"title": "Введены новые экономические санкции против банков", "id": [101, 105]}, {"title": "Запуск новой линии метро в столице", "id": [102, 108]}, {"title": "Другие новости и короткие события", "id": [103, 104, 109]}]

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

        val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

        val prompt = """
    Ты — главный редактор, твоя задача — проанализировать предложенный список новостных тем, объединить дубликаты, отфильтровать маловажные и привести заголовки к единому стандарту.

    Инструкции:
    1.  **Объедини схожие темы.** Например, "Атаки дронов на РФ" и "Налеты БПЛА на регионы России" должны стать одной темой. При объединении сохрани все уникальные ID сообщений из обеих тем. При выборе итогового названия отдавай предпочтение более полному и информативному варианту.
    2.  **Запрещённые темы**: ебе дан список заголовков, темы которых нельзя поднимать. ВЫДЕЛИ ИЗ КАЖДОГО ЗАГОЛОВКА ТЕМУ И, ЕСЛИ НОВОСТЬ СОВПАДАЕТ С ОДНОЙ ИЗ ТЕМ ЗАГОЛОВКА - НЕ ПИШИ ЕЁ. Список запрещённых тем (заголовков): '$bannedNews'.
    3.  **Отфильтруй и оставь ровно $maxTopics самых важных тем.** Если тем изначально меньше, оставь все. Приоритет отдавай темам с наибольшим количеством новостей и наибольшим общественным резонансом.
    4.  **Отформатируй заголовки:** Убедись, что все итоговые заголовки состоят из 5-7 слов, являются ёмкими и точно отражают суть события.
    5.  **Отсортируй финальный список** по убыванию важности.

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
    Ты — беспристрастный новостной аналитик. Твоя задача — составить структурированное и информативное резюме по заданной новостной теме на основе предоставленных материалов.

    **Тема:** "$title"

    **Инструкции:**
    1.  **Напиши подробное резюме объемом до 300 слов.** Изложи суть события, его причины и возможные последствия. Избегай "воды" и общих фраз, используй только факты из новостей.
    2.  **Структурируй текст.** Если в рамках темы есть несколько ключевых подсобытий, раздели их абзацами (переносом строки `\n`).
    3.  **Отражай разные точки зрения.** Если источники предоставляют противоречивую информацию, упомяни это явно. Например: "По данным одного источника..., однако другой источник сообщает, что...".
    4.  **Недостаток информации:** Если предоставленных новостей недостаточно для полного резюме, напиши краткий обзор на основе имеющихся данных и в конце добавь фразу: "Для полного анализа требуется больше информации."
    5.  **Запрещённые темы:** Тебе дан список заголовков, темы которых нельзя поднимать. ВЫДЕЛИ ИЗ КАЖДОГО ЗАГОЛОВКА ТЕМУ И, ЕСЛИ НОВОСТЬ СОВПАДАЕТ С ОДНОЙ ИЗ ТЕМ ЗАГОЛОВКА - НЕ ПИШИ ЕЁ. Список запрещённых тем (заголовков): '$bannedNews'. 
    6.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

    **Формат ответа:**
    -   Верни СТРОГО один JSON-объект.
    -   Ответ должен начинаться с `{` и заканчиваться `}`. Никаких пояснений или другого текста вне JSON.
    -   Не используй Markdown-форматирование (жирный шрифт, курсив и т.д.).
    -   Язык ответа: ${currentLanguage}.

    **Пример формата ответа:**
    {
      "title": "$title",
      "summary": "Резюме новостного события. Описание ключевых деталей, действующих лиц и последствий. Объем текста — до 300 слов.\nВторое подсобытие в рамках этой же темы, изложенное в новом абзаце."
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




