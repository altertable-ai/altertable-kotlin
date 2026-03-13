package ai.altertable.sdk

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Payload for the /track endpoint.
 *
 * @property timestamp ISO-8601 timestamp
 * @property event Event name
 * @property environment Environment name
 * @property deviceId Device identifier
 * @property distinctId User distinct ID
 * @property anonymousId Anonymous ID before identification
 * @property sessionId Session identifier
 * @property properties Event properties
 * @property release Optional release version
 */
@Serializable
internal data class TrackPayload(
    @Serializable(with = InstantIso8601Serializer::class)
    val timestamp: Instant,
    val event: String,
    val environment: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("distinct_id") val distinctId: String,
    @SerialName("anonymous_id") val anonymousId: String?,
    @SerialName("session_id") val sessionId: String,
    val properties: JsonObject = buildJsonObject {},
    val release: String? = null,
)

/**
 * Payload for the /identify endpoint.
 *
 * @property timestamp ISO-8601 timestamp
 * @property environment Environment name
 * @property deviceId Device identifier
 * @property distinctId User distinct ID
 * @property anonymousId Previous anonymous ID
 * @property traits User traits
 * @property release Optional release version
 */
@Serializable
internal data class IdentifyPayload(
    @Serializable(with = InstantIso8601Serializer::class)
    val timestamp: Instant,
    val environment: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("distinct_id") val distinctId: String,
    @SerialName("anonymous_id") val anonymousId: String?,
    val traits: JsonObject = buildJsonObject {},
    val release: String? = null,
)

/**
 * Payload for the /alias endpoint.
 *
 * @property timestamp ISO-8601 timestamp
 * @property environment Environment name
 * @property deviceId Device identifier
 * @property distinctId Current distinct ID
 * @property anonymousId Anonymous ID if any
 * @property newUserId New user ID to alias
 * @property release Optional release version
 */
@Serializable
internal data class AliasPayload(
    @Serializable(with = InstantIso8601Serializer::class)
    val timestamp: Instant,
    val environment: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("distinct_id") val distinctId: String,
    @SerialName("anonymous_id") val anonymousId: String?,
    @SerialName("new_user_id") val newUserId: String,
    val release: String? = null,
)

internal fun propertiesToJsonObject(properties: Map<String, Any>): JsonObject =
    buildJsonObject {
        for ((key, value) in properties) {
            put(key, valueToJsonElement(value))
        }
    }

private fun valueToJsonElement(value: Any?): JsonElement =
    when (value) {
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            val safeMap =
                value.entries
                    .filter { it.key is String }
                    .associate { it.key as String to it.value }
            JsonObject(safeMap.mapValues { (_, v) -> valueToJsonElement(v) })
        }
        is Collection<*> -> JsonArray(value.map { valueToJsonElement(it) })
        is Array<*> -> JsonArray(value.map { valueToJsonElement(it) })
        null -> JsonNull
        else -> JsonPrimitive(value.toString())
    }

/** Sealed class for events that can be queued or sent. */
@Serializable
internal sealed class ApiPayload {
    internal abstract val endpoint: String

    @Transient
    internal open val body: Any
        get() = error("body must be overridden")

    @Serializable
    @SerialName("track")
    internal data class Track(
        val payload: TrackPayload,
    ) : ApiPayload() {
        override val endpoint: String = "/track"

        @Transient
        override val body: Any get() = payload
    }

    @Serializable
    @SerialName("identify")
    internal data class Identify(
        val payload: IdentifyPayload,
    ) : ApiPayload() {
        override val endpoint: String = "/identify"

        @Transient
        override val body: Any get() = payload
    }

    @Serializable
    @SerialName("alias")
    internal data class Alias(
        val payload: AliasPayload,
    ) : ApiPayload() {
        override val endpoint: String = "/alias"

        @Transient
        override val body: Any get() = payload
    }
}

/**
 * Converts an ApiPayload to a public Event for interceptors.
 */
internal fun ApiPayload.toEvent(): Event =
    when (this) {
        is ApiPayload.Track ->
            Event.Track(
                event = payload.event,
                properties = jsonObjectToMap(payload.properties),
            )
        is ApiPayload.Identify ->
            Event.Identify(
                userId = payload.distinctId,
                traits = jsonObjectToMap(payload.traits),
            )
        is ApiPayload.Alias ->
            Event.Alias(
                newUserId = payload.newUserId,
            )
    }

/**
 * Applies changes from an Event back to an ApiPayload.
 * Returns null if the event should be dropped.
 */
@Suppress("ReturnCount")
internal fun ApiPayload.applyEvent(event: Event?): ApiPayload? {
    if (event == null) return null

    return when (this) {
        is ApiPayload.Track -> {
            val trackEvent = event as? Event.Track ?: return this
            this.copy(
                payload =
                    payload.copy(
                        event = trackEvent.event,
                        properties = propertiesToJsonObject(trackEvent.properties),
                    ),
            )
        }
        is ApiPayload.Identify -> {
            val identifyEvent = event as? Event.Identify ?: return this
            this.copy(
                payload =
                    payload.copy(
                        distinctId = identifyEvent.userId,
                        traits = propertiesToJsonObject(identifyEvent.traits),
                    ),
            )
        }
        is ApiPayload.Alias -> {
            val aliasEvent = event as? Event.Alias ?: return this
            this.copy(
                payload =
                    payload.copy(
                        newUserId = aliasEvent.newUserId,
                    ),
            )
        }
    }
}

/**
 * Converts a JsonObject to a Map<String, Any> for public API.
 */
private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> =
    jsonObject.entries
        .mapNotNull { (key, value) ->
            jsonElementToValue(value)?.let { key to it }
        }.toMap()

/**
 * Converts a JsonElement to a plain Kotlin value.
 */
private fun jsonElementToValue(element: JsonElement): Any? =
    when (element) {
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.content == "true" || element.content == "false" -> element.content.toBoolean()
                element.content.toLongOrNull() != null -> element.content.toLong()
                element.content.toDoubleOrNull() != null -> element.content.toDouble()
                else -> element.content
            }
        }
        is JsonObject -> jsonObjectToMap(element)
        is JsonArray -> element.map { jsonElementToValue(it) }
        is JsonNull -> null
    }
