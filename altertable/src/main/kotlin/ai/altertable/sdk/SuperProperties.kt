package ai.altertable.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe wrapper for super properties that provides a map-like interface.
 *
 * Super properties are automatically included in all tracked events.
 *
 * Suspend operations are the primary API and provide synchronous access.
 * Non-suspend operations are fire-and-forget convenience methods for non-coroutine contexts.
 */
public class SuperProperties
    internal constructor(
        private val scope: CoroutineScope,
        private val mutex: Mutex,
        private val properties: MutableMap<String, Any>,
    ) {
        /**
         * Gets the value for the given key, or null if not present.
         */
        public suspend fun get(key: String): Any? =
            mutex.withLock {
                properties[key]
            }

        /**
         * Sets the value for the given key (suspend).
         *
         * Use this when you're already in a coroutine context and need synchronous behavior.
         * For non-coroutine contexts, use the bracket syntax: `superProperties["key"] = value`
         */
        public suspend fun setValue(
            key: String,
            value: Any,
        ) {
            mutex.withLock {
                properties[key] = value
            }
        }

        /**
         * Removes the value for the given key.
         *
         * @return The removed value, or null if the key was not present.
         */
        public suspend fun remove(key: String): Any? =
            mutex.withLock {
                properties.remove(key)
            }

        /**
         * Removes all super properties.
         */
        public suspend fun clear() {
            mutex.withLock {
                properties.clear()
            }
        }

        /**
         * Sets the value for the given key (fire-and-forget).
         *
         * This is a convenience method for non-coroutine contexts.
         * Use the suspend [set] method when you're already in a coroutine context.
         */
        public operator fun set(
            key: String,
            value: Any,
        ) {
            scope.launch {
                mutex.withLock {
                    properties[key] = value
                }
            }
        }

        /**
         * Removes the value for the given key (fire-and-forget).
         *
         * This is a convenience method for non-coroutine contexts.
         * Use the suspend [remove] method when you're already in a coroutine context.
         */
        public fun removeAsync(key: String) {
            scope.launch {
                mutex.withLock {
                    properties.remove(key)
                }
            }
        }

        /**
         * Removes all super properties (fire-and-forget).
         *
         * This is a convenience method for non-coroutine contexts.
         * Use the suspend [clear] method when you're already in a coroutine context.
         */
        public fun clearAsync() {
            scope.launch {
                mutex.withLock {
                    properties.clear()
                }
            }
        }

        /**
         * Gets a snapshot of all current super properties (suspend).
         */
        public suspend fun toMap(): Map<String, Any> =
            mutex.withLock {
                properties.toMap()
            }

        /**
         * Checks if a key is present (suspend).
         */
        public suspend fun containsKey(key: String): Boolean =
            mutex.withLock {
                properties.containsKey(key)
            }

        /**
         * Gets the number of super properties (suspend).
         */
        public suspend fun size(): Int =
            mutex.withLock {
                properties.size
            }
    }
