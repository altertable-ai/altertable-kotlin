package ai.altertable.sdk

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists and loads the event queue for consent-pending scenarios.
 *
 * When consent is PENDING or DISMISSED, events are queued and persisted
 * so they survive app restarts. Uses [Storage] for storage.
 */
@OptIn(AltertableInternal::class)
internal class QueueStorage(
    private val storage: Storage,
    private val storageKeyPrefix: String,
) {
    private val queueKey = "$storageKeyPrefix${STORAGE_KEY_SEPARATOR}${STORAGE_KEY_QUEUE}"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    suspend fun save(queue: List<ApiPayload>) {
        try {
            val jsonString = json.encodeToString(queue)
            storage[queueKey] = jsonString
        } catch (_: SerializationException) {
            // Ignore serialization failures - queue will remain in memory
        }
    }

    suspend fun load(): List<ApiPayload> {
        return try {
            val jsonString = storage[queueKey] ?: return emptyList()
            json.decodeFromString<List<ApiPayload>>(jsonString)
        } catch (_: SerializationException) {
            emptyList()
        }
    }
}
