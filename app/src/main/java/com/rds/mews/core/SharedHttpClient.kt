import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*


object SharedHttpClient {
    val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun createInstance(serverIp: String, rssHubKey: String, enableProxy: Boolean = false): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonParser)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 60000
            }

            if (enableProxy) {
                engine {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(serverIp, 80))
                }

                defaultRequest {
                    val credentials = "mews:$rssHubKey"
                    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
                    header("Proxy-Authorization", "Basic $encodedCredentials")
                }
            }
        }
    }
}