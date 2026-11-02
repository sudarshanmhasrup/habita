package dev.habita.app.core.network

import dev.habita.app.core.data.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
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

    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    suspend inline fun <reified T> get(path: String): T =
        request(path) { method = HttpMethod.Get }.body()

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