@file:OptIn(AltertableInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@file:Suppress("DEPRECATION")

package ai.altertable.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvironmentTest {
    /** Storage that can silently drop writes or removals, like a failing custom adapter. */
    private open class MapStorage : Storage {
        val values = mutableMapOf<String, String>()
        var dropWrites = false
        var dropRemovals = false

        override suspend fun get(key: String): String? = values[key]

        override suspend fun set(
            key: String,
            value: String,
        ) {
            if (!dropWrites) values[key] = value
        }

        override suspend fun remove(key: String) {
            if (!dropRemovals) values.remove(key)
        }

        override suspend fun migrate(
            from: String,
            to: String,
        ) {
            values.remove(from)?.let { values[to] = it }
        }
    }

    private class Harness(
        scope: TestScope,
        storage: Storage,
        environment: String? = null,
        consent: TrackingConsent = TrackingConsent.GRANTED,
        mode: TrackingMode = TrackingMode.PRODUCT,
    ) : AutoCloseable {
        val requests = mutableListOf<Pair<String, JsonObject>>()
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val tracking = TrackingConfig(consent = consent)
        val client =
            AltertableClient(
                if (environment == null) {
                    AltertableConfig(apiKey = "key", mode = mode, tracking = tracking, dispatcher = dispatcher)
                } else {
                    AltertableConfig(
                        apiKey = "key",
                        environment = environment,
                        mode = mode,
                        tracking = tracking,
                        dispatcher = dispatcher,
                    )
                },
                storage,
                httpClientEngine =
                    MockEngine { request ->
                        val body = Json.parseToJsonElement((request.body as TextContent).text)
                        body.jsonArray.forEach { requests += request.url.encodedPath to it.jsonObject }
                        respond("")
                    },
            )

        override fun close() = client.close()
    }

    private val current = buildStorageKeyPrefix("key")
    private val staging = buildLegacyStorageKeyPrefix("key", "staging")
    private val production = buildLegacyStorageKeyPrefix("key", "production")

    private fun MapStorage.seedLegacy(
        prefix: String,
        user: String,
    ) {
        values["$prefix.device_id"] = "$user-device"
        values["$prefix.distinct_id"] = user
        values["$prefix.queue"] =
            """
            [{"type":"track","payload":{"timestamp":"2026-11-04T10:00:00.000Z","event":"$user queued",
              "environment":"staging","device_id":"$user-device","distinct_id":"$user",
              "session_id":"legacy-session","properties":{"environment":"business-value"}}}]
            """.trimIndent()
    }

    private val JsonObject.event get() = getValue("event").jsonPrimitive.content

    @Test
    fun `deprecated environment is omitted from every request while nested values survive`() =
        runTest {
            Harness(this, InMemoryStorage(), environment = "staging").use { h ->
                runCurrent()
                h.client.track("Clicked", mapOf("environment" to "business-value"))
                h.client.identify("user-123", mapOf("environment" to "trait-value"))
                h.client.updateTraits(mapOf("plan" to "pro"))
                h.client.alias("alias")
                runCurrent()
                h.client.awaitFlush()
                assertEquals(setOf("/track", "/identify", "/alias"), h.requests.map { it.first }.toSet())
                h.requests.forEach { (_, record) -> assertFalse(record.containsKey("environment")) }
                val track = h.requests.first { it.first == "/track" }.second
                assertEquals("business-value", track["properties"]!!.jsonObject["environment"]!!.jsonPrimitive.content)
            }
        }

    @Test
    fun `selected legacy namespace migrates once and unrelated namespaces stay intact`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(staging, "staging-user")
            storage.seedLegacy(production, "production-user")
            storage.seedLegacy(buildLegacyStorageKeyPrefix("other-key", "staging"), "other-user")
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.awaitFlush()
                val replayed = h.requests.single().second
                assertEquals("staging-user queued", replayed.event)
                assertFalse(replayed.containsKey("environment"))
                assertEquals(
                    "business-value",
                    replayed["properties"]!!.jsonObject["environment"]!!.jsonPrimitive.content,
                )
                assertEquals("staging-user", storage.values["$current.distinct_id"])
                assertFalse(storage.values.keys.any { it.startsWith("$staging.") })
                assertEquals("production-user", storage.values["$production.distinct_id"])
                assertEquals("other-user", storage.values["atbl.other-key.staging.distinct_id"])
            }
            // Removing the option later selects neither another identity nor another queue.
            Harness(this, storage).use { h ->
                runCurrent()
                h.client.track("Next")
                runCurrent()
                h.client.awaitFlush()
                val event = h.requests.single().second
                assertEquals("Next", event.event)
                assertEquals("staging-user", event["distinct_id"]!!.jsonPrimitive.content)
                assertEquals("production-user", storage.values["$production.distinct_id"])
            }
        }

    @Test
    fun `omitted environment selects the legacy production namespace`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(production, "production-user")
            storage.seedLegacy(staging, "staging-user")
            Harness(this, storage, consent = TrackingConsent.PENDING).use {
                runCurrent()
                assertEquals("production-user", storage.values["$current.distinct_id"])
                assertEquals("staging-user", storage.values["$staging.distinct_id"])
            }
        }

    @Test
    fun `existing API-key state wins without merging legacy identity or queue`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(staging, "staging-user")
            storage.values["$current.device_id"] = "current-device"
            storage.values["$current.distinct_id"] = "current-user"
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.requests.isEmpty())
                assertEquals("current-user", storage.values["$current.distinct_id"])
                assertFalse(storage.values["$current.queue"].orEmpty().contains("queued"))
            }
        }

    @Test
    fun `failed copy keeps the source and blocks delivery until a retry succeeds`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(staging, "staging-user")
            storage.dropWrites = true
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.track("Dropped")
                h.client.identify("someone-else")
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.requests.isEmpty())
                assertEquals("staging-user", storage.values["$staging.distinct_id"])
            }
            storage.dropWrites = false
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.awaitFlush()
                assertEquals(
                    "staging-user queued",
                    h.requests
                        .single()
                        .second.event,
                )
                assertEquals("staging-user", storage.values["$current.distinct_id"])
            }
        }

    @Test
    fun `failed source removal never replays the legacy queue`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(staging, "staging-user")
            storage.dropRemovals = true
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.awaitFlush()
                assertEquals(1, h.requests.size)
            }
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.requests.isEmpty())
                assertEquals("staging-user", storage.values["$current.distinct_id"])
            }
        }

    @Test
    fun `audience clients never read or migrate legacy state`() =
        runTest {
            val storage = MapStorage()
            storage.seedLegacy(staging, "staging-user")
            val before = storage.values.toMap()
            Harness(this, storage, environment = "staging", mode = TrackingMode.AUDIENCE).use { h ->
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.requests.isEmpty())
                assertEquals(before, storage.values)
            }
        }

    @Test
    fun `calls made during initialization wait for migrated state`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val storage =
                object : MapStorage() {
                    override suspend fun get(key: String): String? {
                        release.await()
                        return super.get(key)
                    }
                }
            storage.values["$staging.device_id"] = "legacy-device"
            Harness(this, storage, environment = "staging").use { h ->
                runCurrent()
                h.client.track("Early")
                h.client.identify("known-user")
                runCurrent()
                release.complete(Unit)
                runCurrent()
                h.client.awaitFlush()
                assertEquals(2, h.requests.size)
                h.requests.forEach { (_, record) ->
                    assertEquals("legacy-device", record["device_id"]!!.jsonPrimitive.content)
                }
            }
        }
}
