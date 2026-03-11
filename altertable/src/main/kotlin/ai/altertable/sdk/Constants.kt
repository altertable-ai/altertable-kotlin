package ai.altertable.sdk

internal const val STORAGE_KEY_PREFIX = "atbl"
internal const val STORAGE_KEY_SEPARATOR = "."
internal const val PREFIX_SESSION_ID = "session"
internal const val PREFIX_ANONYMOUS_ID = "anonymous"
internal const val PREFIX_DEVICE_ID = "device"
internal const val STORAGE_KEY_QUEUE = "queue"
internal const val STORAGE_KEY_TRACKING_CONSENT = "tracking_consent"
internal const val SESSION_EXPIRATION_TIME_MS = 1_800_000L

// Events
internal const val EVENT_SCREEN_VIEW = "\$screen"

internal const val MAX_USER_ID_LENGTH = 1024

internal val RESERVED_USER_IDS: Set<String> =
    setOf(
        "anonymous_id",
        "anonymous",
        "distinct_id",
        "distinctid",
        "false",
        "guest",
        "id",
        "not_authenticated",
        "true",
        "undefined",
        "user_id",
        "user",
        "visitor_id",
        "visitor",
    )

internal val RESERVED_USER_IDS_CASE_SENSITIVE: Set<String> =
    setOf(
        "[object Object]",
        "0",
        "NaN",
        "none",
        "None",
        "null",
    )

internal fun buildStorageKeyPrefix(
    apiKey: String,
    environment: String,
): String = "$STORAGE_KEY_PREFIX$STORAGE_KEY_SEPARATOR$apiKey$STORAGE_KEY_SEPARATOR$environment"
