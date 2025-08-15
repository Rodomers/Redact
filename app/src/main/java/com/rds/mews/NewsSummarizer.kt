package com.rds.mews

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min


// ====== Класс для работы с OpenRouter API ======
class OpenRouterClient(
    // Апи ключ пока захардкожен, потом спокойно можно подвязать другой ключ
    // "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"
    // "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"
    private val apiKey: String = "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",
    // модель на опенроутер, можно тоже потом выбор пользователю давать
    private val OPENROUTER_MODEL: String = "openai/gpt-oss-20b:free",
    // ссылка на опенроутер
    private val OPENROUTER_URL: String = "https://openrouter.ai/api/v1/chat/completions"

) {
    //инициализация клиента
    private val client = OkHttpClient()
    //Отправляем запрос нейросети, получаем в ответ json
    fun sendPrompt(prompt: String): String? {
        val requestBody = JSONObject()
            .put("model", OPENROUTER_MODEL)
            .put("messages", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("content", prompt)
            ))

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody.toString()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Ошибка API: ${response.code} — ${response.message}")
                return null
            }
            val json = JSONObject(response.body?.string() ?: "")
            return json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }
}

// ====== Логика суммаризации ======
class NewsSummarizer(
    private val db: DbHelper, // Хелпер для БД
    private val llm: OpenRouterClient, // клиенты для опенроутера
) {

    /**
     * Этап 1: выделяем темы из новостей и сохраняем в titles
     * Функцию можно впринципе не вызывать, она приватная и нужна только для следующей
     * можно указать сколько тем извлечь
     * можно указать на актуальность новостей в секундах, по стандарту - неделя
     */
    suspend private fun extractTopics(max: Int = 20, messageSeconds: Long = 14515200): List<String> {
        val messages = db.getMessages(messageSeconds)
        var topics: List<String> = mutableListOf()
        if (messages.isEmpty()) {
            println("Нет новостей для анализа") // ошибка, если нет новостей для суммаризации
            return topics
        }
        val combinedNews = messages.joinToString("\n") { "• ${it.mess}" }
        val prompt = """
            Проанализируй новости и выдели от 1 до $max основных тем.
            Ответ верни в виде списка, каждая тема с новой строки.
            
            Новости:
            $combinedNews
        """.trimIndent()

        val topicsText = llm.sendPrompt(prompt) ?: return topics
        topics = topicsText.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotBlank() }
        delay(30000)
        return topics
    }

    /**
     * Этап 2: для каждой темы создаем краткое резюме с указанием источников
     * Эту функцию уже вызывать в коде, она загружает всё в бд
     * можно указать максимальное количество тем
     * можно указать время новостей в секундах, по стандарту - неделя
     * можно указать время между запросами, по стандарту - 10 секунд
     */
    suspend fun summarizeTopics(maxTopics: Int = 20, messageSeconds: Long = 14515200, delaySeconds: Long = 10) {
        val titles = extractTopics(maxTopics, messageSeconds)
        val messages = db.getMessages(messageSeconds)

        titles.forEach { title ->
            // Составление списка новостей для нейронки
            val newsText = messages.joinToString("\n") { "— ${it.mess} (id - ${it.id})" }
            val prompt = """
                Составь краткое резюме по теме: "${title}".
                Укажи основные факты и перечисли id сообщений, относящихся к теме. 
                Возвращай всё в СТРОГОМ JSON формате: {
                "title": "<заголовок темы>", 
                "summary": "<резюме по теме>", 
                "id": ["<id`s>"]
                }, 
                где title - название темы,
                summary - резюме по теме, 
                id - id новостей, относящихчся к теме.
                
                Требования:
                1. Не добавляй никакие ``` или ""${'"'}.
                2. Не добавляй пояснения или текст вне JSON.
                3. Ответ должен начинаться с { и заканчиваться }.
                4. Отвечай на РУССКОМ языке.
                5. Не оставляй поля пустыми НИ В КОЕМ СЛУЧАЕ! ВСЁ ПОЛЯ ДОЛЖНЫ ТАК ИЛИ ИНАЧЕ БЫТЬ ЗАПОЛНЕНЫ
                                
                Новости:
                $newsText
            """.trimIndent()

            val response = llm.sendPrompt(prompt) ?: return@forEach

            // Приведение ответа к адекватному виду
            val cleanResponse = response.trim().replace("```json", "")
                .replace("```", "")
                .replace("\"\"\"", "")
                .trim()


            // Парсинг ответа по полям
            val obj = JSONObject(cleanResponse)
            val summary = obj.getString("summary")
            val idsStr = obj.getString("id")
            // id в список лонгов
            val ids = mutableListOf<Long>()
            idsStr.forEach { id -> ids += id.toLong() }

            // время для темы выбирается по первой новости по этой теме или по системному времени, если ошибка
            var time: Long = System.currentTimeMillis()
            ids.forEach{ id -> time = min(db.getMessage(id)?.time ?: 0, time)}

            // линки и сурсы в особый формат по id
            var links: String = ""
            var sources: String = ""
            var pair: Pair<String, String>?

            ids.forEach { id ->
                pair = db.getTitleLinks(id)
                links += pair?.first + "\n"
                sources += pair?.second + "\n"
            }

            // Сохраняем в БД
            db.addTitle(titleTime = time, title = title, text = summary,
                sources = sources, links = links)

            // задержка перед следующим запросом
            delay(delaySeconds * 1000)
        }
    }
}
