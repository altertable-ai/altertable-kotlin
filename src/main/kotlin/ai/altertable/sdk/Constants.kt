package ai.altertable.sdk

object Constants {
    const val STORAGE_KEY_PREFIX = "atbl"
    const val STORAGE_KEY_SEPARATOR = "."
    const val STORAGE_KEY_TEST = "atbl.check"

    const val PREFIX_SESSION_ID = "session"
    const val PREFIX_ANONYMOUS_ID = "anonymous"
    const val PREFIX_DEVICE_ID = "device"

    const val AUTO_CAPTURE_INTERVAL_MS = 100L
    const val SESSION_EXPIRATION_TIME_MS = 1_800_000L
    const val MAX_QUEUE_SIZE = 1_000
    const val REQUEST_TIMEOUT_MS = 5_000L
    const val MOBILE_REQUEST_TIMEOUT_MS = 10_000L

    const val EVENT_PAGEVIEW = "\$pageview"
    const val PROPERTY_LIB = "\$lib"
    const val PROPERTY_LIB_VERSION = "\$lib_version"
    const val PROPERTY_REFERER = "\$referer"
    const val PROPERTY_RELEASE = "\$release"
    const val PROPERTY_URL = "\$url"
    const val PROPERTY_VIEWPORT = "\$viewport"

    val RESERVED_USER_IDS = setOf(
        "anonymous_id", "anonymous", "distinct_id", "distinctid", "false", "guest",
        "id", "not_authenticated", "true", "undefined", "user_id", "user",
        "visitor_id", "visitor"
    )

    val RESERVED_USER_IDS_CASE_SENSITIVE = setOf(
        "[object Object]", "0", "NaN", "none", "None", "null"
    )
}
