@file:Suppress("UnusedPrivateProperty")
@file:OptIn(AltertableInternal::class)

package ai.altertable.sdk

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextAndTraitsTest {
    @Test
    fun `track includes system context properties`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            client.track("TestEvent", mapOf("custom" to "value"))
            advanceUntilIdle()

            val queueItems = client.eventQueue.flush()
            assertEquals(1, queueItems.size)
            val trackPayload = (queueItems[0] as ApiPayload.Track).payload
            assertTrue(trackPayload.properties.containsKey(ContextProperties.LIB))
            assertTrue(trackPayload.properties.containsKey(ContextProperties.LIB_VERSION))
            assertTrue(trackPayload.properties.containsKey(ContextProperties.OS))
            assertTrue(trackPayload.properties.containsKey("custom"))
        }

    @Test
    fun `identify with traits sends traits in payload`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            client.identify("user_1", mapOf("email" to "a@b.com", "name" to "Alice"))
            advanceUntilIdle()

            val queueItems = client.eventQueue.flush()
            assertEquals(1, queueItems.size)
            val identifyPayload = (queueItems[0] as ApiPayload.Identify).payload
            assertTrue(identifyPayload.traits.containsKey("email"))
            assertTrue(identifyPayload.traits.containsKey("name"))
        }

    @Test
    fun `updateTraits sends identify payload when user is identified`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
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
        }

    @Test
    fun `screen delegates to track with screen event and $screen_name`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            client.screen("HomeScreen", mapOf("section" to "main"))
            advanceUntilIdle()

            val queueItems = client.eventQueue.flush()
            assertEquals(1, queueItems.size)
            val trackPayload = (queueItems[0] as ApiPayload.Track).payload
            assertEquals(EVENT_SCREEN_VIEW, trackPayload.event)
            assertTrue(trackPayload.properties.containsKey(ContextProperties.SCREEN_NAME))
            assertTrue(trackPayload.properties.containsKey("section"))
        }

    @Test
    fun `reset with resetTrackingConsent updates consent`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val storage = InMemoryStorage()
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.DENIED),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config, storage)
            client.reset(resetDeviceId = false, resetTrackingConsent = true)
            advanceUntilIdle()
            assertEquals(TrackingConsent.GRANTED, client.trackingConsent.value)
        }
}
