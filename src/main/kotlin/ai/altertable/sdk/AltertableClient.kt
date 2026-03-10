package ai.altertable.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class AltertableClient(var config: AltertableConfig) {
    val storage: StorageApi
    val identityManager: IdentityManager
    val sessionManager: SessionManager
    val eventQueue: EventQueue
    val transport: Transport
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        storage = InMemoryStorage()
        identityManager = IdentityManager(storage, config.apiKey, config.environment)
        sessionManager = SessionManager(storage, config.apiKey, config.environment)
        eventQueue = EventQueue(Constants.MAX_QUEUE_SIZE)
        transport = Transport(config)
    }

    fun configure(updates: AltertableConfig) {
        val oldConsent = config.trackingConsent
        config = updates
        if (oldConsent != config.trackingConsent && config.trackingConsent == TrackingConsentState.GRANTED) {
            scope.launch {
                flushQueue()
            }
        }
    }

    fun getTrackingConsent(): TrackingConsentState = config.trackingConsent

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

    fun reset(resetDeviceId: Boolean = false) {
        identityManager.reset(resetDeviceId)
        sessionManager.renewSession()
        scope.launch {
            eventQueue.clear()
        }
    }

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

        fun setup(config: AltertableConfig): AltertableClient {
            return AltertableClient(config).also { instance = it }
        }

        fun shared(): AltertableClient {
            return instance ?: throw AltertableError("AltertableClient not configured. Call setup() first.")
        }
    }
}
