package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EventQueue(
    private val maxSize: Int = Constants.MAX_QUEUE_SIZE
) {
    private val queue = mutableListOf<Map<String, Any?>>()
    private val mutex = Mutex()

    suspend fun enqueue(event: Map<String, Any?>) {
        mutex.withLock {
            if (queue.size >= maxSize) {
                // Drop oldest
                queue.removeAt(0)
            }
            queue.add(event)
        }
    }

    suspend fun flush(): List<Map<String, Any?>> {
        mutex.withLock {
            val items = queue.toList()
            queue.clear()
            return items
        }
    }

    suspend fun clear() {
        mutex.withLock {
            queue.clear()
        }
    }
}
