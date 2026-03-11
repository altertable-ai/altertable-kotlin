package ai.altertable.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import java.io.IOException

private const val BACKOFF_BASE_MS = 1000L
private const val BACKOFF_MAX_MS = 10000L

internal class Transport(
    private val apiKey: String,
    private val baseUrl: String,
    private val dispatcher: CoroutineDispatcher,
    private val requestTimeout: Duration,
    private val maxRetries: Int,
    engine: HttpClientEngine? = null,
) : AutoCloseable {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    private val client = HttpClient(engine ?: OkHttp.create()) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        }
        if (engine == null) {
            engine {
                followRedirects = true
            }
        }
    }

    internal suspend fun post(payload: ApiPayload) {
        retryWithBackoff(maxRetries) { attempt ->
            try {
                val response =
                    withContext(dispatcher) {
                        client.post("$baseUrl${payload.endpoint}") {
                            header("X-API-Key", apiKey)
                            contentType(ContentType.Application.Json)
                            setBody(
                                when (payload) {
                                    is ApiPayload.Track -> payload.payload as Any
                                    is ApiPayload.Identify -> payload.payload as Any
                                    is ApiPayload.Alias -> payload.payload as Any
                                },
                            )
                        }
                    }
                if (!response.status.isSuccess()) {
                    val status = response.status.value
                    var errorCode: String? = null
                    try {
                        val errorJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                        errorCode = errorJson?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: SerializationException) {
                        // Ignore JSON parsing error - errorCode remains null
                    }
                    val err =
                        AltertableError.Api(
                            status,
                            response.status.description,
                            errorCode,
                            null,
                            "API Error: $status",
                        )
                    // Retry on 5xx errors
                    if (status in 500..599) {
                        throw RetryableError(AltertableException(err))
                    }
                    throw AltertableException(err)
                }
            } catch (e: AltertableException) {
                throw e
            } catch (e: IOException) {
                throw RetryableError(AltertableException(AltertableError.Network("Network request failed", e)))
            } catch (e: Throwable) {
                throw RetryableError(AltertableException(AltertableError.Network("Network request failed", e)))
            }
        }
    }

    override fun close() {
        client.close()
    }
}

/**
 * Wrapper for errors that should trigger a retry.
 */
private class RetryableError(val exception: AltertableException) : Exception(exception)

/**
 * Retries a suspend operation with exponential backoff and jitter.
 *
 * @param maxRetries Maximum number of retry attempts (0 means no retries, only one attempt)
 * @param operation The suspend operation to retry. Receives the current attempt number (0-indexed).
 * @throws AltertableException The last error encountered if all retries are exhausted
 */
private suspend fun <T> retryWithBackoff(
    maxRetries: Int,
    operation: suspend (attempt: Int) -> T,
): T {
    val effectiveMaxRetries = maxRetries.coerceAtLeast(0)
    var lastError: AltertableError? = null

    for (attempt in 0..effectiveMaxRetries) {
        try {
            return operation(attempt)
        } catch (e: AltertableException) {
            // Non-retryable error, throw immediately
            throw e
        } catch (e: RetryableError) {
            lastError = e.exception.error
            if (attempt < effectiveMaxRetries) {
                delay(backoffWithJitter(attempt))
            } else {
                throw e.exception
            }
        }
    }

    throw AltertableException(lastError ?: AltertableError.Network("Network request failed", Exception("Unknown error")))
}

/**
 * Calculates exponential backoff delay with jitter.
 *
 * @param attempt The current attempt number (0-indexed)
 * @return Delay in milliseconds
 */
private fun backoffWithJitter(attempt: Int): Long {
    val exponential = (BACKOFF_BASE_MS * (1 shl attempt)).coerceAtMost(BACKOFF_MAX_MS)
    return Random.nextLong(exponential / 2, exponential)
}
