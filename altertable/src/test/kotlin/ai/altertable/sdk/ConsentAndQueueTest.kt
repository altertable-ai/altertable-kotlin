package ai.altertable.sdk

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@Suppress("UnusedPrivateProperty")
class ConsentAndQueueTest {
    @Test
    fun `test event queue buffering and flush on consent granted`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config = AltertableConfig(
                apiKey = "test-key",
                tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                dispatcher = testDispatcher,
            )
            val client = AltertableClient(config)

            // Track an event while pending
            client.track("TestEvent")
            advanceUntilIdle()

            val queueItems = client.eventQueue.flush()
            assertEquals(1, queueItems.size)
            val trackPayload = queueItems[0] as ApiPayload.Track
            assertEquals("/track", trackPayload.endpoint)
            assertEquals("TestEvent", trackPayload.payload.event)

            // Put it back
            client.eventQueue.enqueue(queueItems[0])

            // Now grant consent.
            client.configure { tracking { consent = TrackingConsent.GRANTED } }
            advanceUntilIdle()

            val finalQueue = client.eventQueue.flush()
            assertEquals(0, finalQueue.size)
        }

    @Test
    fun `test events are dropped when consent is denied`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config = AltertableConfig(
                apiKey = "test-key",
                trackingConsent = TrackingConsentState.DENIED,
                dispatcher = testDispatcher,
            )
            val client = AltertableClient(config)

            client.track("TestEvent")
            advanceUntilIdle()

            val queueItems = client.eventQueue.flush()
            assertEquals(0, queueItems.size)
        }

    @Test
    fun `test transport construction with config`() {
        val config = AltertableConfig(
            apiKey = "test-key",
            network = NetworkConfig(baseUrl = "https://example.com"),
        )
        val transport = Transport(
            apiKey = config.apiKey,
            baseUrl = config.network.baseUrl,
            environment = config.environment,
            dispatcher = config.dispatcher,
            requestTimeout = config.network.requestTimeout,
            maxRetries = config.network.maxRetries,
        )
        assertNotNull(transport)
        transport.close()
    }

    @Test
    fun `test configure to DENIED clears queue`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config = AltertableConfig(
                apiKey = "test-key",
                tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                dispatcher = testDispatcher,
            )
            val client = AltertableClient(config)
            client.track("TestEvent")
            advanceUntilIdle()
            client.configure { tracking { consent = TrackingConsent.DENIED } }
            advanceUntilIdle()
            val queueItems = client.eventQueue.flush()
            assertEquals(0, queueItems.size)
        }

    @Test
    fun `test queue persistence across client restarts`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = InMemoryStorage()
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    trackingConsent = TrackingConsentState.PENDING,
                    dispatcher = testDispatcher,
                )
            val client1 = AltertableClient(config, storage)
            client1.track("PersistedEvent")
            advanceUntilIdle()
            client1.close()

            val client2 = AltertableClient(config, storage)
            val queueItems = client2.eventQueue.flush()
            assertEquals(1, queueItems.size)
            assertEquals("PersistedEvent", (queueItems[0] as ApiPayload.Track).payload.event)
            client2.close()
        }
}
