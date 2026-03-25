@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections

@OptIn(AltertableInternal::class)
private fun trackPayload(eventName: String = "e"): ApiPayload.Track =
    ApiPayload.Track(
        TrackPayload(
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            event = eventName,
            environment = "test",
            deviceId = "d",
            distinctId = "u",
            anonymousId = null,
            sessionId = "s",
            properties = buildJsonObject {},
            release = null,
        ),
    )

class BatcherTest {
    private companion object {
        private const val TIMER_TICK_WAIT_MS = 500L
        private const val FLUSH_STALLED_WAIT_MS = 100L
    }

    private fun batcherScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `flush when combined count reaches threshold`() =
        runBlocking {
            val batcherScope = batcherScope()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 3,
                    flushIntervalMs = 60_000L,
                    maxBatchSize = 10,
                    send = { _, payloads ->
                        batches.add(payloads.toList())
                    },
                )
            try {
                batcher.start()
                batcher.add(trackPayload("a"))
                batcher.add(trackPayload("b"))
                assertEquals(0, batches.size)
                batcher.add(trackPayload("c"))
                delay(50)
                assertEquals(1, batches.size)
                assertEquals(3, batches[0].size)
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `manual flush drains buffer`() =
        runBlocking {
            val batcherScope = batcherScope()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 60_000L,
                    maxBatchSize = 10,
                    send = { _, payloads ->
                        batches.add(payloads.toList())
                    },
                )
            try {
                batcher.start()
                batcher.add(trackPayload("x"))
                assertEquals(0, batches.size)
                batcher.flush()
                assertEquals(1, batches.size)
                assertEquals(1, batches[0].size)
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `maxBatchSize splits one endpoint into multiple requests`() =
        runBlocking {
            val batcherScope = batcherScope()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 60_000L,
                    maxBatchSize = 2,
                    send = { _, payloads ->
                        batches.add(payloads.toList())
                    },
                )
            try {
                batcher.start()
                repeat(5) { index ->
                    batcher.add(trackPayload("ev$index"))
                }
                batcher.flush()
                assertEquals(3, batches.size)
                assertEquals(5, batches.sumOf { it.size })
                assertEquals(listOf(1, 2, 2), batches.map { it.size }.sorted())
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `retryable failure prepends chunk back for later flush`() =
        runBlocking {
            val batcherScope = batcherScope()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val sendCount =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 60_000L,
                    maxBatchSize = 10,
                    send = { _, payloads ->
                        val count = sendCount.incrementAndGet()
                        if (count == 1) {
                            throw AltertableException(
                                AltertableError.Api(503, "x", null, null, "x"),
                            )
                        }
                        batches.add(payloads.toList())
                    },
                )
            try {
                batcher.start()
                batcher.add(trackPayload("retry"))
                batcher.flush()
                assertEquals(2, sendCount.get())
                assertEquals(1, batches.size)
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `clear drops pending payloads before they are sent`() =
        runBlocking {
            val batcherScope = batcherScope()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 60_000L,
                    maxBatchSize = 10,
                    send = { _, payloads ->
                        batches.add(payloads.toList())
                    },
                )
            try {
                batcher.start()
                batcher.add(trackPayload("dropped"))
                batcher.clear()
                batcher.flush()
                assertTrue(batches.isEmpty())
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `flush waits for timer-originated send jobs`() =
        runBlocking {
            val batcherScope = batcherScope()
            val unblockSend = CompletableDeferred<Unit>()
            val batches = Collections.synchronizedList(mutableListOf<List<ApiPayload>>())
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 200L,
                    maxBatchSize = 10,
                    send = { _, payloads ->
                        batches.add(payloads.toList())
                        unblockSend.await()
                    },
                )
            try {
                batcher.start()
                batcher.add(trackPayload("timer"))
                delay(TIMER_TICK_WAIT_MS)
                val flushJob =
                    batcherScope.launch {
                        batcher.flush()
                    }
                delay(FLUSH_STALLED_WAIT_MS)
                assertFalse(flushJob.isCompleted)
                unblockSend.complete(Unit)
                flushJob.join()
                assertEquals(1, batches.size)
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }

    @Test
    fun `changing interval without started timer does not start periodic flush`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val batcherScope = CoroutineScope(SupervisorJob() + testDispatcher)
            var sendCount = 0
            val batcher =
                EventBatcher(
                    scope = batcherScope,
                    flushEventThreshold = 100,
                    flushIntervalMs = 5_000L,
                    maxBatchSize = 10,
                    send = { _, _ ->
                        sendCount++
                    },
                )
            try {
                batcher.updateBatchSettings(
                    previousFlushEventThreshold = 100,
                    previousFlushIntervalMs = 5_000L,
                    flushEventThreshold = 100,
                    flushIntervalMs = 10_000L,
                    maxBatchSize = 10,
                )
                advanceTimeBy(30_000L)
                advanceUntilIdle()
                assertEquals(0, sendCount)
            } finally {
                batcher.stop()
                batcherScope.cancel()
            }
        }
}
