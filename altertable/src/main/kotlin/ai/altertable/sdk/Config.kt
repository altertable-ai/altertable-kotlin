package ai.altertable.sdk

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the current tracking consent state.
 */
public enum class TrackingConsent {
    /** Consent has been explicitly granted. */
    GRANTED,

    /** Consent has been explicitly denied. */
    DENIED,

    /** Consent is pending user decision. */
    PENDING,

    /** Consent dialog was dismissed without decision. */
    DISMISSED,
}

/**
 * Base error type for all Altertable-related errors.
 *
 * Sealed class hierarchy enables exhaustive `when` matching.
 * This is a data type, not an exception. To throw, wrap in [AltertableException].
 */
public sealed class AltertableError(
    public val message: String,
    public val cause: Throwable? = null,
) {
    /**
     * Represents a validation error (e.g., invalid user ID, blank event name).
     */
    public class Validation(
        message: String,
    ) : AltertableError(message)

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
    public class Api(
        public val status: Int,
        public val statusText: String,
        public val errorCode: String?,
        public val details: Map<String, Any>?,
        message: String,
        cause: Throwable? = null,
    ) : AltertableError(message, cause)

    /**
     * Represents a network-related error.
     *
     * @param message The detailed error message.
     * @param cause The underlying cause of the network error.
     */
    public class Network(
        message: String,
        cause: Throwable,
    ) : AltertableError(message, cause)
}

/**
 * Exception wrapper for [AltertableError] when throwing is required.
 *
 * Use this when you need to throw an [AltertableError] in exception-based error handling.
 */
public class AltertableException(
    public val error: AltertableError,
) : Exception(error.message, error.cause)

/**
 * Network configuration for the Altertable SDK.
 *
 * @param baseUrl The base URL for the Altertable API.
 * @param requestTimeout The request timeout for network requests.
 * @param maxRetries Number of retries after the first attempt for transient failures (network, 429, 5xx).
 *   With the default of `3`, the client performs up to four HTTP attempts total, matching the JS SDK.
 */
public data class NetworkConfig(
    public val baseUrl: String = "https://api.altertable.ai",
    public val requestTimeout: Duration = 10.seconds,
    public val maxRetries: Int = 3,
)

/**
 * Tracking configuration for the Altertable SDK.
 *
 * @param consent The initial tracking consent state.
 * @param captureScreenViews Whether to enable screen view auto-capture (Android only).
 * @param flushOnBackground Whether to flush pending events when the app goes into the background.
 * @param maxQueueSize Maximum events to hold in the queue (older events dropped when exceeded).
 * @param flushEventThreshold Flush outbound batches when the combined buffered event count reaches this value.
 * @param flushIntervalMs Periodic flush interval for outbound batches (milliseconds).
 * @param maxBatchSize Maximum payloads per HTTP request per endpoint ([/track], [/identify], [/alias]).
 */
public data class TrackingConfig(
    public val consent: TrackingConsent = TrackingConsent.GRANTED,
    public val captureScreenViews: Boolean = true,
    public val flushOnBackground: Boolean = true,
    public val maxQueueSize: Int = 1_000,
    public val flushEventThreshold: Int = 20,
    public val flushIntervalMs: Long = 5_000L,
    public val maxBatchSize: Int = 20,
)

/**
 * Configuration for the Altertable SDK client.
 *
 * @param apiKey The Altertable API key.
 * @param environment The environment name (e.g., "production", "development").
 * @param network Network configuration.
 * @param tracking Tracking configuration.
 * @param release The optional release version of the application.
 * @param debug Whether to enable debug logging.
 * @param dispatcher Coroutine dispatcher for async operations (inject for deterministic tests).
 * @param logger Optional logger for SDK events (inject for testing or custom logging).
 * @param integrations Integrations to install on setup (e.g., lifecycle tracking, screen views).
 * @param beforeSend Hooks to transform or filter events before they are sent. Return null to drop.
 */
@Suppress("LongParameterList")
public class AltertableConfig(
    public val apiKey: String,
    public val environment: String = "production",
    public val network: NetworkConfig = NetworkConfig(),
    public val tracking: TrackingConfig = TrackingConfig(),
    public val release: String? = null,
    public val debug: Boolean = false,
    public val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    public val logger: AltertableLogger? = null,
    public val integrations: List<AltertableIntegration> = emptyList(),
    public val beforeSend: List<EventInterceptor> = emptyList(),
)
