package ai.altertable.sdk

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import kotlin.RequiresOptIn
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.jvm.JvmStatic

/**
 * Marks APIs that are internal to the Altertable SDK.
 *
 * These APIs are exposed for use by sibling modules (e.g., altertable-android)
 * but should not be used by external consumers.
 */
@RequiresOptIn(message = "This is internal to the Altertable SDK.")
@Retention(BINARY)
public annotation class AltertableInternal

/**
 * Public interface for the Altertable SDK client.
 *
 * Enables the instance pattern ([Altertable.create]) and testability
 * by allowing consumers to depend on the interface rather than the concrete implementation.
 */
public interface Altertable : Closeable {
    /**
     * Tracks an event with the given name and optional properties.
     */
    public fun track(
        event: String,
        properties: Map<String, Any> = emptyMap(),
    )

    /**
     * Identifies a user with a unique ID and optional traits.
     *
     * @param userId The unique identifier for the user.
     * @param traits User properties (e.g., email, name). Default is empty.
     */
    public fun identify(
        userId: String,
        traits: Map<String, Any> = emptyMap(),
    )

    /**
     * Updates user traits for the current identified user.
     * Must call [identify] first.
     *
     * @param traits User properties to update.
     */
    public fun updateTraits(traits: Map<String, Any>)

    /**
     * Tracks a screen view event.
     *
     * @param name The screen name.
     * @param properties Additional properties. Default is empty.
     */
    public fun screen(
        name: String,
        properties: Map<String, Any> = emptyMap(),
    )

    /**
     * Associates a new user ID with the existing user ID.
     */
    public fun alias(newUserId: String)

    /**
     * Resets the client state, clearing the current user identity and session.
     *
     * @param resetDeviceId If true, generates a new device ID.
     * @param resetTrackingConsent If true, resets consent to [TrackingConsent.GRANTED].
     */
    public fun reset(
        resetDeviceId: Boolean = false,
        resetTrackingConsent: Boolean = false,
    )

    /**
     * Updates the client's configuration dynamically.
     *
     * Only safe-to-change-at-runtime options are available:
     * - Tracking consent (via `tracking { consent = ... }`)
     * - Debug logging (`debug = true/false`)
     * - Logger instance (`logger = ...`)
     * - Event interceptors (`beforeSend = listOf(...)`)
     *
     * Changing `apiKey`, `environment`, `dispatcher`, or network settings requires
     * creating a new client instance.
     *
     * @param block Lambda that modifies the runtime configuration builder.
     *
     * Example:
     * ```
     * client.configure {
     *     tracking { consent = TrackingConsent.GRANTED }
     *     debug = true
     * }
     * ```
     */
    public fun configure(block: RuntimeConfigBuilder.() -> Unit)

    /**
     * The current tracking consent state as a reactive [StateFlow].
     *
     * Use this to observe consent changes in Compose or with lifecycle-aware components.
     */
    public val trackingConsent: StateFlow<TrackingConsent>

    /**
     * A reactive [SharedFlow] of errors that occur during SDK operations.
     *
     * Use this to observe errors in a reactive way, for example in Compose:
     * ```
     * LaunchedEffect(Unit) {
     *     client.errors.collect { error ->
     *         // Handle error
     *     }
     * }
     * ```
     */
    public val errors: SharedFlow<AltertableError>

    /**
     * Force-flushes any pending events in the queue.
     *
     * This is a fire-and-forget operation that does not block the calling thread.
     * Use [awaitFlush] if you need to await completion.
     */
    public fun flush()

    /**
     * Force-flushes any pending events in the queue and suspends until completion.
     *
     * Use this when you need to ensure events are sent before proceeding (e.g., before app termination).
     * Do not call from the main thread on Android.
     */
    public suspend fun awaitFlush()

    /**
     * Super properties that will be appended to every subsequent event.
     *
     * Use map-like syntax: `client.superProperties["key"] = value` or `client.superProperties.remove("key")`
     */
    public val superProperties: SuperProperties

    public companion object {
        /**
         * Initializes and returns the shared [Altertable] instance using a DSL builder.
         *
         * @param block The configuration DSL block.
         * @return The configured [Altertable] instance.
         */
        @JvmStatic
        public fun setup(block: AltertableConfigBuilder.() -> Unit): Altertable {
            @OptIn(AltertableInternal::class)
            val config = AltertableConfigBuilder().apply(block).build()
            @OptIn(AltertableInternal::class)
            return setup(config)
        }

        /**
         * Initializes and returns the shared [Altertable] instance.
         *
         * @param config The [AltertableConfig] to configure the client with.
         * @param storage Custom [Storage] implementation for persisting SDK state. Defaults to in-memory storage.
         * @param contextProvider Optional [ContextProvider] for device/system properties. Defaults to JVM context.
         * @return The configured [Altertable] instance.
         */
        @AltertableInternal
        public fun setup(
            config: AltertableConfig,
            storage: Storage = InMemoryStorage(),
            contextProvider: ContextProvider = DefaultContextProvider,
        ): Altertable = AltertableClient.setup(config, storage, contextProvider)

        /**
         * The shared [Altertable] instance, or `null` if not yet configured.
         *
         * Use this property to access the singleton instance after calling [setup].
         * For Java interop, Kotlin automatically generates `getShared()` from this property.
         */
        public val shared: Altertable?
            @JvmStatic
            get() = AltertableClient.shared

        /**
         * Creates and returns a new [Altertable] instance (non-singleton) using a DSL builder.
         *
         * Use this for multi-environment setups, testing, or when embedding the SDK in a library.
         *
         * @param block The configuration DSL block.
         * @return A new [Altertable] instance.
         */
        @JvmStatic
        public fun create(block: AltertableConfigBuilder.() -> Unit): Altertable {
            @OptIn(AltertableInternal::class)
            val config = AltertableConfigBuilder().apply(block).build()
            return create(config)
        }

        /**
         * Creates and returns a new [Altertable] instance (non-singleton).
         *
         * Use this for multi-environment setups, testing, or when embedding the SDK in a library.
         *
         * @param config The [AltertableConfig] to configure the client with.
         * @return A new [Altertable] instance.
         */
        @JvmStatic
        public fun create(config: AltertableConfig): Altertable = AltertableClient.create(config)
    }
}
