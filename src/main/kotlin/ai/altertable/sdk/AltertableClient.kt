package ai.altertable.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Main entry point for interacting with the Altertable API.
 * 
 * Provides methods for identifying users, tracking events, and managing session state.
 *
 * @param config The [AltertableConfig] instance used to initialize the client.
 * @param cacheDir An optional [File] representing the cache directory for queue storage.
 */
@Suppress("TooManyFunctions")
class AltertableClient(
    var config: AltertableConfig,
    private val cacheDir: File? = null
) {
    /** The storage implementation used by this client. */
    val storage: StorageApi
    /** The identity manager handling user identities. */
    val identityManager: IdentityManager
    /** The session manager handling session lifecycles. */
    val sessionManager: SessionManager
    /** The event queue for pending events when consent is pending. */
    val eventQueue: EventQueue
    /** The transport layer used to send requests to the Altertable API. */
    val transport: Transport
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    init {
        storage = InMemoryStorage()
        identityManager = IdentityManager(storage, config.apiKey, config.environment)
        sessionManager = SessionManager(storage, config.apiKey, config.environment)
        
        val queueStorage = cacheDir?.let { QueueStorage(it) }
        eventQueue = EventQueue(Constants.MAX_QUEUE_SIZE, queueStorage)
        transport = Transport(config)

        scope.launch {
            eventQueue.loadFromDisk()
        }
    }

    /**
     * Updates the client's configuration dynamically.
     * 
     * If the `trackingConsent` changes from non-granted to [TrackingConsentState.GRANTED],
     * any pending events in the queue will be flushed.
     *
     * @param updates The new [AltertableConfig] to apply.
     */
    fun configure(updates: AltertableConfig) {
        val oldConsent = config.trackingConsent
        
        // Store init-only fields that cannot be changed
        val frozenFlushOnBackground = config.flushOnBackground
        
        // Apply updates
        config = updates.copy(flushOnBackground = frozenFlushOnBackground)
        
        if (oldConsent != config.trackingConsent) {
            when (config.trackingConsent) {
                TrackingConsentState.GRANTED -> {
                    scope.launch {
                        flushQueue()
                    }
                }
                TrackingConsentState.DENIED -> {
                    scope.launch {
                        eventQueue.clear()
                    }
                }
                else -> { /* no-op */ }
            }
        }
    }

    /**
     * Retrieves the current tracking consent state.
     *
     * @return The current [TrackingConsentState].
     */
    fun getTrackingConsent(): TrackingConsentState = config.trackingConsent

    /**
     * Identifies a user with a unique ID.
     * 
     * This links the current anonymous state to the known user identity.
     *
     * @param userId The unique identifier for the user.
     * @param traits Optional properties to associate with the user identity.
     */
    fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        val oldDistinctId = identityManager.distinctId
        identityManager.identify(userId)
        val event = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to userId,
            "anonymous_id" to oldDistinctId,
            "traits" to traits
        )
        enqueueOrSend("/identify", event)
    }

    /**
     * Associates a new user ID with the existing user ID.
     *
     * @param newUserId The new user identifier to associate.
     */
    fun alias(newUserId: String) {
        val event = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to identityManager.distinctId,
            "anonymous_id" to identityManager.anonymousId,
            "new_user_id" to newUserId
        )
        enqueueOrSend("/alias", event)
    }

    /**
     * Updates traits for the currently identified user.
     * 
     * @param traits The properties to update.
     */
    fun updateTraits(traits: Map<String, Any>) {
        if (identityManager.anonymousId == null) {
            return
        }
        val event = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to identityManager.distinctId,
            "anonymous_id" to identityManager.anonymousId,
            "traits" to traits
        )
        enqueueOrSend("/identify", event)
    }

    /**
     * Resets the client state, clearing the current user identity and session.
     *
     * @param resetDeviceId If `true`, a new device ID will be generated. Default is `false`.
     */
    fun reset(resetDeviceId: Boolean = false) {
        identityManager.reset(resetDeviceId)
        sessionManager.renewSession()
        scope.launch {
            eventQueue.clear()
        }
    }

    /**
     * Tracks an event with the given name and optional properties.
     *
     * @param event The name of the event to track.
     * @param properties Optional properties associated with the event. Default is an empty map.
     */
    @Suppress("UnusedPrivateProperty", "UnusedParameter", "UNUSED_PARAMETER")
    fun track(
        event: String,
        properties: Map<String, Any> = emptyMap()
    ) {
        sessionManager.getSessionIdAndTouch()
        
        val systemProperties = getSystemProperties()
        val finalProperties = systemProperties + properties
        
        val payload = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "event" to event,
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to identityManager.distinctId,
            "anonymous_id" to identityManager.anonymousId,
            "session_id" to sessionManager.getSessionIdAndTouch(),
            "properties" to finalProperties
        )
        enqueueOrSend("/track", payload)
    }

    private fun getSystemProperties(): Map<String, Any> {
        val props = mutableMapOf<String, Any>()
        
        props["\$os"] = "Android"
        
        config.release?.let {
            props["\$release"] = it
        }

        return props
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun enqueueOrSend(endpoint: String, payload: Map<String, Any?>) {
        val finalPayload = payload.toMutableMap()
        finalPayload["_endpoint"] = endpoint
        scope.launch {
            when (config.trackingConsent) {
                TrackingConsentState.GRANTED -> {
                    try {
                        transport.post(endpoint, payload)
                    } catch (e: Exception) {
                        eventQueue.enqueue(finalPayload)
                    }
                }
                TrackingConsentState.PENDING, TrackingConsentState.DISMISSED -> eventQueue.enqueue(finalPayload)
                TrackingConsentState.DENIED -> { /* drop */ }
            }
        }
    }

    fun flush() {
        scope.launch {
            flushQueue()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun flushQueue() {
        val events = eventQueue.flush()
        for (evt in events) {
            try {
                transport.postEvent(evt)
            } catch (e: Exception) {
                val payload = eventQueue.deserializePayload(evt.payloadJson)
                val newPayload = payload.toMutableMap()
                newPayload["_endpoint"] = evt.endpoint
                eventQueue.enqueue(newPayload)
            }
        }
    }

    private fun serializeToJsonElement(map: Map<String, Any?>): kotlinx.serialization.json.JsonElement {
        val jsonMap = map.mapValues { (_, value) ->
            when (value) {
                is String -> kotlinx.serialization.json.JsonPrimitive(value)
                is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    serializeToJsonElement(value as Map<String, Any?>)
                }
                null -> kotlinx.serialization.json.JsonNull
                else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
            }
        }
        return kotlinx.serialization.json.JsonObject(jsonMap)
    }

    companion object {
        private var instance: AltertableClient? = null

        /**
         * Initializes and returns the shared [AltertableClient] instance.
         *
         * @param config The [AltertableConfig] to configure the client with.
         * @param cacheDir An optional [File] representing the cache directory for queue storage.
         * @return The configured [AltertableClient] instance.
         */
        fun setup(config: AltertableConfig, cacheDir: File? = null): AltertableClient {
            return AltertableClient(config, cacheDir).also { instance = it }
        }

        /**
         * Returns the shared [AltertableClient] instance.
         *
         * @return The previously configured [AltertableClient] instance.
         * @throws AltertableError if the client has not been configured via [setup].
         */
        fun shared(): AltertableClient {
            return instance ?: throw AltertableError("AltertableClient not configured. Call setup() first.")
        }
    }
}
