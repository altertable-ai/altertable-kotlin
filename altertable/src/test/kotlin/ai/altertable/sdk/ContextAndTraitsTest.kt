@file:Suppress("UnusedPrivateProperty")
@file:OptIn(AltertableInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextAndTraitsTest {
    private companion object {
        private const val ASYNC_WORK_DELAY_MS = 300L
        private const val RESET_ASYNC_WAIT_MS = 500L
    }

    @Test
    fun `track includes system context properties`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            try {
                advanceUntilIdle()
                client.track("TestEvent", mapOf("custom" to "value"))
                advanceUntilIdle()

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                val trackPayload = (queueItems[0] as ApiPayload.Track).payload
                assertTrue(trackPayload.properties.containsKey(ContextProperties.LIB))
                assertTrue(trackPayload.properties.containsKey(ContextProperties.LIB_VERSION))
                assertTrue(trackPayload.properties.containsKey(ContextProperties.OS))
                assertTrue(trackPayload.properties.containsKey("custom"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `identify with traits sends traits in payload`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            try {
                advanceUntilIdle()
                client.identify("user_1", mapOf("email" to "a@b.com", "name" to "Alice"))
                advanceUntilIdle()

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                val identifyPayload = (queueItems[0] as ApiPayload.Identify).payload
                assertTrue(identifyPayload.traits.containsKey("email"))
                assertTrue(identifyPayload.traits.containsKey("name"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `updateTraits sends identify payload when user is identified`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            try {
                advanceUntilIdle()
                client.identify("user_1")
                advanceUntilIdle()
                client.eventQueue.flush()
                client.updateTraits(mapOf("onboarding_completed" to true))
                advanceUntilIdle()

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                val identifyPayload = (queueItems[0] as ApiPayload.Identify).payload
                assertEquals("user_1", identifyPayload.distinctId)
                assertTrue(identifyPayload.traits.containsKey("onboarding_completed"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `screen delegates to track with screen event and screen_name property`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            try {
                advanceUntilIdle()
                client.screen("HomeScreen", mapOf("section" to "main"))
                advanceUntilIdle()

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                val trackPayload = (queueItems[0] as ApiPayload.Track).payload
                assertEquals(EVENT_SCREEN_VIEW, trackPayload.event)
                assertTrue(trackPayload.properties.containsKey(ContextProperties.SCREEN_NAME))
                assertTrue(trackPayload.properties.containsKey("section"))
            } finally {
                client.close()
            }
        }

    /**
     * Uses [Dispatchers.IO] for the client: resetting consent to GRANTED starts [EventBatcher]'s
     * timer on the client scope; sharing [StandardTestDispatcher] with [runTest] can prevent the
     * test coroutine from completing.
     */
    @Test
    fun `reset with resetTrackingConsent updates consent`() =
        runBlocking {
            val storage = InMemoryStorage()
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.DENIED),
                    dispatcher = Dispatchers.IO,
                )
            val client = AltertableClient(config, storage)
            try {
                delay(ASYNC_WORK_DELAY_MS)
                client.reset(resetDeviceId = false, resetTrackingConsent = true)
                delay(RESET_ASYNC_WAIT_MS)
                assertEquals(TrackingConsent.GRANTED, client.trackingConsent.value)
            } finally {
                client.close()
            }
        }
}
