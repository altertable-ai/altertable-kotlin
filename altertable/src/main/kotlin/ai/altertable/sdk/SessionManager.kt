package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    private var sessionIdValue: String? = null
    private var lastEventAtValue: Long? = null

    internal val sessionId: String
        get() = sessionIdValue ?: error("SessionManager not initialized. Call initialize() first.")

    private fun generateSessionId(): String = "${PREFIX_SESSION_ID}-${Uuid.random()}"

    internal suspend fun initialize() {
        mutex.withLock {
            if (sessionIdValue == null) {
                sessionIdValue = storage[sessionIdKey] ?: generateSessionId().also {
                    storage[sessionIdKey] = it
                }
            }
            if (lastEventAtValue == null) {
                lastEventAtValue = storage[lastEventAtKey]?.toLongOrNull() ?: clock.now().toEpochMilliseconds()
            }
        }
    }

    internal suspend fun getSessionIdAndTouch(): String =
        mutex.withLock {
            val now = clock.now().toEpochMilliseconds()
            val currentLastEventAt = lastEventAtValue ?: clock.now().toEpochMilliseconds()
            if (now - currentLastEventAt > SESSION_EXPIRATION_TIME_MS) {
                renewSessionUnsafe()
            }
            lastEventAtValue = now
            storage[lastEventAtKey] = now.toString()
            checkNotNull(sessionIdValue) { "SessionManager not initialized. Call initialize() first." }
        }

    internal suspend fun renewSession() =
        mutex.withLock {
            renewSessionUnsafe()
        }

    private suspend fun renewSessionUnsafe() {
        sessionIdValue = generateSessionId()
        storage[sessionIdKey] = checkNotNull(sessionIdValue) { "sessionId should be set after generateSessionId()" }
        lastEventAtValue = clock.now().toEpochMilliseconds()
        storage[lastEventAtKey] =
            checkNotNull(lastEventAtValue) {
                "lastEventAt should be set after clock.now()"
            }.toString()
    }
}
