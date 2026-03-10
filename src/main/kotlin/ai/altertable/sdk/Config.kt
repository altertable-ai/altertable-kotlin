package ai.altertable.sdk

/**
 * Represents the current tracking consent state.
 *
 * @param value The string representation of the state.
 */
enum class TrackingConsentState(val value: String) {
    /** Consent has been explicitly granted. */
    GRANTED("granted"),
    /** Consent has been explicitly denied. */
    DENIED("denied"),
    /** Consent is pending user decision. */
    PENDING("pending"),
    /** Consent dialog was dismissed without decision. */
    DISMISSED("dismissed")
}

/**
 * Base exception for all Altertable-related errors.
 *
 * @param message The detailed error message.
 * @param cause The underlying cause of the error.
 */
open class AltertableError(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Represents an error returned by the Altertable API.
 *
 * @param status The HTTP status code.
 * @param statusText The HTTP status text.
 * @param errorCode The optional API-specific error code.
 * @param details Additional error details provided by the API.
 * @param message The detailed error message.
 * @param cause The underlying cause of the error.
 */
class ApiError(
    val status: Int,
    val statusText: String,
    val errorCode: String?,
    val details: Map<String, Any>?,
    message: String,
    cause: Throwable? = null
) : AltertableError(message, cause)

/**
 * Represents a network-related error.
 *
 * @param message The detailed error message.
 * @param cause The underlying cause of the network error.
 */
class NetworkError(message: String, cause: Throwable) : AltertableError(message, cause)

/**
 * Configuration for the [AltertableClient].
 *
 * @param apiKey The Altertable API key.
 * @param baseUrl The base URL for the Altertable API.
 * @param environment The environment name (e.g., "production", "development").
 * @param trackingConsent The initial tracking consent state.
 * @param release The optional release version of the application.
 * @param onError An optional callback for handling [AltertableError]s.
 * @param debug Whether to enable debug logging.
 * @param requestTimeout The request timeout in milliseconds.
 * @param flushOnBackground Whether to flush pending events when the app goes into the background.
 */
data class AltertableConfig(
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
        /**
         * Default configuration for mobile applications.
         */
        val MOBILE_DEFAULTS = AltertableConfig(
            apiKey = "" // Must be provided
        )
    }
}
