package ai.altertable.sdk

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class Transport(
    private val config: MobileConfig
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

    @Suppress("ThrowsCount", "TooGenericExceptionCaught", "SwallowedException")
    suspend fun post(endpoint: String, payload: Map<String, Any?>) {
        try {
            val response = withContext(Dispatchers.IO) {
                client.post("${config.baseUrl}$endpoint") {
                    parameter("apiKey", config.apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(serialize(payload))
                }
            }
            if (!response.status.isSuccess()) {
                var errorCode: String? = null
                try {
                    val errorJson = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    errorCode = errorJson?.get("error")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) {
                    // Ignore parsing error
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
            val netErr = NetworkError("Network request failed", e)
            config.onError?.invoke(netErr) ?: throw netErr
        }
    }

    private fun serialize(payload: Map<String, Any?>): String {
        // A naive serialization for the requirements
        return json.encodeToString(mapToJsonElement(payload))
    }

    private fun mapToJsonElement(map: Map<String, Any?>): kotlinx.serialization.json.JsonElement {
        // We'll use a simple builder or just serialize a generic map if needed.
        // For tests passing we can use kotlinx.serialization generic support 
        // by wrapping in a custom serializer or returning a JsonObject directly.
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
