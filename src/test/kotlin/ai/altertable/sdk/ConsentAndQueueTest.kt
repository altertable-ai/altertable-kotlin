package ai.altertable.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

class ConsentAndQueueTest {

    @Test
    @Suppress("UnusedPrivateProperty", "UNUSED_VARIABLE")
    fun `test event queue buffering and flush on consent granted`() = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.PENDING)
        val client = AltertableClient(config)
        
        // Track an event while pending
        client.track("TestEvent")
        
        // wait for Dispatchers.IO
        var queueItems = emptyList<QueuedEvent>()
        for (_i in 0 until 20) {
            queueItems = client.eventQueue.flush()
            if (queueItems.isNotEmpty()) break
            delay(50)
        }
        
        assertEquals(1, queueItems.size)
        assertEquals("/track", queueItems[0].endpoint)
        
        // Put it back - need to convert back to the format expected by enqueue
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val payloadJson = queueItems[0].payloadJson
        val requeuePayload = client.eventQueue.deserializePayload(payloadJson).toMutableMap()
        requeuePayload["_endpoint"] = queueItems[0].endpoint
        client.eventQueue.enqueue(requeuePayload)
        
        // Now grant consent.
        client.configure(config.copy(trackingConsent = TrackingConsentState.GRANTED))
        
        for (_i in 0 until 20) {
            val emptyQueue = client.eventQueue.flush()
            if (emptyQueue.isEmpty()) {
                break
            }
            val requeuePayload2 = client.eventQueue.deserializePayload(emptyQueue[0].payloadJson).toMutableMap()
            requeuePayload2["_endpoint"] = emptyQueue[0].endpoint
            client.eventQueue.enqueue(requeuePayload2)
            delay(50)
        }
        val finalQueue = client.eventQueue.flush()
        assertEquals(0, finalQueue.size)
    }

    @Test
    fun `test events are dropped when consent is denied`() = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.DENIED)
        val client = AltertableClient(config)
        
        client.track("TestEvent")
        delay(200) // Give it time to attempt enqueue
        
        val queueItems = client.eventQueue.flush()
        assertEquals(0, queueItems.size)
    }

    @Test
    fun `test transport construction with config`() {
        val config = AltertableConfig(apiKey = "test-key", baseUrl = "https://example.com")
        val transport = Transport(config)
        assertNotNull(transport)
    }
}
