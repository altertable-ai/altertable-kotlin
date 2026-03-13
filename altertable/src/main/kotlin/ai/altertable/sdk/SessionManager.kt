package ai.altertable.sdk

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

@OptIn(ExperimentalUuidApi::class, AltertableInternal::class)

internal class SessionManager(
    private val storage: Storage,
    private val apiKey: String,
    private val environment: String,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()
    private val storageKeyPrefix = buildStorageKeyPrefix(apiKey, environment)
    private val sessionIdKey = "$storageKeyPrefix.session_id"
    private val lastEventAtKey = "$storageKeyPrefix.last_event_at"

    private var _sessionId: String? = null
    private var _lastEventAt: Long? = null

    internal val sessionId: String
        get() = _sessionId ?: error("SessionManager not initialized. Call initialize() first.")

    private fun generateSessionId(): String = "${PREFIX_SESSION_ID}-${Uuid.random()}"

    internal suspend fun initialize() {
        mutex.withLock {
            if (_sessionId == null) {
                _sessionId = storage[sessionIdKey] ?: generateSessionId().also {
                    storage[sessionIdKey] = it
                }
            }
            if (_lastEventAt == null) {
                _lastEventAt = storage[lastEventAtKey]?.toLongOrNull() ?: clock.now().toEpochMilliseconds()
            }
        }
    }

    internal suspend fun getSessionIdAndTouch(): String = mutex.withLock {
        val now = clock.now().toEpochMilliseconds()
        val currentLastEventAt = _lastEventAt ?: clock.now().toEpochMilliseconds()
        if (now - currentLastEventAt > SESSION_EXPIRATION_TIME_MS) {
            renewSessionUnsafe()
        }
        _lastEventAt = now
        storage[lastEventAtKey] = now.toString()
        checkNotNull(_sessionId) { "SessionManager not initialized. Call initialize() first." }
    }

    internal suspend fun renewSession() = mutex.withLock {
        renewSessionUnsafe()
    }

    private suspend fun renewSessionUnsafe() {
        _sessionId = generateSessionId()
        storage[sessionIdKey] = checkNotNull(_sessionId) { "sessionId should be set after generateSessionId()" }
        _lastEventAt = clock.now().toEpochMilliseconds()
        storage[lastEventAtKey] = checkNotNull(_lastEventAt) {
            "lastEventAt should be set after clock.now()"
        }.toString()
    }
}
