package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class EventQueue(
    private val maxSize: Int,
    private val queueStorage: QueueStorage? = null,
) {
    private val queue = ArrayDeque<ApiPayload>()
    private val mutex = Mutex()
    private var initialized = false

    internal suspend fun initialize() {
        mutex.withLock {
            if (!initialized) {
                queue.addAll(queueStorage?.load() ?: emptyList())
                initialized = true
            }
        }
    }

    internal suspend fun enqueue(event: ApiPayload, persist: Boolean = false) {
        mutex.withLock {
            if (queue.size >= maxSize) {
                queue.removeFirst()
            }
            queue.addLast(event)
            if (persist) {
                queueStorage?.save(queue.toList())
            }
        }
    }

    internal suspend fun flush(): List<ApiPayload> {
        mutex.withLock {
            val items = queue.toList()
            queue.clear()
            queueStorage?.save(emptyList())
            return items
        }
    }

    internal suspend fun clear() {
        mutex.withLock {
            queue.clear()
            queueStorage?.save(emptyList())
        }
    }
}
