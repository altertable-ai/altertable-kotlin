package ai.altertable.sdk

enum class TrackingConsentState(val value: String) {
    GRANTED("granted"),
    DENIED("denied"),
    PENDING("pending"),
    DISMISSED("dismissed")
}

class AltertableError(message: String, cause: Throwable? = null) : Exception(message, cause)

data class MobileConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.altertable.ai",
    val environment: String = "production",
    val trackingConsent: TrackingConsentState = TrackingConsentState.GRANTED,
    val release: String? = null,
    val onError: ((AltertableError) -> Unit)? = null,
    val debug: Boolean = false,
    val requestTimeout: Long = Constants.MOBILE_REQUEST_TIMEOUT_MS,
    val flushOnBackground: Boolean = true
) {
    companion object {
        val MOBILE_DEFAULTS = MobileConfig(
            apiKey = "" // Must be provided
        )
    }
}
