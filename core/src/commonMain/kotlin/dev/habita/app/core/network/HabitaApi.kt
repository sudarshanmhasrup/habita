package dev.habita.app.core.network

import dev.habita.app.core.data.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why a server call failed. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The app was built without HABITA_SERVER_URL. */
    class NotConfigured : ApiException("No server address set")

    /** The server could not be reached. */
    class Offline(cause: Throwable) : ApiException("Server unreachable", cause)

    /** Wrong login, or a token the server no longer accepts. */
    class Unauthorized(message: String) : ApiException(message)

    class Server(val status: Int, message: String) : ApiException(message)
}

@Serializable
private data class ErrorBody(val error: String)

/**
 * The Habita server. Requests carry the saved login token. When the server rejects that token, it
 * is cleared, which sends the app back to the login screen.
 */
class HabitaApi(serverUrl: String, private val settings: Settings) {
    val isConfigured: Boolean = serverUrl.isNotBlank()

    /** The server address with one trailing slash, so paths can be appended as they are. */
    private val baseUrl = serverUrl.trim().trimEnd('/') + "/"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(httpEngine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
        install(WebSockets)
    }

    suspend inline fun <reified T> get(path: String): T =
        request(path) { method = HttpMethod.Get }.body()

    /** Like [get], but `null` when the server answers 204 No Content. */
    suspend inline fun <reified T> getOrNull(path: String): T? {
        val response = request(path) { method = HttpMethod.Get }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }

    /**
     * Streams a large response, such as an app update, to [write] in chunks instead of holding it
     * in memory. [onProgress] gets the share done, from 0 to 1, when the size is known.
     */
    suspend fun download(path: String, onProgress: (Float) -> Unit = {}, write: (ByteArray, Int) -> Unit) {
        if (!isConfigured) throw ApiException.NotConfigured()
        val token = settings.token.value
        try {
            client.prepareRequest(baseUrl + path.removePrefix("/")) {
                method = HttpMethod.Get
                token?.let { bearerAuth(it) }
                timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS }
            }.execute { response ->
                if (response.status == HttpStatusCode.Unauthorized) throw ApiException.Unauthorized(response.status.description)
                if (!response.status.isSuccess()) throw ApiException.Server(response.status.value, response.status.description)
                val total = response.contentLength()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        write(buffer, read)
                        done += read
                        if (total != null && total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException.Offline(e)
        }
    }

    /**
     * Opens a WebSocket at [path] with the saved token and calls [onText] with every text message.
     * Returns when the server closes it or sends nothing for [silenceMillis]; throws when it can't
     * be opened.
     */
    suspend fun listen(path: String, silenceMillis: Long, onText: suspend (String) -> Unit) {
        if (!isConfigured) throw ApiException.NotConfigured()
        val token = settings.token.value ?: throw ApiException.Unauthorized("Not logged in")
        val url = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + path.removePrefix("/")
        client.webSocket(url, request = { bearerAuth(token) }) {
            while (true) {
                val frame = withTimeoutOrNull(silenceMillis) { incoming.receiveCatching().getOrNull() } ?: return@webSocket
                if (frame is Frame.Text) onText(frame.readText())
            }
        }
    }

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        request(path) {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    @PublishedApi
    internal suspend fun request(path: String, configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        if (!isConfigured) throw ApiException.NotConfigured()
        val token = settings.token.value
        val response = try {
            client.request(baseUrl + path.removePrefix("/")) {
                token?.let { bearerAuth(it) }
                configure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException.Offline(e)
        }
        if (response.status.isSuccess()) return response
        val message = runCatching { response.body<ErrorBody>().error }.getOrNull() ?: response.status.description
        if (response.status == HttpStatusCode.Unauthorized) {
            if (token != null && settings.token.value == token) settings.clearToken()
            throw ApiException.Unauthorized(message)
        }
        throw ApiException.Server(response.status.value, message)
    }
}

/** An update can take a while on a slow connection. */
private const val DOWNLOAD_TIMEOUT_MILLIS = 10 * 60 * 1000L