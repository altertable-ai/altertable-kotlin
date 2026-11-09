package ai.altertable.sdk

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URISyntaxException

private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443

/** Final collection policy, applied after caller interceptors. Custom properties remain caller-owned. */
@OptIn(AltertableInternal::class)
internal fun prepareAudienceEvent(payload: TrackPayload): TrackPayload {
    val properties = payload.properties.toMutableMap()
    properties["\$url"] = sanitizeAudienceUrl(properties["\$url"] as? JsonPrimitive)
    properties["\$referer"] = sanitizeAudienceUrl(properties["\$referer"] as? JsonPrimitive, originOnly = true)
    properties.remove(ContextProperties.VIEWPORT)
    return payload.copy(
        mode = "audience",
        environment = null,
        deviceId = null,
        distinctId = null,
        anonymousId = null,
        sessionId = null,
        release = null,
        properties = JsonObject(properties),
    )
}

private fun sanitizeAudienceUrl(
    value: JsonPrimitive?,
    originOnly: Boolean = false,
): JsonElement {
    val text = value?.takeIf { it.isString }?.contentOrNull ?: return JsonNull
    return try {
        val uri = URI(text)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || host == null) {
            JsonNull
        } else {
            val port =
                uri.port.takeUnless {
                    it == -1 ||
                        (scheme == "http" && it == HTTP_DEFAULT_PORT) ||
                        (scheme == "https" && it == HTTPS_DEFAULT_PORT)
                }
            val origin = "$scheme://$host" + (port?.let { ":$it" } ?: "")
            JsonPrimitive(origin + if (originOnly) "" else uri.rawPath?.ifEmpty { "/" }.orEmpty())
        }
    } catch (_: URISyntaxException) {
        JsonNull
    }
}
