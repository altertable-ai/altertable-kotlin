package ai.altertable.sdk

/**
 * Validates user IDs for identify and alias calls.
 */
internal fun validateUserId(userId: String): Result<Unit> {
    val trimmed = userId.trim()

    val error =
        when {
            trimmed.isEmpty() -> AltertableError.Validation("User ID must not be blank.")
            RESERVED_USER_IDS_CASE_SENSITIVE.contains(trimmed) ->
                AltertableError.Validation("User ID \"$trimmed\" is reserved. Choose a different ID.")
            RESERVED_USER_IDS.contains(trimmed.lowercase()) ->
                AltertableError.Validation("User ID \"$trimmed\" is reserved. Choose a different ID.")
            trimmed.length > MAX_USER_ID_LENGTH ->
                AltertableError.Validation("User ID must be $MAX_USER_ID_LENGTH characters or less.")
            else -> null
        }

    return error?.let { Result.failure(AltertableException(it)) } ?: Result.success(Unit)
}
