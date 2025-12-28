package com.rds.mews.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.json.Json

object SharedHttpClient {
    val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class HttpResponse(
        val status: Int,
        val body: String,
        val error: Exception? = null
    )

    class JdkClient(
        private val serverIp: String,
        private val rssHubKey: String,
        private val enableProxy: Boolean
    ) : Closeable {

        suspend fun request(
            method: String,
            urlString: String,
            body: String? = null,
            headers: Map<String, String> = emptyMap()
        ): HttpResponse {
            return withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(urlString)

                    val proxy = if (enableProxy) {
                        Proxy(Proxy.Type.SOCKS, InetSocketAddress(serverIp, 8443))
                    } else {
                        Proxy.NO_PROXY
                    }

                    connection = url.openConnection(proxy) as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 60_000
                    connection.requestMethod = method
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                    if (enableProxy) {
                        val credentials = "mews:$rssHubKey"
                        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                        connection.setRequestProperty("Proxy-Authorization", "Basic $encoded")
                    }

                    headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

                    if (body != null && (method == "POST" || method == "PUT")) {
                        connection.doOutput = true
                        connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(body) }
                    }

                    val status = connection.responseCode

                    val stream = if (status < 400) connection.inputStream else connection.errorStream
                    val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""

                    return@withContext HttpResponse(status, responseBody)

                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext HttpResponse(0, "", e)
                } finally {
                    connection?.disconnect()
                }
            }
        }

        suspend fun get(url: String): HttpResponse {
            return request("GET", url)
        }

        suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse {
            return request("POST", url, body, headers)
        }

        override fun close() {}
    }

    fun createInstance(serverIp: String, rssHubKey: String, enableProxy: Boolean = false): JdkClient {
        return JdkClient(serverIp, rssHubKey, enableProxy)
    }
}