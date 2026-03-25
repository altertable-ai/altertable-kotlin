@file:OptIn(AltertableInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@Suppress("UnusedPrivateProperty")
class ConsentAndQueueTest {
    private companion object {
        private const val CLIENT_INIT_SETTLE_MS = 400L
        private const val ASYNC_SCOPE_SETTLE_MS = 500L
        private const val TRACK_ENQUEUE_SETTLE_MS = 100L
    }

    @Test
    fun `test event queue buffering and flush on consent granted`() =
        runBlocking {
            val engine =
                MockEngine(
                    MockEngineConfig().apply {
                        addHandler {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(),
                            )
                        }
                    },
                )
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = Dispatchers.Default,
                )
            val client = AltertableClient(config, InMemoryStorage(), httpClientEngine = engine)
            try {
                delay(CLIENT_INIT_SETTLE_MS)
                client.track("TestEvent")
                delay(TRACK_ENQUEUE_SETTLE_MS)

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                val trackPayload = queueItems[0] as ApiPayload.Track
                assertEquals("/track", trackPayload.endpoint)
                assertEquals("TestEvent", trackPayload.payload.event)

                client.eventQueue.enqueue(queueItems[0])

                client.configure { tracking { consent = TrackingConsent.GRANTED } }
                delay(ASYNC_SCOPE_SETTLE_MS)
                client.awaitFlush()

                val finalQueue = client.eventQueue.flush()
                assertEquals(0, finalQueue.size)
            } finally {
                client.close()
            }
        }

    @Test
    fun `non-retryable batch failure persists payloads to queue`() =
        runBlocking {
            val engine =
                MockEngine(
                    MockEngineConfig().apply {
                        addHandler {
                            respond(
                                content = "",
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(),
                            )
                        }
                    },
                )
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking =
                        TrackingConfig(
                            consent = TrackingConsent.GRANTED,
                            flushEventThreshold = 1,
                            flushIntervalMs = 3_600_000L,
                        ),
                    network = NetworkConfig(baseUrl = "https://api.example.com", maxRetries = 0),
                    dispatcher = Dispatchers.Default,
                )
            val client = AltertableClient(config, InMemoryStorage(), httpClientEngine = engine)
            try {
                delay(CLIENT_INIT_SETTLE_MS)
                client.track("RequeuedEvent")
                delay(TRACK_ENQUEUE_SETTLE_MS)
                client.awaitFlush()

                val queueItems = client.eventQueue.flush()
                assertEquals(1, queueItems.size)
                assertEquals("RequeuedEvent", (queueItems[0] as ApiPayload.Track).payload.event)
            } finally {
                client.close()
            }
        }

    @Test
    fun `test events are dropped when consent is denied`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.DENIED),
                    dispatcher = testDispatcher,
                )
            val client = AltertableClient(config)
            try {
                advanceUntilIdle()
                client.track("TestEvent")
                advanceUntilIdle()

                val queueItems = client.eventQueue.flush()
                assertEquals(0, queueItems.size)
            } finally {
                client.close()
            }
        }

    @Test
    fun `test transport construction with config`() {
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://example.com"),
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
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
                client.track("TestEvent")
                advanceUntilIdle()
                client.configure { tracking { consent = TrackingConsent.DENIED } }
                advanceUntilIdle()
                val queueItems = client.eventQueue.flush()
                assertEquals(0, queueItems.size)
            } finally {
                client.close()
            }
        }

    @Test
    fun `test queue persistence across client restarts`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage = InMemoryStorage()
            val config =
                AltertableConfig(
                    apiKey = "test-key",
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    dispatcher = testDispatcher,
                )
            val client1 = AltertableClient(config, storage)
            client1.track("PersistedEvent")
            advanceUntilIdle()
            client1.close()

            val client2 = AltertableClient(config, storage)
            advanceUntilIdle()
            val queueItems = client2.eventQueue.flush()
            assertEquals(1, queueItems.size)
            assertEquals("PersistedEvent", (queueItems[0] as ApiPayload.Track).payload.event)
            client2.close()
        }
}
