package ai.altertable.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main entry point for interacting with the Altertable API.
 * 
 * Provides methods for identifying users, tracking events, and managing session state.
 *
 * @param config The [AltertableConfig] instance used to initialize the client.
 */
class AltertableClient(var config: AltertableConfig) {
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

    init {
        storage = InMemoryStorage()
        identityManager = IdentityManager(storage, config.apiKey, config.environment)
        sessionManager = SessionManager(storage, config.apiKey, config.environment)
        eventQueue = EventQueue(Constants.MAX_QUEUE_SIZE)
        transport = Transport(config)
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
        config = updates
        if (oldConsent != config.trackingConsent && config.trackingConsent == TrackingConsentState.GRANTED) {
            scope.launch {
                flushQueue()
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
     */
    fun identify(userId: String) {
        val oldDistinctId = identityManager.distinctId
        identityManager.identify(userId)
        val event = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to userId,
            "anonymous_id" to oldDistinctId,
            "traits" to emptyMap<String, Any>()
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
            "anonymous_id" to null,
            "new_user_id" to newUserId
        )
        enqueueOrSend("/alias", event)
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
        val payload = mutableMapOf<String, Any?>(
            "timestamp" to java.time.Instant.now().toString(),
            "event" to event,
            "environment" to config.environment,
            "device_id" to identityManager.deviceId,
            "distinct_id" to identityManager.distinctId,
            "anonymous_id" to identityManager.anonymousId,
            "session_id" to sessionManager.getSessionIdAndTouch(),
            "properties" to properties
        )
        enqueueOrSend("/track", payload)
    }
    
    private fun enqueueOrSend(endpoint: String, payload: Map<String, Any?>) {
        val finalPayload = payload.toMutableMap()
        finalPayload["_endpoint"] = endpoint
        scope.launch {
            when (config.trackingConsent) {
                TrackingConsentState.GRANTED -> transport.post(endpoint, payload)
                TrackingConsentState.PENDING, TrackingConsentState.DISMISSED -> eventQueue.enqueue(finalPayload)
                TrackingConsentState.DENIED -> { /* drop */ }
            }
        }
    }
    
    private suspend fun flushQueue() {
        val events = eventQueue.flush()
        for (evt in events) {
            val endpoint = evt["_endpoint"] as? String ?: "/track"
            val payload = evt.filterKeys { it != "_endpoint" }
            transport.post(endpoint, payload)
        }
    }

    companion object {
        private var instance: AltertableClient? = null

        /**
         * Initializes and returns the shared [AltertableClient] instance.
         *
         * @param config The [AltertableConfig] to configure the client with.
         * @return The configured [AltertableClient] instance.
         */
        fun setup(config: AltertableConfig): AltertableClient {
            return AltertableClient(config).also { instance = it }
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
