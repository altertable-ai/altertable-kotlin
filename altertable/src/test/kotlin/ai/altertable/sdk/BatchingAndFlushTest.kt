@file:OptIn(AltertableInternal::class)

package ai.altertable.sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchingAndFlushTest {
    @Test
    fun `tracking config defaults include batching and flush interval`() {
        val tracking = TrackingConfig()

        assertEquals(20, tracking.flushAt)
        assertEquals(30_000, tracking.flushInterval.inWholeMilliseconds)
        assertEquals(30_000, tracking.flushIntervalMillis)
        assertEquals(50, tracking.maxBatchSize)
    }

    @Test
    fun `event timestamp remains stable when flush fails and event is re-queued`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val client =
                AltertableClient(
                    AltertableConfig(
                        apiKey = "test-key",
                        dispatcher = dispatcher,
                        network = NetworkConfig(baseUrl = "http://127.0.0.1:1", maxRetries = 0),
                        tracking = TrackingConfig(flushAt = 100, flushIntervalMillis = 60_000),
                    ),
                )

            try {
                client.track("InvariantEvent")
                advanceUntilIdle()

                val queuedBeforeFlush = client.eventQueue.flush()
                assertEquals(1, queuedBeforeFlush.size)
                val original = queuedBeforeFlush.first() as ApiPayload.Track

                // Put it back and force flush, which will fail and re-queue.
                client.eventQueue.enqueue(original, persist = true)
                client.awaitFlush()
                advanceUntilIdle()

                val queuedAfterFailedFlush = client.eventQueue.flush()
                assertEquals(1, queuedAfterFailedFlush.size)
                val retried = queuedAfterFailedFlush.first() as ApiPayload.Track

                assertEquals(original.payload.timestamp, retried.payload.timestamp)
                assertEquals("InvariantEvent", retried.payload.event)
            } finally {
                client.close()
            }
        }
}
