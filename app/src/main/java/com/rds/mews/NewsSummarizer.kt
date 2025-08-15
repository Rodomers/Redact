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
    private val apiKey: String = "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", //"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",
    private val referer: String = "http://localhost",
    private val siteName: String = "NewsSummarizer",
    private val OPENROUTER_MODEL: String = "openai/gpt-oss-20b:free",
    private val OPENROUTER_URL: String = "https://openrouter.ai/api/v1/chat/completions"

) {
    private val client = OkHttpClient()

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
            .addHeader("HTTP-Referer", referer)
            .addHeader("X-Title", siteName)
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
class NewsSummarizer(private val db: DbHelper, private val llm: OpenRouterClient, private val maxTopics: Int = 20) {

    /**
     * Этап 1: выделяем темы из новостей и сохраняем в titles
     */
    suspend fun extractTopics(max: Int = maxTopics): List<String> {
        val messages = db.getMessages()
        var topics: List<String> = mutableListOf()
        if (messages.isEmpty()) {
            println("Нет новостей для анализа")
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
        println("Выделено тем: ${topics.size}")
        delay(30000)
        return topics
    }

    /**
     * Этап 2: для каждой темы создаем краткое резюме с указанием источников
     */
    suspend fun summarizeTopics(maxTopics: Int = 20) {
        val titles = extractTopics(maxTopics)
        val messages = db.getMessages()

        titles.forEach { title ->

            val newsText = messages.joinToString("\n") { "— ${it.mess} (источник: ${it.source}, ссылка: ${it.link}, id - ${it.id})" }
            val prompt = """
                Составь краткое резюме по теме: "${title}".
                Укажи основные факты и перечисли источники c указанием ссылок. 
                Возвращай всё в СТРОГОМ JSON формате: {
                "title": "<заголовок темы>", 
                "summary": "<резюме по теме>", 
                "sources": ["<истоники>"], 
                "links": ["<ссылки>"],
                "id": ["<id`s>"}
                }, 
                где title - название темы,
                summary - резюме по теме, 
                sources - источники информации (Название СМИ), 
                которые относятся к теме,
                links - ссылки, относящиеся к теме.
                
                Требования:
                1. Не добавляй никакие ``` или ""${'"'}.
                2. Не добавляй пояснения или текст вне JSON.
                3. Ответ должен начинаться с { и заканчиваться }.
                4. Отвечай на РУССКОМ языке.
                
                Новости:
                $newsText
            """.trimIndent()

            val response = llm.sendPrompt(prompt) ?: return@forEach
            // Сохраняем в БД
            val cleanResponse = response.trim().replace("```json", "")
                .replace("```", "")
                .replace("\"\"\"", "")
                .trim()
            println(cleanResponse.toString())
            println("-------------------------------------------------------")
            val obj = JSONObject(cleanResponse)
            val summary = obj.getString("summary")
            val sources = obj.getString("sources")
            val links = obj.getString("links")
            val ids = obj.getString("id")
            var time: Long = 2147483648
            ids.forEach{ id -> time = min(db.getMessage(id.toLong())?.time ?: 0, time)}
            db.addTitle(titleTime = time, title = title, text = summary,
                sources = sources, links = links)
            println("Резюме для темы '${title}' сохранено")
            println("-------------------------------------------------------")
            delay(30000)
        }
    }
}
