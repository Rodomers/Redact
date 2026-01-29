package com.rds.mews.core

import android.util.Log
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
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

        private val client: OkHttpClient
        private val dnsClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        init {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            builder.addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }

            builder.addInterceptor { chain ->
                val request = chain.request()
                Log.d("API_LOG", "--> ${request.method} ${request.url.toString().replace(
                    MewsRepository.SERVER_KEY, "server key")}")
                try {
                    val response = chain.proceed(request)
                    Log.d("API_LOG", "<-- ${response.code} ${if (response.isSuccessful) "OK" else "FAIL"}")
                    response
                } catch (e: Exception) {
                    Log.e("API_LOG", "<-- ERROR: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
            }

            if (enableProxy) {
                builder.proxySelector(object : ProxySelector() {
                    override fun select(uri: URI?): List<Proxy> {
                        return try {
                            val parts = serverIp.split(":")
                            val host = parts[0]
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: 80

                            val resolvedIp = resolveOverDohRecursive(host, 0) ?: host

                            val proxyAddress = InetSocketAddress(resolvedIp, port)
                            listOf(Proxy(Proxy.Type.HTTP, proxyAddress))
                        } catch (e: Exception) {
                            Log.e("API_LOG", "Proxy selection failed: ${e.message}")
                            listOf(Proxy.NO_PROXY)
                        }
                    }

                    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                        Log.e("API_LOG", "Proxy connection failed: ${ioe?.message}")
                    }
                })

                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic("mews", rssHubKey)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }

            client = builder.build()
        }

        private fun resolveOverDohRecursive(host: String, depth: Int): String? {
            if (depth > 3) return null
            if (isIpAddress(host)) return host

            try {
                val url = "https://8.8.8.8/resolve?name=$host&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                dnsClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body?.string() ?: return null

                    val matcher = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]+)\"").matcher(body)
                    while (matcher.find()) {
                        val data = matcher.group(1)
                        if (isIpAddress(data)) {
                            Log.d("API_LOG", "DoH Resolved IP: $host -> $data")
                            return data
                        }
                    }

                    matcher.reset()
                    if (matcher.find()) {
                        val cname = matcher.group(1)
                        Log.d("API_LOG", "DoH CNAME found: $host -> $cname, recursing...")
                        return resolveOverDohRecursive(cname, depth + 1)
                    }
                }
            } catch (e: Exception) {
                Log.e("API_LOG", "DoH Lookup failed: ${e.message}")
            }
            return null
        }

        private fun isIpAddress(input: String?): Boolean {
            return input != null && Pattern.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$", input)
        }

        suspend fun request(
            method: String,
            urlString: String,
            body: String? = null,
            headers: Map<String, String> = emptyMap()
        ): HttpResponse {
            return withContext(Dispatchers.IO) {
                try {
                    val requestBuilder = Request.Builder().url(urlString)
                    headers.forEach { (k, v) -> requestBuilder.header(k, v) }

                    if (body != null && (method.equals("POST", true) || method.equals("PUT", true))) {
                        val contentType = headers.entries.find { it.key.equals("Content-Type", true) }?.value
                            ?: "application/json; charset=utf-8"
                        requestBuilder.method(method, body.toRequestBody(contentType.toMediaTypeOrNull()))
                    } else {
                        requestBuilder.method(method, null)
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        val responseBody = response.body.string()
                        return@withContext HttpResponse(response.code, responseBody)
                    }

                } catch (e: Exception) {
                    return@withContext HttpResponse(0, "", e)
                }
            }
        }

        suspend fun get(url: String): HttpResponse = request("GET", url)
        suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse = request("POST", url, body, headers)

        override fun close() {
            try {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
                dnsClient.dispatcher.executorService.shutdown()
                dnsClient.connectionPool.evictAll()
            } catch (e: Exception) { }
        }
    }

    fun createInstance(serverIp: String, rssHubKey: String, enableProxy: Boolean = false): JdkClient {
        return JdkClient(serverIp, rssHubKey, enableProxy)
    }
}