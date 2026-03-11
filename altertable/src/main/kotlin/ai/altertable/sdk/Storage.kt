package ai.altertable.sdk

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Key-value storage abstraction for persisting SDK state.
 *
 * Implement this interface to provide custom storage (e.g., DataStore on Android).
 * The default [InMemoryStorage] is used when no custom storage is provided.
 */
@AltertableInternal
public interface Storage {
    /** Retrieves a value by key, or null if not found. */
    public suspend fun get(key: String): String?

    /** Stores a value for the given key. */
    public suspend fun set(
        key: String,
        value: String,
    )

    /** Removes the value for the given key. */
    public suspend fun remove(key: String)

    /** Migrates a value from one key to another, removing the source. */
    public suspend fun migrate(
        from: String,
        to: String,
    )
}

@OptIn(AltertableInternal::class)
internal class InMemoryStorage : Storage {
    private val store = mutableMapOf<String, String>()
    private val mutex = Mutex()

    override suspend fun get(key: String): String? = mutex.withLock {
        store[key]
    }

    override suspend fun set(
        key: String,
        value: String,
    ) {
        mutex.withLock {
            store[key] = value
        }
    }

    override suspend fun remove(key: String) {
        mutex.withLock {
            store.remove(key)
        }
    }

    override suspend fun migrate(
        from: String,
        to: String,
    ) {
        mutex.withLock {
            val value = store.remove(from)
            if (value != null) {
                store[to] = value
            }
        }
    }
}

/**
 * Internal helper to call the Storage interface's get method from operator extensions.
 * Uses a function parameter to avoid recursion.
 */
@OptIn(AltertableInternal::class)
private suspend fun callStorageGet(storage: Storage, key: String): String? = storage.get(key)

/**
 * Internal helper to call the Storage interface's set method from operator extensions.
 * Uses a function parameter to avoid recursion.
 */
@OptIn(AltertableInternal::class)
private suspend fun callStorageSet(storage: Storage, key: String, value: String) {
    storage.set(key, value)
}

/**
 * Operator extension for [Storage] to enable bracket notation: `storage[key]`.
 * 
 * Note: This enables the syntax but requires suspend context. All usages must be within suspend functions.
 */
@AltertableInternal
public suspend operator fun Storage.get(key: String): String? = callStorageGet(this, key)

/**
 * Operator extension for [Storage] to enable bracket assignment: `storage[key] = value`.
 * 
 * Note: This enables the syntax but requires suspend context. All usages must be within suspend functions.
 */
@AltertableInternal
public suspend operator fun Storage.set(key: String, value: String) {
    callStorageSet(this, key, value)
}
