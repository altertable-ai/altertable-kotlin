package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    private var deviceIdValue: String? = null
    private var distinctIdValue: String? = null
    private var anonymousIdValue: String? = null

    internal val deviceId: String
        get() = deviceIdValue ?: error("IdentityManager not initialized. Call initialize() first.")

    internal val distinctId: String
        get() = distinctIdValue ?: error("IdentityManager not initialized. Call initialize() first.")

    internal val anonymousId: String?
        get() = anonymousIdValue

    private fun generateId(prefix: String): String = "$prefix-${Uuid.random()}"

    private fun isReservedId(userId: String): Boolean =
        RESERVED_USER_IDS_CASE_SENSITIVE.contains(userId) ||
            RESERVED_USER_IDS.contains(userId.lowercase())

    internal suspend fun initialize() {
        mutex.withLock {
            if (deviceIdValue == null) {
                deviceIdValue = storage[deviceIdKey] ?: generateId(PREFIX_DEVICE_ID).also {
                    storage[deviceIdKey] = it
                }
            }
            if (distinctIdValue == null) {
                distinctIdValue = storage[distinctIdKey] ?: generateId(PREFIX_ANONYMOUS_ID).also {
                    storage[distinctIdKey] = it
                }
            }
            if (anonymousIdValue == null) {
                anonymousIdValue = storage[anonymousIdKey]
            }
        }
    }

    internal suspend fun identify(userId: String) {
        mutex.withLock {
            if (isReservedId(userId)) return
            if (userId == distinctIdValue) return

            val currentDistinctId =
                checkNotNull(distinctIdValue) {
                    "IdentityManager not initialized. Call initialize() first."
                }
            if (!currentDistinctId.startsWith("${PREFIX_ANONYMOUS_ID}-")) {
                resetUnsafe(resetDeviceId = false)
            }

            anonymousIdValue = distinctIdValue
            distinctIdValue = userId

            storage[anonymousIdKey] = checkNotNull(anonymousIdValue) { "anonymousId should be set after identify()" }
            storage[distinctIdKey] = checkNotNull(distinctIdValue) { "distinctId should be set to userId" }
        }
    }

    internal suspend fun reset(resetDeviceId: Boolean = false) {
        mutex.withLock {
            resetUnsafe(resetDeviceId)
        }
    }

    private suspend fun resetUnsafe(resetDeviceId: Boolean = false) {
        if (resetDeviceId) {
            deviceIdValue = generateId(PREFIX_DEVICE_ID)
            storage[deviceIdKey] = checkNotNull(deviceIdValue) { "deviceId should be set after generateId()" }
        }

        distinctIdValue = generateId(PREFIX_ANONYMOUS_ID)
        anonymousIdValue = null

        storage[distinctIdKey] = checkNotNull(distinctIdValue) { "distinctId should be set after generateId()" }
        storage.remove(anonymousIdKey)
    }
}
