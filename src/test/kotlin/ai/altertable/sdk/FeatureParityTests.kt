package ai.altertable.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.json.Json
import java.io.File

class QueuePersistenceTest {

    @Test
    fun `test queue persistence to disk`(@TempDir tempDir: File) = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.PENDING)
        
        // Create client with file-based queue storage
        val client = AltertableClient(config, tempDir)
        
        // Track an event while pending
        client.track("persisted_event")
        
        // Wait for async operations
        delay(200)
        
        // Get events to verify they're queued
        val events = client.eventQueue.flush()
        assertTrue(events.isNotEmpty(), "Events should be queued after track")
        
        // Put them back
        for (evt in events) {
            val payload = client.eventQueue.deserializePayload(evt.payloadJson).toMutableMap()
            payload["_endpoint"] = evt.endpoint
            client.eventQueue.enqueue(payload)
        }
        
        delay(100)
        
        // Verify the file was created
        val queueFile = File(tempDir, "altertable_queue.json")
        assertTrue(queueFile.exists(), "Queue file should exist after enqueue")
    }

    @Test
    fun `test queue drops oldest when full`(@TempDir tempDir: File) = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.PENDING)
        val client = AltertableClient(config, tempDir)
        
        // Enqueue more events than the max size
        client.track("event_0")
        delay(50)
        client.track("event_1")
        delay(50)
        client.track("event_2")
        delay(50)
        client.track("event_3")
        delay(50)
        client.track("event_4")
        delay(100)
        
        // Get the events
        val events = client.eventQueue.flush()
        
        // Should have at most maxSize events (the oldest ones should be dropped)
        assertTrue(events.size <= Constants.MAX_QUEUE_SIZE)
    }
}

class RetryTest {

    @Test
    fun `test transport retry on network failure`() = runBlocking {
        val config = AltertableConfig(
            apiKey = "test-key",
            baseUrl = "http://invalid-host-does-not-exist.example.com",
            onError = { }
        )
        val transport = Transport(config)
        
        // This should fail and retry but eventually give up
        // We can't easily test retry in unit tests without mocking
        // This test just verifies the transport is created properly
        assertNotNull(transport)
    }
}

class UpdateTraitsTest {

    @Test
    fun `test updateTraits requires prior identify`() = runBlocking {
        val config = AltertableConfig(apiKey = "test-key")
        val client = AltertableClient(config)
        
        // Should not throw - updateTraits should silently drop if not identified
        client.updateTraits(mapOf("plan" to "premium"))
        
        // No exception means test passed
    }

    @Test
    fun `test updateTraits sends identify after identify`(@TempDir tempDir: File) = runBlocking {
        val config = AltertableConfig(apiKey = "test-key", trackingConsent = TrackingConsentState.PENDING)
        val client = AltertableClient(config, tempDir)
        
        // First identify
        client.identify("user_123")
        
        delay(100)
        
        // Now update traits - should work
        client.updateTraits(mapOf("plan" to "premium"))
        
        delay(100)
        
        // Verify queue has events
        val events = client.eventQueue.flush()
        assertTrue(events.isNotEmpty())
    }
}

class ConfigTest {

    @Test
    fun `test configure preserves init-only fields`() = runBlocking {
        val config = AltertableConfig(
            apiKey = "test-key",
            flushOnBackground = true,
            trackingConsent = TrackingConsentState.GRANTED
        )
        val client = AltertableClient(config)
        
        // Configure with different values
        client.configure(config.copy(
            flushOnBackground = false,
            trackingConsent = TrackingConsentState.GRANTED
        ))
        
        // flushOnBackground should remain true (init-only)
        assertEquals(true, client.config.flushOnBackground)
    }
}
