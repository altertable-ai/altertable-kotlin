@file:OptIn(AltertableInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EnvironmentTest {
    private class MapStorage : Storage {
        val values = mutableMapOf<String, String>()
        var failWriteKey: String? = null
        var failRemoveKey: String? = null

        override suspend fun get(key: String): String? = values[key]

        override suspend fun set(
            key: String,
            value: String,
        ) {
            if (key == failWriteKey) return
            values[key] = value
        }

        override suspend fun remove(key: String) {
            if (key == failRemoveKey) return
            values.remove(key)
        }

        override suspend fun migrate(
            from: String,
            to: String,
        ) {
            values.remove(from)?.let { values[to] = it }
        }
    }

    private class BlockingStorage : Storage {
        val values = mutableMapOf<String, String>()
        val readStarted = CompletableDeferred<Unit>()
        val releaseReads = CompletableDeferred<Unit>()
        var getCalls = 0

        override suspend fun get(key: String): String? {
            getCalls += 1
            readStarted.complete(Unit)
            releaseReads.await()
            return values[key]
        }

        override suspend fun set(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }

        override suspend fun migrate(
            from: String,
            to: String,
        ) {
            values.remove(from)?.let { values[to] = it }
        }
    }

    @Test
    fun `deprecated environment migrates its legacy namespace into API-key state`() =
        runTest {
            val storage = MapStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.distinct_id"] = "legacy-user"
            val client =
                AltertableClient(
                    AltertableConfig(
                        apiKey = "key",
                        environment = "staging",
                        dispatcher = StandardTestDispatcher(testScheduler),
                    ),
                    storage,
                )
            try {
                runCurrent()
                assertEquals("legacy-user", storage.values["$current.distinct_id"])
                assertFalse(storage.values.keys.any { it.startsWith("atbl.key.staging") })
                assertEquals(buildStorageKeyPrefix("key", "production"), current)
            } finally {
                client.close()
            }
        }

    @Test
    fun `partly initialized API-key identity wins without mixing legacy identity`() =
        runTest {
            val storage = MapStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.distinct_id"] = "legacy-user"
            storage.values["$current.device_id"] = "destination-device"
            val client =
                AltertableClient(
                    AltertableConfig(
                        apiKey = "key",
                        environment = "staging",
                        dispatcher = StandardTestDispatcher(testScheduler),
                    ),
                    storage,
                )
            try {
                runCurrent()
                assertEquals("destination-device", storage.values["$current.device_id"])
                assertFalse(storage.values["$current.distinct_id"] == "legacy-user")
                assertFalse(storage.values.keys.any { it.startsWith("atbl.key.staging") })
            } finally {
                client.close()
            }
        }

    @Test
    fun `partial migration resumes the recorded source after environment changes`() =
        runTest {
            val storage = MapStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.device_id"] = "legacy-device"
            storage.values["$legacy.distinct_id"] = "legacy-user"
            storage.failWriteKey = "$current.distinct_id"
            val failed =
                AltertableClient(
                    AltertableConfig("key", "staging", dispatcher = StandardTestDispatcher(testScheduler)),
                    storage,
                )
            runCurrent()
            failed.close()
            storage.failWriteKey = null
            val resumed =
                AltertableClient(
                    AltertableConfig("key", "production", dispatcher = StandardTestDispatcher(testScheduler)),
                    storage,
                )
            try {
                runCurrent()
                assertEquals("legacy-user", storage.values["$current.distinct_id"])
                assertFalse(storage.values.keys.any { it.startsWith(legacy) })
                assertFalse(storage.values.containsKey("$current.legacy_migration"))
            } finally {
                resumed.close()
            }
        }

    @Test
    fun `failed queue cleanup leaves a marker until retry completes`() =
        runTest {
            val storage = MapStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.queue"] = "[]"
            storage.failRemoveKey = "$legacy.queue"
            val failed =
                AltertableClient(
                    AltertableConfig("key", "staging", dispatcher = StandardTestDispatcher(testScheduler)),
                    storage,
                )
            runCurrent()
            failed.close()
            assertEquals(legacy, storage.values["$current.legacy_migration"])
            storage.failRemoveKey = null
            val resumed =
                AltertableClient(
                    AltertableConfig("key", "production", dispatcher = StandardTestDispatcher(testScheduler)),
                    storage,
                )
            try {
                runCurrent()
                assertFalse(storage.values.containsKey("$legacy.queue"))
                assertFalse(storage.values.containsKey("$current.legacy_migration"))
            } finally {
                resumed.close()
            }
        }

    @Test
    fun `failed marker write leaves legacy state untouched`() =
        runTest {
            val storage = MapStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.distinct_id"] = "legacy-user"
            storage.failWriteKey = "$current.legacy_migration"
            val client =
                AltertableClient(
                    AltertableConfig("key", "staging", dispatcher = StandardTestDispatcher(testScheduler)),
                    storage,
                )
            try {
                runCurrent()
                client.track("ignored-after-failed-initialization")
                client.identify("ignored-after-failed-initialization")
                client.alias("ignored-after-failed-initialization")
                client.flush()
                runCurrent()
                assertEquals("legacy-user", storage.values["$legacy.distinct_id"])
                assertFalse(storage.values.containsKey("$current.distinct_id"))
                assertFalse(storage.values.containsKey("$current.legacy_migration"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `immediate state calls wait for initialization and omit deprecated environment`() =
        runTest {
            val storage = BlockingStorage()
            val legacy = buildLegacyStorageKeyPrefix("key", "staging")
            val current = buildStorageKeyPrefix("key")
            storage.values["$legacy.device_id"] = "legacy-device"
            val client =
                AltertableClient(
                    AltertableConfig(
                        apiKey = "key",
                        environment = "staging",
                        tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                        dispatcher = StandardTestDispatcher(testScheduler),
                    ),
                    storage,
                )
            try {
                runCurrent()
                storage.readStarted.await()
                client.track("queued")
                client.identify("known-user")
                client.alias("alias")
                client.flush()
                runCurrent()
                assertEquals(1, storage.getCalls)

                storage.releaseReads.complete(Unit)
                advanceUntilIdle()

                assertEquals("legacy-device", storage.values["$current.device_id"])
                val queued = client.eventQueue.flush()
                assertEquals(3, queued.size)
                queued.forEach { payload ->
                    when (payload) {
                        is ApiPayload.Track -> assertNull(payload.payload.environment)
                        is ApiPayload.Identify -> assertNull(payload.payload.environment)
                        is ApiPayload.Alias -> assertNull(payload.payload.environment)
                    }
                }
            } finally {
                client.close()
            }
        }
}
