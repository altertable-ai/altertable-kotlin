package ai.altertable.sdk

import kotlin.annotation.AnnotationTarget.CLASS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * DSL marker annotation to prevent scope leaking in nested DSL builders.
 */
@DslMarker
@Target(CLASS)
internal annotation class AltertableDsl

/**
 * DSL builder for [AltertableConfig].
 *
 * Use with [Altertable.setup] or [Altertable.create] for idiomatic Kotlin setup:
 *
 * ```
 * Altertable.setup {
 *     apiKey = "your-api-key"
 *     environment = "production"
 *     debug = true
 * }
 * ```
 */
@AltertableDsl
public class NetworkConfigBuilder {
    public var baseUrl: String = "https://api.altertable.ai"
    public var requestTimeout: Duration = 10.seconds
    public var maxRetries: Int = 3

    internal fun build(): NetworkConfig = NetworkConfig(
        baseUrl = baseUrl,
        requestTimeout = requestTimeout,
        maxRetries = maxRetries,
    )

    internal fun from(config: NetworkConfig): NetworkConfigBuilder {
        baseUrl = config.baseUrl
        requestTimeout = config.requestTimeout
        maxRetries = config.maxRetries
        return this
    }
}

@AltertableDsl
public class TrackingConfigBuilder {
    public var consent: TrackingConsent = TrackingConsent.GRANTED
    public var captureScreenViews: Boolean = true
    public var flushOnBackground: Boolean = true
    public var maxQueueSize: Int = 1_000

    internal fun build(): TrackingConfig = TrackingConfig(
        consent = consent,
        captureScreenViews = captureScreenViews,
        flushOnBackground = flushOnBackground,
        maxQueueSize = maxQueueSize,
    )

    internal fun from(config: TrackingConfig): TrackingConfigBuilder {
        consent = config.consent
        captureScreenViews = config.captureScreenViews
        flushOnBackground = config.flushOnBackground
        maxQueueSize = config.maxQueueSize
        return this
    }
}

/**
 * Builder for runtime configuration changes via [Altertable.configure].
 *
 * Only exposes options that are safe to change at runtime:
 * - Tracking consent
 * - Debug logging
 * - Logger instance
 * - Event interceptors (beforeSend)
 *
 * Changing `apiKey`, `environment`, `dispatcher`, or `network` settings requires
 * creating a new client instance.
 */
@AltertableDsl
public class RuntimeConfigBuilder {
    public var debug: Boolean = false
    public var logger: AltertableLogger? = null
    public var beforeSend: List<EventInterceptor> = emptyList()

    private val trackingBuilder = TrackingConfigBuilder()

    public fun tracking(block: TrackingConfigBuilder.() -> Unit) {
        trackingBuilder.block()
    }

    internal fun applyTo(config: AltertableConfig): AltertableConfig {
        return AltertableConfig(
            apiKey = config.apiKey,
            environment = config.environment,
            network = config.network,
            tracking = trackingBuilder.build(),
            release = config.release,
            debug = debug,
            dispatcher = config.dispatcher,
            logger = logger,
            integrations = config.integrations,
            beforeSend = beforeSend,
        )
    }

    internal fun from(config: AltertableConfig): RuntimeConfigBuilder {
        debug = config.debug
        logger = config.logger
        beforeSend = config.beforeSend
        trackingBuilder.from(config.tracking)
        return this
    }
}

@AltertableDsl
public class AltertableConfigBuilder {
    public var apiKey: String = ""
    public var environment: String = "production"
    public var release: String? = null
    public var debug: Boolean = false
    public var dispatcher: CoroutineDispatcher = Dispatchers.IO
    public var logger: AltertableLogger? = null
    public var integrations: List<AltertableIntegration> = emptyList()
    public var beforeSend: List<EventInterceptor> = emptyList()

    private val networkBuilder = NetworkConfigBuilder()
    private val trackingBuilder = TrackingConfigBuilder()

    public fun network(block: NetworkConfigBuilder.() -> Unit) {
        networkBuilder.block()
    }

    public fun tracking(block: TrackingConfigBuilder.() -> Unit) {
        trackingBuilder.block()
    }

    @AltertableInternal
    public fun build(): AltertableConfig {
        return AltertableConfig(
            apiKey = apiKey,
            environment = environment,
            network = networkBuilder.build(),
            tracking = trackingBuilder.build(),
            release = release,
            debug = debug,
            dispatcher = dispatcher,
            logger = logger,
            integrations = integrations,
            beforeSend = beforeSend,
        )
    }

    /**
     * Seeds this builder with values from an existing [AltertableConfig].
     */
    @AltertableInternal
    public fun from(config: AltertableConfig): AltertableConfigBuilder {
        apiKey = config.apiKey
        environment = config.environment
        release = config.release
        debug = config.debug
        dispatcher = config.dispatcher
        logger = config.logger
        integrations = config.integrations
        beforeSend = config.beforeSend
        networkBuilder.from(config.network)
        trackingBuilder.from(config.tracking)
        return this
    }
}
