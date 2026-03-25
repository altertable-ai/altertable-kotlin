package ai.altertable.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

private const val FLUSH_MAX_DRAIN_ITERATIONS = 100

/**
 * Buffers outbound [ApiPayload]s per endpoint, flushes on threshold, timer, or [flush].
 */
@Suppress("TooManyFunctions")
internal class EventBatcher(
    private val scope: CoroutineScope,
    flushEventThreshold: Int,
    flushIntervalMs: Long,
    maxBatchSize: Int,
    private val send: suspend (endpoint: String, payloads: List<ApiPayload>) -> Unit,
) {
    private val bufferMutex = Mutex()
    private var flushEventThreshold = flushEventThreshold.coerceAtLeast(1)

    @Volatile
    private var flushIntervalMs = flushIntervalMs.coerceAtLeast(1L)
    private var maxBatchSize = maxBatchSize.coerceAtLeast(1)

    private var bufferGeneration = 0
    private val trackBuffer = ArrayDeque<ApiPayload>()
    private val identifyBuffer = ArrayDeque<ApiPayload>()
    private val aliasBuffer = ArrayDeque<ApiPayload>()

    private val inFlightOther: MutableSet<Job> =
        Collections.synchronizedSet(mutableSetOf())
    private val inFlightTimer: MutableSet<Job> =
        Collections.synchronizedSet(mutableSetOf())

    private var timerJob: Job? = null

    private fun dequeFor(payload: ApiPayload): ArrayDeque<ApiPayload> =
        when (payload) {
            is ApiPayload.Track -> trackBuffer
            is ApiPayload.Identify -> identifyBuffer
            is ApiPayload.Alias -> aliasBuffer
        }

    private fun dequeForEndpoint(endpoint: String): ArrayDeque<ApiPayload>? =
        when (endpoint) {
            "/track" -> trackBuffer
            "/identify" -> identifyBuffer
            "/alias" -> aliasBuffer
            else -> null
        }

    suspend fun add(payload: ApiPayload) {
        val shouldFlush =
            bufferMutex.withLock {
                dequeFor(payload).addLast(payload)
                trackBuffer.size + identifyBuffer.size + aliasBuffer.size >= flushEventThreshold
            }
        if (shouldFlush) {
            scope.launch {
                dispatchFlushFromBuffer(fromTimer = false)
            }
        }
    }

    /**
     * Drains the buffer and waits until all in-flight [send] jobs finish. If this never completes,
     * a coroutine is likely stuck inside [send] (non-suspending blocking work or a deadlock).
     */
    suspend fun flush() {
        repeat(FLUSH_MAX_DRAIN_ITERATIONS) {
            val otherJobs = synchronized(inFlightOther) { inFlightOther.toList() }
            val timerJobs = synchronized(inFlightTimer) { inFlightTimer.toList() }
            otherJobs.joinAll()
            timerJobs.joinAll()
            dispatchFlushFromBuffer(fromTimer = false)
            val done =
                bufferMutex.withLock {
                    trackBuffer.isEmpty() && identifyBuffer.isEmpty() && aliasBuffer.isEmpty()
                } &&
                    synchronized(inFlightOther) { inFlightOther.isEmpty() } &&
                    synchronized(inFlightTimer) { inFlightTimer.isEmpty() }
            if (done) {
                return
            }
        }
        error(
            "Batcher flush exceeded $FLUSH_MAX_DRAIN_ITERATIONS drain iterations without draining " +
                "the buffer and in-flight jobs; check that send() always completes or throws.",
        )
    }

    suspend fun clear() {
        bufferMutex.withLock {
            bufferGeneration++
            trackBuffer.clear()
            identifyBuffer.clear()
            aliasBuffer.clear()
        }
    }

    fun start() {
        if (timerJob?.isActive == true) {
            return
        }
        timerJob =
            scope.launch {
                while (isActive) {
                    val interval = flushIntervalMs
                    delay(interval)
                    dispatchFlushFromBuffer(fromTimer = true)
                }
            }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Applies new batch settings. Matches JS: interval restart only when interval changes;
     * threshold-triggered flush only when [flushEventThreshold] changed and buffer is at/above the new threshold.
     */
    suspend fun updateBatchSettings(
        previousFlushEventThreshold: Int,
        previousFlushIntervalMs: Long,
        flushEventThreshold: Int,
        flushIntervalMs: Long,
        maxBatchSize: Int,
    ) {
        var shouldFlushForThreshold = false
        bufferMutex.withLock {
            this.flushEventThreshold = flushEventThreshold.coerceAtLeast(1)
            this.flushIntervalMs = flushIntervalMs.coerceAtLeast(1L)
            this.maxBatchSize = maxBatchSize.coerceAtLeast(1)
            val total = trackBuffer.size + identifyBuffer.size + aliasBuffer.size
            if (previousFlushEventThreshold != flushEventThreshold) {
                shouldFlushForThreshold = total >= this.flushEventThreshold
            }
        }
        if (previousFlushIntervalMs != flushIntervalMs) {
            val wasTimerRunning = timerJob?.isActive == true
            stop()
            if (wasTimerRunning) {
                start()
            }
        }
        if (shouldFlushForThreshold) {
            dispatchFlushFromBuffer(fromTimer = false)
        }
    }

    private suspend fun dispatchFlushFromBuffer(fromTimer: Boolean) {
        val dispatchGeneration: Int
        val chunkCap: Int
        val tracks: List<ApiPayload>
        val identifies: List<ApiPayload>
        val aliases: List<ApiPayload>
        bufferMutex.withLock {
            dispatchGeneration = bufferGeneration
            chunkCap = maxBatchSize
            tracks = trackBuffer.toList()
            identifies = identifyBuffer.toList()
            aliases = aliasBuffer.toList()
            trackBuffer.clear()
            identifyBuffer.clear()
            aliasBuffer.clear()
        }
        if (tracks.isEmpty() && identifies.isEmpty() && aliases.isEmpty()) {
            return
        }
        launchChunks(fromTimer, dispatchGeneration, "/track", tracks, chunkCap)
        launchChunks(fromTimer, dispatchGeneration, "/identify", identifies, chunkCap)
        launchChunks(fromTimer, dispatchGeneration, "/alias", aliases, chunkCap)
    }

    private fun launchChunks(
        fromTimer: Boolean,
        dispatchGeneration: Int,
        endpoint: String,
        items: List<ApiPayload>,
        chunkSize: Int,
    ) {
        if (items.isEmpty()) {
            return
        }
        val safeChunkSize = chunkSize.coerceAtLeast(1)
        var index = 0
        while (index < items.size) {
            val chunk = items.subList(index, (index + safeChunkSize).coerceAtMost(items.size)).toList()
            index += safeChunkSize
            launchChunkSend(fromTimer, dispatchGeneration, endpoint, chunk)
        }
    }

    private fun launchChunkSend(
        fromTimer: Boolean,
        dispatchGeneration: Int,
        endpoint: String,
        chunk: List<ApiPayload>,
    ) {
        val bucket = if (fromTimer) inFlightTimer else inFlightOther
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    send(endpoint, chunk)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (e: AltertableException) {
                    if (isRetryableHttpDeliveryError(e.error)) {
                        bufferMutex.withLock {
                            if (dispatchGeneration == bufferGeneration) {
                                prependToBuffer(endpoint, chunk)
                            }
                        }
                    }
                }
            }
        synchronized(bucket) {
            bucket.add(job)
        }
        job.invokeOnCompletion {
            synchronized(bucket) {
                bucket.remove(job)
            }
        }
        job.start()
    }

    private fun prependToBuffer(
        endpoint: String,
        items: List<ApiPayload>,
    ) {
        if (items.isEmpty()) {
            return
        }
        val deque = dequeForEndpoint(endpoint) ?: return
        for (payload in items.asReversed()) {
            deque.addFirst(payload)
        }
    }
}
