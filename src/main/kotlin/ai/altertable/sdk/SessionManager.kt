package ai.altertable.sdk

import java.util.UUID

class SessionManager(
    private val storage: StorageApi,
    private val apiKey: String,
    private val environment: String
) {
    private val storageKeyPrefix = "${Constants.STORAGE_KEY_PREFIX}" +
        "${Constants.STORAGE_KEY_SEPARATOR}$apiKey" +
        "${Constants.STORAGE_KEY_SEPARATOR}$environment"
    private val sessionIdKey = "$storageKeyPrefix.session_id"
    private val lastEventAtKey = "$storageKeyPrefix.last_event_at"

    var sessionId: String = storage.getItem(sessionIdKey) ?: generateSessionId().also {
        storage.setItem(sessionIdKey, it)
    }
        private set

    private var lastEventAt: Long = storage.getItem(lastEventAtKey)?.toLongOrNull() ?: System.currentTimeMillis()

    private fun generateSessionId(): String {
        return "${Constants.PREFIX_SESSION_ID}-${UUID.randomUUID()}"
    }

    fun getSessionIdAndTouch(): String {
        val now = System.currentTimeMillis()
        if (now - lastEventAt > Constants.SESSION_EXPIRATION_TIME_MS) {
            renewSession()
        }
        lastEventAt = now
        storage.setItem(lastEventAtKey, now.toString())
        return sessionId
    }

    fun renewSession() {
        sessionId = generateSessionId()
        storage.setItem(sessionIdKey, sessionId)
        lastEventAt = System.currentTimeMillis()
        storage.setItem(lastEventAtKey, lastEventAt.toString())
    }
}
