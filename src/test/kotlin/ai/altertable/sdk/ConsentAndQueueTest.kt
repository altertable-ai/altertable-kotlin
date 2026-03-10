package ai.altertable.sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ConsentAndQueueTest {

    @Test
    fun `test event queue buffering and flush on consent granted`() = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.PENDING)
        val client = AltertableClient(config)
        
        // Track an event while pending
        client.track("TestEvent")
        
        // wait for Dispatchers.IO
        var queueItems = emptyList<Map<String, Any?>>()
        repeat(20) {
            queueItems = client.eventQueue.flush()
            if (queueItems.isNotEmpty()) break
            delay(50)
        }
        
        assertEquals(1, queueItems.size)
        assertEquals("/track", queueItems[0]["_endpoint"])
        assertEquals("TestEvent", queueItems[0]["event"])
        
        // Put it back
        client.eventQueue.enqueue(queueItems[0])
        
        // Now grant consent.
        
        
        client.configure(config.copy(trackingConsent = TrackingConsentState.GRANTED))
        
        repeat(20) {
            val emptyQueue = client.eventQueue.flush()
            if (emptyQueue.isEmpty()) {
                
                
                
                // `val events = eventQueue.flush(); for (evt in events) { transport.post(endpoint, payload) }`
                
                break
            }
            client.eventQueue.enqueue(emptyQueue[0])
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
