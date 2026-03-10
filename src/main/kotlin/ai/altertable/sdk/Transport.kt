package ai.altertable.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow

class Transport(
    private val config: AltertableConfig
) {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout)
        engine {
            config {
                followRedirects(true)
            }
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 2000L
        private const val SERVER_ERROR_MIN = 500
        private const val SERVER_ERROR_MAX = 599
        private const val RATE_LIMIT_ERROR = 429
    }

    @Suppress("ThrowsCount", "TooGenericExceptionCaught", "SwallowedException")
    suspend fun post(endpoint: String, payload: Map<String, Any?>, retryCount: Int = 0) {
        try {
            val response = withContext(Dispatchers.IO) {
                client.post("${config.baseUrl}$endpoint") {
                    header("X-API-Key", config.apiKey)
                    parameter("environment", config.environment)
                    contentType(ContentType.Application.Json)
                    setBody(serialize(payload))
                }
            }
            if (!response.status.isSuccess()) {
                val statusCode = response.status.value
                var errorCode: String? = null
                try {
                    val errorJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    errorCode = errorJson?.get("error")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) {
                    // Ignore parsing error
                }

                // Retry on 5xx errors or 429
                val shouldRetry = statusCode in SERVER_ERROR_MIN..SERVER_ERROR_MAX || 
                                  statusCode == RATE_LIMIT_ERROR
                if (shouldRetry && retryCount < MAX_RETRIES) {
                    val delayMs = BASE_DELAY_MS * 2.0.pow(retryCount.toDouble()).toLong()
                    delay(delayMs)
                    post(endpoint, payload, retryCount + 1)
                    return
                }

                val err = ApiError(
                    response.status.value,
                    response.status.description,
                    errorCode,
                    null,
                    "API Error: ${response.status.value}"
                )
                config.onError?.invoke(err) ?: throw err
            }
        } catch (e: AltertableError) {
            throw e
        } catch (e: Exception) {
            // Retry on network errors
            if (retryCount < MAX_RETRIES) {
                val delayMs = BASE_DELAY_MS * 2.0.pow(retryCount.toDouble()).toLong()
                delay(delayMs)
                post(endpoint, payload, retryCount + 1)
                return
            }
            val netErr = NetworkError("Network request failed", e)
            config.onError?.invoke(netErr) ?: throw netErr
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun postEvent(event: QueuedEvent) {
        val payload = try {
            json.parseToJsonElement(event.payloadJson).let { element ->
                parseJsonElement(element)
            }
        } catch (e: Exception) {
            emptyMap()
        }
        post(event.endpoint, payload)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonElement(element: kotlinx.serialization.json.JsonElement): Map<String, Any?> {
        return when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                element.mapValues { (_, v) -> parseJsonValue(v) }.toMap()
            }
            else -> emptyMap()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonValue(value: kotlinx.serialization.json.JsonElement): Any? {
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> value.content
            is kotlinx.serialization.json.JsonObject -> parseJsonElement(value)
            is kotlinx.serialization.json.JsonArray -> value.map { parseJsonValue(it) }
            is kotlinx.serialization.json.JsonNull -> null
        }
    }

    private fun serialize(payload: Map<String, Any?>): String {
        return json.encodeToString(mapToJsonElement(payload))
    }

    private fun mapToJsonElement(map: Map<String, Any?>): kotlinx.serialization.json.JsonElement {
        val jsonMap = map.mapValues { (_, value) ->
            when (value) {
                is String -> kotlinx.serialization.json.JsonPrimitive(value)
                is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val mapValue = value as Map<String, Any?>
                    mapToJsonElement(mapValue)
                }
                null -> kotlinx.serialization.json.JsonNull
                else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
            }
        }
        return kotlinx.serialization.json.JsonObject(jsonMap)
    }
}
