package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class QueuedEvent(
    val endpoint: String,
    val payloadJson: String
)

class EventQueue(
    private val maxSize: Int = Constants.MAX_QUEUE_SIZE,
    private val storage: QueueStorage? = null
) {
    private val queue = mutableListOf<QueuedEvent>()
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun enqueue(event: Map<String, Any?>) {
        mutex.withLock {
            if (queue.size >= maxSize) {
                queue.removeAt(0)
            }
            val endpoint = event["_endpoint"] as? String ?: "/track"
            val payload = event.filterKeys { it != "_endpoint" }
            val payloadJson = json.encodeToString(serializeToJsonElement(payload))
            queue.add(QueuedEvent(endpoint, payloadJson))
            saveToDisk()
        }
    }

    suspend fun flush(): List<QueuedEvent> {
        return mutex.withLock {
            val items = queue.toList()
            queue.clear()
            saveToDisk()
            items
        }
    }

    suspend fun clear() {
        mutex.withLock {
            queue.clear()
            saveToDisk()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun loadFromDisk() {
        mutex.withLock {
            storage?.let { storage ->
                try {
                    val data = storage.load()
                    if (data.isNotEmpty()) {
                        queue.clear()
                        queue.addAll(data)
                    }
                } catch (e: Exception) {
                    // Ignore load errors, start fresh
                }
            }
        }
    }

    private fun saveToDisk() {
        storage?.save(queue.toList())
    }

    private fun serializeToJsonElement(map: Map<String, Any?>): kotlinx.serialization.json.JsonElement {
        val jsonMap = map.mapValues { (_, value) ->
            when (value) {
                is String -> kotlinx.serialization.json.JsonPrimitive(value)
                is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    serializeToJsonElement(value as Map<String, Any?>)
                }
                null -> kotlinx.serialization.json.JsonNull
                else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
            }
        }
        return kotlinx.serialization.json.JsonObject(jsonMap)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun deserializePayload(payloadJson: String): Map<String, Any?> {
        return try {
            val element = json.parseToJsonElement(payloadJson)
            parseJsonElement(element)
        } catch (e: Exception) {
            emptyMap()
        }
    }

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
}

class QueueStorage(private val directory: File) {
    private val file = File(directory, "altertable_queue.json")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun save(queue: List<QueuedEvent>) {
        try {
            val data = json.encodeToString(queue)
            file.writeText(data)
        } catch (e: Exception) {
            // Log error in production
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun load(): List<QueuedEvent> {
        return try {
            if (file.exists()) {
                val data = file.readText()
                if (data.isNotBlank()) {
                    json.decodeFromString<List<QueuedEvent>>(data)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
