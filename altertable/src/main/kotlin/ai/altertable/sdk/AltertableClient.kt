package ai.altertable.sdk

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.io.Closeable

/**
 * Main entry point for interacting with the Altertable API.
 *
 * Provides methods for identifying users, tracking events, and managing session state.
 *
 * @param config The [AltertableConfig] instance used to initialize the client.
 */
@OptIn(AltertableInternal::class)
@Suppress("DEPRECATION", "LongParameterList", "TooGenericExceptionCaught", "TooManyFunctions")
internal class AltertableClient private constructor(
    initialConfig: AltertableConfig,
    private val storage: Storage,
    private val contextProvider: ContextProvider,
    internal val identityManager: IdentityManager,
    internal val sessionManager: SessionManager,
    internal val eventQueue: EventQueue,
    private var transport: Transport,
) : Altertable {
    private val configState = MutableStateFlow(initialConfig)
    private val initialization = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(initialConfig.dispatcher + SupervisorJob())
    private val superPropertiesMutex = Mutex()
    private val superPropertiesMap = mutableMapOf<String, Any>()
    override val superProperties = SuperProperties(scope, superPropertiesMutex, superPropertiesMap)
    private val installedIntegrations = mutableListOf<Closeable>()
    private val trackingConsentState = MutableStateFlow(initialConfig.tracking.consent)
    override val trackingConsent: StateFlow<TrackingConsent> = trackingConsentState.asStateFlow()
    private val errorsFlow = MutableSharedFlow<AltertableError>(extraBufferCapacity = 64)
    override val errors: SharedFlow<AltertableError> = errorsFlow.asSharedFlow()

    private val eventBatcher =
        EventBatcher(
            scope = scope,
            flushEventThreshold = initialConfig.tracking.flushEventThreshold,
            flushIntervalMs = initialConfig.tracking.flushIntervalMs,
            maxBatchSize = initialConfig.tracking.maxBatchSize,
            send = { _, payloads ->
                try {
                    if (configState.value.mode == TrackingMode.AUDIENCE &&
                        configState.value.tracking.consent != TrackingConsent.GRANTED
                    ) {
                        if (configState.value.tracking.consent != TrackingConsent.DENIED) {
                            for (payload in payloads) eventQueue.enqueue(payload)
                        }
                    } else {
                        transport.postBatch(payloads)
                    }
                } catch (e: AltertableException) {
                    errorsFlow.emit(e.error)
                    if (isRetryableHttpDeliveryError(e.error)) {
                        throw e
                    }
                    for (payload in payloads) {
                        eventQueue.enqueue(payload, persist = true)
                    }
                }
            },
        )

    init {
        for (integration in initialConfig.integrations) {
            val closeable = integration.create(this, initialConfig)
            installedIntegrations.add(closeable)
        }
        if (initialConfig.tracking.consent == TrackingConsent.GRANTED) {
            eventBatcher.start()
        }
        // Initialize managers and restore consent asynchronously
        scope.launch {
            try {
                if (initialConfig.mode == TrackingMode.PRODUCT) {
                    migrateLegacyStorage(initialConfig)
                    identityManager.initialize()
                    sessionManager.initialize()
                }
                eventQueue.initialize()
                // Restore consent from storage and update config
                val restoredConfig = restoreConsentFromStorage(configState.value, storage)
                val previousConsent = configState.value.tracking.consent
                if (restoredConfig.tracking.consent != previousConsent) {
                    configState.value = restoredConfig
                    trackingConsentState.value = restoredConfig.tracking.consent
                    applyConsentTransition(previousConsent, restoredConfig.tracking.consent)
                }
                initialization.complete(Unit)
            } catch (error: CancellationException) {
                initialization.completeExceptionally(error)
                throw error
            } catch (error: Throwable) {
                initialization.completeExceptionally(error)
                errorsFlow.emit(AltertableError.Validation("Failed to initialize Altertable state: ${error.message}"))
            }
        }
    }

    internal constructor(config: AltertableConfig) : this(config, InMemoryStorage())

    /**
     * Creates a client with custom storage (e.g., DataStore on Android).
     *
     * @param config The [AltertableConfig] instance used to initialize the client.
     * @param storage Custom [Storage] implementation for persisting SDK state.
     * @param contextProvider Optional [ContextProvider] for device/system properties. Defaults to JVM context.
     * @param httpClientEngine Optional Ktor engine (e.g. [io.ktor.client.engine.mock.MockEngine] in tests).
     */
    internal constructor(
        config: AltertableConfig,
        storage: Storage,
        contextProvider: ContextProvider = DefaultContextProvider,
        httpClientEngine: HttpClientEngine? = null,
    ) : this(
        initialConfig = config,
        storage = if (config.mode == TrackingMode.AUDIENCE) InMemoryStorage() else storage,
        contextProvider = contextProvider,
        identityManager = IdentityManager(storage, config.apiKey, config.environment),
        sessionManager = SessionManager(storage, config.apiKey, config.environment),
        eventQueue =
            EventQueue(
                maxSize = config.tracking.maxQueueSize,
                queueStorage =
                    if (config.mode == TrackingMode.PRODUCT) {
                        QueueStorage(storage, buildStorageKeyPrefix(config.apiKey, config.environment))
                    } else {
                        null
                    },
            ),
        transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = httpClientEngine,
            ),
    ) {
        require(config.apiKey.isNotBlank()) { "apiKey must not be blank" }
    }

    private suspend fun persistConsent(consent: TrackingConsent) {
        val currentConfig = configState.value
        val key = "${buildStorageKeyPrefix(
            currentConfig.apiKey,
            currentConfig.environment,
        )}${STORAGE_KEY_SEPARATOR}${STORAGE_KEY_TRACKING_CONSENT}"
        storage[key] = consent.name.lowercase()
    }

    private fun rejectAudienceIdentityCall(): Boolean {
        if (configState.value.mode != TrackingMode.AUDIENCE) return false
        if (configState.value.debug) {
            log(LogLevel.WARN, "identify(), alias(), and updateTraits() are ignored in audience mode.")
        }
        return true
    }

    private fun log(
        level: LogLevel,
        message: String,
    ) {
        val currentConfig = configState.value
        if (!currentConfig.debug && level == LogLevel.DEBUG) return
        val logger = currentConfig.logger ?: if (currentConfig.debug) DefaultLogger else null
        logger?.log(level, message)
    }

    /**
     * Updates the client's configuration dynamically.
     *
     * If the `trackingConsent` changes from non-granted to [TrackingConsent.GRANTED],
     * any pending events in the queue will be flushed.
     * If it changes to [TrackingConsent.DENIED], the queue is cleared.
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
    override fun configure(block: RuntimeConfigBuilder.() -> Unit) {
        scope.launch {
            if (!awaitInitialization()) return@launch
            val oldConsent = configState.value.tracking.consent
            val oldTracking = configState.value.tracking
            val builder = RuntimeConfigBuilder().from(configState.value)
            builder.block()
            val newConfig = builder.applyTo(configState.value)
            configState.value = newConfig
            val newTracking = newConfig.tracking
            if (oldTracking.flushEventThreshold != newTracking.flushEventThreshold ||
                oldTracking.flushIntervalMs != newTracking.flushIntervalMs ||
                oldTracking.maxBatchSize != newTracking.maxBatchSize
            ) {
                eventBatcher.updateBatchSettings(
                    previousFlushEventThreshold = oldTracking.flushEventThreshold,
                    previousFlushIntervalMs = oldTracking.flushIntervalMs,
                    flushEventThreshold = newTracking.flushEventThreshold,
                    flushIntervalMs = newTracking.flushIntervalMs,
                    maxBatchSize = newTracking.maxBatchSize,
                )
            }
            // Note: Transport is not recreated since apiKey/environment/dispatcher cannot change
            if (oldConsent != newConfig.tracking.consent) {
                trackingConsentState.value = newConfig.tracking.consent
                persistConsent(newConfig.tracking.consent)
                applyConsentTransition(oldConsent, newConfig.tracking.consent)
            }
        }
    }

    /**
     * Identifies a user with a unique ID and optional traits.
     *
     * This links the current anonymous state to the known user identity.
     *
     * @param userId The unique identifier for the user.
     * @param traits User properties (e.g., email, name).
     */
    override fun identify(
        userId: String,
        traits: Map<String, Any>,
    ) {
        if (rejectAudienceIdentityCall()) return
        scope.launch {
            if (!awaitInitialization()) return@launch
            identifyInternal(userId, traits)
        }
    }

    private suspend fun identifyInternal(
        userId: String,
        traits: Map<String, Any>,
    ) {
        validateUserId(userId).onFailure { exception ->
            val altertableError =
                when (exception) {
                    is AltertableException -> exception.error
                    else -> AltertableError.Validation(exception.message ?: "Validation failed")
                }
            log(LogLevel.ERROR, altertableError.message)
            errorsFlow.tryEmit(altertableError)
            return
        }
        val trimmedUserId = userId.trim()
        val wasIdentified = identityManager.anonymousId != null
        val needsReset = wasIdentified && identityManager.distinctId != trimmedUserId
        if (needsReset) {
            log(
                LogLevel.WARN,
                "User \"$trimmedUserId\" is already identified as \"${identityManager.distinctId}\". " +
                    "The session has been automatically reset. " +
                    "Use alias() to link the new ID to the existing one if intentional.",
            )
        }
        if (needsReset) {
            identityManager.reset(resetDeviceId = false)
            sessionManager.renewSession()
        }
        identityManager.identify(trimmedUserId)
        val currentConfig = configState.value
        val payload =
            IdentifyPayload(
                timestamp = Clock.System.now(),
                environment = currentConfig.environment,
                deviceId = identityManager.deviceId,
                distinctId = identityManager.distinctId,
                anonymousId = identityManager.anonymousId,
                traits = propertiesToJsonObject(traits),
                release = currentConfig.release,
            )
        enqueueOrSend(ApiPayload.Identify(payload), clearQueueFirst = needsReset)
    }

    /**
     * Updates user traits for the current identified user.
     */
    override fun updateTraits(traits: Map<String, Any>) {
        if (rejectAudienceIdentityCall()) return
        scope.launch {
            if (!awaitInitialization()) return@launch
            if (identityManager.anonymousId == null) {
                val error =
                    AltertableError.Validation(
                        "User must be identified with identify() before updating traits.",
                    )
                log(LogLevel.ERROR, error.message)
                errorsFlow.tryEmit(error)
                return@launch
            }
            val currentConfig = configState.value
            val payload =
                IdentifyPayload(
                    timestamp = Clock.System.now(),
                    environment = currentConfig.environment,
                    deviceId = identityManager.deviceId,
                    distinctId = identityManager.distinctId,
                    anonymousId = identityManager.anonymousId,
                    traits = propertiesToJsonObject(traits),
                    release = currentConfig.release,
                )
            enqueueOrSend(ApiPayload.Identify(payload))
        }
    }

    /**
     * Tracks a screen view event.
     */
    override fun screen(
        name: String,
        properties: Map<String, Any>,
    ) {
        val screenProperties = mapOf(ContextProperties.SCREEN_NAME to name) + properties
        track(EVENT_SCREEN_VIEW, screenProperties)
    }

    /**
     * Associates a new user ID with the existing user ID.
     *
     * @param newUserId The new user identifier to associate.
     */
    override fun alias(newUserId: String) {
        if (rejectAudienceIdentityCall()) return
        scope.launch {
            if (!awaitInitialization()) return@launch
            validateUserId(newUserId).onFailure { exception ->
                val altertableError =
                    when (exception) {
                        is AltertableException -> exception.error
                        else -> AltertableError.Validation(exception.message ?: "Validation failed")
                    }
                log(LogLevel.ERROR, altertableError.message)
                errorsFlow.tryEmit(altertableError)
                return@launch
            }
            val currentConfig = configState.value
            val payload =
                AliasPayload(
                    timestamp = Clock.System.now(),
                    environment = currentConfig.environment,
                    deviceId = identityManager.deviceId,
                    distinctId = identityManager.distinctId,
                    anonymousId = identityManager.anonymousId,
                    newUserId = newUserId.trim(),
                    release = currentConfig.release,
                )
            enqueueOrSend(ApiPayload.Alias(payload))
        }
    }

    /**
     * Resets the client state, clearing the current user identity and session.
     *
     * @param resetDeviceId If `true`, a new device ID will be generated. Default is `false`.
     * @param resetTrackingConsent If `true`, resets consent to [TrackingConsent.GRANTED]. Default is `false`.
     */
    override fun reset(
        resetDeviceId: Boolean,
        resetTrackingConsent: Boolean,
    ) {
        scope.launch {
            if (!awaitInitialization()) return@launch
            if (configState.value.mode == TrackingMode.PRODUCT) {
                identityManager.reset(resetDeviceId)
                sessionManager.renewSession()
            }
            if (resetTrackingConsent) {
                val currentConfig = configState.value
                val newConfig =
                    AltertableConfigBuilder()
                        .from(currentConfig)
                        .apply {
                            tracking {
                                consent = TrackingConsent.GRANTED
                            }
                        }.build()
                configState.value = newConfig
                trackingConsentState.value = TrackingConsent.GRANTED
                persistConsent(TrackingConsent.GRANTED)
            }
            eventQueue.clear()
            eventBatcher.clear(cancelInFlight = configState.value.mode == TrackingMode.AUDIENCE)
            if (resetTrackingConsent) {
                eventBatcher.start()
            }
        }
    }

    /**
     * Tracks an event with the given name and optional properties.
     *
     * @param event The name of the event to track.
     * @param properties Optional properties associated with the event. Default is an empty map.
     */
    override fun track(
        event: String,
        properties: Map<String, Any>,
    ) {
        trackInternal(event, properties)
    }

    private fun trackInternal(
        event: String,
        properties: Map<String, Any>,
    ) {
        if (event.isBlank()) {
            val error = AltertableError.Validation("event name must not be blank")
            log(LogLevel.ERROR, error.message)
            errorsFlow.tryEmit(error)
            return
        }
        scope.launch {
            if (!awaitInitialization()) return@launch
            val currentConfig = configState.value
            val audience = currentConfig.mode == TrackingMode.AUDIENCE
            val sessionId = if (audience) null else sessionManager.getSessionIdAndTouch()
            val systemProps =
                if (audience) {
                    mutableMapOf<String, Any>(
                        ContextProperties.LIB to ContextProperties.LIBRARY_NAME,
                        ContextProperties.LIB_VERSION to ContextProperties.LIBRARY_VERSION,
                    )
                } else {
                    contextProvider.getSystemProperties().toMutableMap()
                }
            currentConfig.release?.let { systemProps[ContextProperties.RELEASE] = it }
            val currentSuperProperties =
                superPropertiesMutex.withLock {
                    superPropertiesMap.toMap()
                }
            val mergedProperties = systemProps + currentSuperProperties + properties
            val payload =
                TrackPayload(
                    timestamp = Clock.System.now(),
                    event = event.trim(),
                    environment = currentConfig.environment,
                    deviceId = if (audience) null else identityManager.deviceId,
                    distinctId = if (audience) null else identityManager.distinctId,
                    anonymousId = if (audience) null else identityManager.anonymousId,
                    sessionId = sessionId,
                    properties = propertiesToJsonObject(mergedProperties),
                    release = currentConfig.release,
                )
            enqueueOrSend(ApiPayload.Track(payload))
        }
    }

    /**
     * Force-flushes any pending events in the queue.
     *
     * This is a fire-and-forget operation that does not block the calling thread.
     */
    override fun flush() {
        scope.launch {
            if (awaitInitialization()) flushAllPending()
        }
    }

    /**
     * Force-flushes any pending events in the queue and suspends until completion.
     */
    override suspend fun awaitFlush() {
        if (awaitInitialization()) flushAllPending()
    }

    /**
     * Closes the client and releases resources (coroutine scope, HTTP client, integrations).
     */
    override fun close() {
        for (closeable in installedIntegrations) {
            closeable.close()
        }
        installedIntegrations.clear()
        eventBatcher.stop()
        scope.cancel()
        transport.close()
    }

    private fun enqueueOrSend(
        payload: ApiPayload,
        clearQueueFirst: Boolean = false,
    ) {
        val currentConfig = configState.value
        var currentEvent: Event? = payload.toEvent()

        // Apply interceptors
        for (interceptor in currentConfig.beforeSend) {
            currentEvent = currentEvent?.let { interceptor(it) }
            if (currentEvent == null) break
        }

        // Convert back to ApiPayload, dropping if null
        val transformed = currentEvent?.let { payload.applyEvent(it) } ?: return
        val finalPayload =
            if (currentConfig.mode == TrackingMode.AUDIENCE) {
                val track = transformed as? ApiPayload.Track ?: return
                track.copy(payload = prepareAudienceEvent(track.payload))
            } else {
                transformed.withoutEnvironment()
            }

        scope.launch {
            if (clearQueueFirst) {
                eventQueue.clear()
                eventBatcher.clear(cancelInFlight = configState.value.mode == TrackingMode.AUDIENCE)
            }
            val configForConsent = configState.value
            when (configForConsent.tracking.consent) {
                TrackingConsent.GRANTED -> {
                    eventBatcher.add(finalPayload)
                }
                TrackingConsent.PENDING, TrackingConsent.DISMISSED ->
                    eventQueue.enqueue(finalPayload, persist = true)
                TrackingConsent.DENIED -> { /* drop */ }
            }
        }
    }

    /** Restore only the explicitly selected legacy namespace as one recoverable state move. */
    @Suppress("DEPRECATION")
    private suspend fun migrateLegacyStorage(config: AltertableConfig) {
        val legacy = buildLegacyStorageKeyPrefix(config.apiKey, config.environment)
        val current = buildStorageKeyPrefix(config.apiKey)
        migrateLegacyState(
            legacy,
            current,
            listOf(
                "device_id",
                "distinct_id",
                "anonymous_id",
                "session_id",
                "last_event_at",
                STORAGE_KEY_QUEUE,
                STORAGE_KEY_TRACKING_CONSENT,
            ),
        )
    }

    private suspend fun migrateLegacyState(
        legacyPrefix: String,
        currentPrefix: String,
        suffixes: List<String>,
    ) {
        val targets = suffixes.associateWith { "$currentPrefix$STORAGE_KEY_SEPARATOR$it" }
        val marker = "$currentPrefix${STORAGE_KEY_SEPARATOR}legacy_migration"
        val pendingMigration = storage[marker]
        if (pendingMigration == null && targets.values.any { storage[it] != null }) {
            val source = suffixes.associateWith { storage["$legacyPrefix$STORAGE_KEY_SEPARATOR$it"] }
            source.filterValues { it != null }.keys.forEach { suffix ->
                storage.remove("$legacyPrefix$STORAGE_KEY_SEPARATOR$suffix")
                check(storage["$legacyPrefix$STORAGE_KEY_SEPARATOR$suffix"] == null) {
                    "Storage failed to remove superseded Altertable state"
                }
            }
            return
        }
        val sourcePrefix =
            pendingMigration ?: legacyPrefix.also {
                storage[marker] = it
                check(storage[marker] == it) {
                    "Storage failed to persist Altertable migration state"
                }
            }
        val source = suffixes.associateWith { storage["$sourcePrefix$STORAGE_KEY_SEPARATOR$it"] }
        source.forEach { (suffix, value) ->
            if (value != null) storage[checkNotNull(targets[suffix])] = value
        }
        source.forEach { (suffix, value) ->
            if (value != null) {
                check(storage[checkNotNull(targets[suffix])] == value) {
                    "Storage failed to persist migrated Altertable state"
                }
            }
        }
        source.filterValues { it != null }.keys.forEach { suffix ->
            val key = "$sourcePrefix$STORAGE_KEY_SEPARATOR$suffix"
            storage.remove(key)
            check(storage[key] == null) {
                "Storage failed to remove migrated Altertable state"
            }
        }
        storage.remove(marker)
        check(storage[marker] == null) {
            "Storage failed to complete Altertable state migration"
        }
    }

    /** Returns false after a failed initialization so public calls cannot race partial state. */
    private suspend fun awaitInitialization(): Boolean =
        try {
            initialization.await()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }

    /**
     * Replays persisted consent queue into the batcher, then flushes that snapshot (JS parity).
     */
    private suspend fun flushQueue() {
        val events = eventQueue.flush()
        for (evt in events) {
            eventBatcher.add(evt)
        }
        if (events.isNotEmpty()) {
            eventBatcher.flush()
        }
    }

    /** Drains the persisted consent queue and all in-memory outbound batches. */
    private suspend fun flushAllPending() {
        if (configState.value.tracking.consent != TrackingConsent.GRANTED) return
        val events = eventQueue.flush()
        for (evt in events) {
            eventBatcher.add(evt)
        }
        eventBatcher.flush()
    }

    private suspend fun applyConsentTransition(
        previous: TrackingConsent,
        current: TrackingConsent,
    ) {
        when (current) {
            TrackingConsent.GRANTED -> {
                if (previous != TrackingConsent.GRANTED) {
                    eventBatcher.start()
                }
                flushQueue()
            }
            TrackingConsent.DENIED -> {
                eventQueue.clear()
                eventBatcher.clear(cancelInFlight = configState.value.mode == TrackingMode.AUDIENCE)
                eventBatcher.stop()
            }
            TrackingConsent.PENDING, TrackingConsent.DISMISSED -> {
                if (previous == TrackingConsent.GRANTED) {
                    if (configState.value.mode == TrackingMode.PRODUCT) eventBatcher.flush()
                    eventBatcher.stop()
                }
            }
        }
    }

    public companion object {
        @Volatile
        private var instance: AltertableClient? = null

        private val setupLock = Any()

        @Suppress("ReturnCount", "MaxLineLength")
        private suspend fun restoreConsentFromStorage(
            config: AltertableConfig,
            storage: Storage,
        ): AltertableConfig {
            val key = "${buildStorageKeyPrefix(
                config.apiKey,
                config.environment,
            )}${STORAGE_KEY_SEPARATOR}${STORAGE_KEY_TRACKING_CONSENT}"
            val stored = storage[key] ?: return config
            val consent = TrackingConsent.entries.find { it.name.lowercase() == stored } ?: return config
            return AltertableConfigBuilder()
                .from(config)
                .apply {
                    tracking {
                        this@tracking.consent = consent
                    }
                }.build()
        }

        /**
         * Internal factory method for creating the singleton instance.
         * Called from [Altertable.setup].
         */
        internal fun setup(
            config: AltertableConfig,
            storage: Storage = InMemoryStorage(),
            contextProvider: ContextProvider = DefaultContextProvider,
        ): AltertableClient {
            synchronized(setupLock) {
                require(config.apiKey.isNotBlank()) { "apiKey must not be blank" }
                instance?.close()
                return AltertableClient(config, storage, contextProvider).also { instance = it }
            }
        }

        /**
         * The shared singleton instance, or `null` if not yet configured.
         */
        internal val shared: AltertableClient?
            get() = instance

        /**
         * Internal factory method for creating non-singleton instances.
         * Called from [Altertable.create].
         */
        internal fun create(config: AltertableConfig): AltertableClient {
            require(config.apiKey.isNotBlank()) { "apiKey must not be blank" }
            return AltertableClient(config)
        }
    }
}
