package ai.altertable.sdk

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalUuidApi::class, AltertableInternal::class)

internal class IdentityManager(
    private val storage: Storage,
    private val apiKey: String,
    private val environment: String,
) {
    private val mutex = Mutex()
    private val storageKeyPrefix = buildStorageKeyPrefix(apiKey, environment)
    private val deviceIdKey = "$storageKeyPrefix.device_id"
    private val distinctIdKey = "$storageKeyPrefix.distinct_id"
    private val anonymousIdKey = "$storageKeyPrefix.anonymous_id"

    private var _deviceId: String? = null
    private var _distinctId: String? = null
    private var _anonymousId: String? = null

    internal val deviceId: String
        get() = _deviceId ?: error("IdentityManager not initialized. Call initialize() first.")

    internal val distinctId: String
        get() = _distinctId ?: error("IdentityManager not initialized. Call initialize() first.")

    internal val anonymousId: String?
        get() = _anonymousId

    private fun generateId(prefix: String): String = "$prefix-${Uuid.random()}"

    private fun isReservedId(userId: String): Boolean =
        RESERVED_USER_IDS_CASE_SENSITIVE.contains(userId) ||
            RESERVED_USER_IDS.contains(userId.lowercase())

    internal suspend fun initialize() {
        mutex.withLock {
            if (_deviceId == null) {
                _deviceId = storage[deviceIdKey] ?: generateId(PREFIX_DEVICE_ID).also {
                    storage[deviceIdKey] = it
                }
            }
            if (_distinctId == null) {
                _distinctId = storage[distinctIdKey] ?: generateId(PREFIX_ANONYMOUS_ID).also {
                    storage[distinctIdKey] = it
                }
            }
            if (_anonymousId == null) {
                _anonymousId = storage[anonymousIdKey]
            }
        }
    }

    internal suspend fun identify(userId: String) {
        mutex.withLock {
            if (isReservedId(userId)) return
            if (userId == _distinctId) return

            val currentDistinctId = checkNotNull(_distinctId) { "IdentityManager not initialized. Call initialize() first." }
            if (!currentDistinctId.startsWith("${PREFIX_ANONYMOUS_ID}-")) {
                resetUnsafe(resetDeviceId = false)
            }

            _anonymousId = _distinctId
            _distinctId = userId

            storage[anonymousIdKey] = checkNotNull(_anonymousId) { "anonymousId should be set after identify()" }
            storage[distinctIdKey] = checkNotNull(_distinctId) { "distinctId should be set to userId" }
        }
    }

    internal suspend fun reset(resetDeviceId: Boolean = false) {
        mutex.withLock {
            resetUnsafe(resetDeviceId)
        }
    }

    private suspend fun resetUnsafe(resetDeviceId: Boolean = false) {
        if (resetDeviceId) {
            _deviceId = generateId(PREFIX_DEVICE_ID)
            storage[deviceIdKey] = checkNotNull(_deviceId) { "deviceId should be set after generateId()" }
        }

        _distinctId = generateId(PREFIX_ANONYMOUS_ID)
        _anonymousId = null

        storage[distinctIdKey] = checkNotNull(_distinctId) { "distinctId should be set after generateId()" }
        storage.remove(anonymousIdKey)
    }
}
