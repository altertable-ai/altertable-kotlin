package ai.altertable.sdk

import java.util.UUID

class IdentityManager(
    private val storage: StorageApi,
    private val apiKey: String,
    private val environment: String
) {
    private val storageKeyPrefix = "${Constants.STORAGE_KEY_PREFIX}${Constants.STORAGE_KEY_SEPARATOR}$apiKey${Constants.STORAGE_KEY_SEPARATOR}$environment"
    private val deviceIdKey = "$storageKeyPrefix.device_id"
    private val distinctIdKey = "$storageKeyPrefix.distinct_id"
    private val anonymousIdKey = "$storageKeyPrefix.anonymous_id"

    var deviceId: String = storage.getItem(deviceIdKey) ?: generateId(Constants.PREFIX_DEVICE_ID).also {
        storage.setItem(deviceIdKey, it)
    }
        private set

    var distinctId: String = storage.getItem(distinctIdKey) ?: generateId(Constants.PREFIX_ANONYMOUS_ID).also {
        storage.setItem(distinctIdKey, it)
    }
        private set

    var anonymousId: String? = storage.getItem(anonymousIdKey)
        private set

    private fun generateId(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }

    private fun isReservedId(userId: String): Boolean {
        if (Constants.RESERVED_USER_IDS_CASE_SENSITIVE.contains(userId)) return true
        if (Constants.RESERVED_USER_IDS.contains(userId.lowercase())) return true
        return false
    }

    fun identify(userId: String) {
        if (isReservedId(userId)) return
        if (userId == distinctId) return

        if (!distinctId.startsWith("${Constants.PREFIX_ANONYMOUS_ID}-")) {
            reset(resetDeviceId = false)
        }

        anonymousId = distinctId
        distinctId = userId

        storage.setItem(anonymousIdKey, anonymousId!!)
        storage.setItem(distinctIdKey, distinctId)
    }

    fun reset(resetDeviceId: Boolean = false) {
        if (resetDeviceId) {
            deviceId = generateId(Constants.PREFIX_DEVICE_ID)
            storage.setItem(deviceIdKey, deviceId)
        }
        
        distinctId = generateId(Constants.PREFIX_ANONYMOUS_ID)
        anonymousId = null
        
        storage.setItem(distinctIdKey, distinctId)
        storage.removeItem(anonymousIdKey)
    }
}
