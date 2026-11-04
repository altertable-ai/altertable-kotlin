@file:OptIn(AltertableInternal::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Applicable wire, storage, transform, consent and lifecycle cases from the JS audience suite. */
class AudienceTest {
    private class ForbiddenStorage : Storage {
        override suspend fun get(key: String): String? = error("Audience read persistent storage: $key")

        override suspend fun set(
            key: String,
            value: String,
        ): Unit = error("Audience wrote persistent storage: $key")

        override suspend fun remove(key: String): Unit = error("Audience removed persistent storage: $key")

        override suspend fun migrate(
            from: String,
            to: String,
        ): Unit = error("Audience migrated persistent storage")
    }

    @Suppress("LongParameterList")
    private class Harness(
        scope: TestScope,
        mode: TrackingMode = TrackingMode.AUDIENCE,
        consent: TrackingConsent = TrackingConsent.GRANTED,
        hooks: List<EventInterceptor> = emptyList(),
        storage: Storage = ForbiddenStorage(),
        threshold: Int = 20,
        maxBatchSize: Int = 20,
        retries: Int = 0,
        provider: ContextProvider = ContextProviderStub,
        status: (Int) -> HttpStatusCode = { HttpStatusCode.OK },
        beforeResponse: suspend (Int) -> Unit = {},
    ) : AutoCloseable {
        val requests = mutableListOf<List<JsonObject>>()
        val endpoints = mutableListOf<String>()
        val client =
            AltertableClient(
                AltertableConfig(
                    apiKey = "key",
                    mode = mode,
                    release = "1.2.3",
                    dispatcher = StandardTestDispatcher(scope.testScheduler),
                    tracking =
                        TrackingConfig(
                            consent = consent,
                            flushEventThreshold = threshold,
                            flushIntervalMs = 1_000_000,
                            maxBatchSize = maxBatchSize,
                        ),
                    network = NetworkConfig(maxRetries = retries),
                    beforeSend = hooks,
                ),
                storage,
                provider,
                MockEngine { request ->
                    endpoints += request.url.encodedPath
                    val body = (request.body as TextContent).text
                    requests += Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
                    beforeResponse(requests.size)
                    respond("", status(requests.size))
                },
            )
        val events: List<JsonObject> get() = requests.flatten()

        override fun close() = client.close()
    }

    private object ContextProviderStub : ContextProvider {
        override fun getSystemProperties(): Map<String, Any> = error("Audience collected automatic device context")
    }

    private fun assertAudience(event: JsonObject) {
        assertEquals(setOf("mode", "event", "timestamp", "properties"), event.keys)
        assertEquals("audience", event["mode"]?.jsonPrimitive?.content)
        assertFalse(event["properties"]!!.jsonObject.containsKey(ContextProperties.VIEWPORT))
    }

    @Test
    fun `wire omits identity environment and release and suppresses identity calls`() =
        runTest {
            Harness(this).use { h ->
                runCurrent()
                h.client.identify("user", mapOf("email" to "private@example.com"))
                h.client.alias("alias")
                h.client.updateTraits(mapOf("name" to "private"))
                h.client.track("Opened", mapOf("section" to "docs"))
                h.client.screen("Home")
                runCurrent()
                h.client.awaitFlush()
                assertEquals(listOf("/track"), h.endpoints)
                assertEquals(listOf("Opened", EVENT_SCREEN_VIEW), h.events.map { it["event"]!!.jsonPrimitive.content })
                h.events.forEach(::assertAudience)
                val screen = h.events.last()["properties"]!!.jsonObject
                assertEquals("Home", screen[ContextProperties.SCREEN_NAME]!!.jsonPrimitive.content)
            }
        }

    @Test
    fun `interceptors cannot restore restricted context and can retain custom properties`() =
        runTest {
            val hook: EventInterceptor = { event ->
                (event as Event.Track).copy(
                    properties =
                        event.properties +
                            mapOf(
                                "\$url" to "https://user:password@site.example/docs?token=secret#hash",
                                "\$referer" to "https://search.example/private?token=secret",
                                ContextProperties.VIEWPORT to "1234x987",
                                "email" to "caller-owned@example.com",
                            ),
                )
            }
            Harness(this, hooks = listOf(hook)).use { h ->
                runCurrent()
                h.client.superProperties.setValue("section", "intro")
                h.client.track("Opened")
                runCurrent()
                h.client.awaitFlush()
                val event = h.events.single()
                assertAudience(event)
                val props = event["properties"]!!.jsonObject
                assertEquals("https://site.example/docs", props["\$url"]!!.jsonPrimitive.content)
                assertEquals("https://search.example", props["\$referer"]!!.jsonPrimitive.content)
                assertEquals("intro", props["section"]!!.jsonPrimitive.content)
                assertEquals("caller-owned@example.com", props["email"]!!.jsonPrimitive.content)
                assertFalse(event.toString().contains("secret"))
            }
        }

    @Test
    fun `invalid urls are nulled and viewport removed after runtime hooks`() =
        runTest {
            Harness(this).use { h ->
                runCurrent()
                for (url in listOf("javascript:secret", "/relative?token=secret", "not a URL", "https://")) {
                    h.client.configure {
                        beforeSend =
                            listOf {
                                (it as Event.Track).copy(
                                    properties =
                                        mapOf(
                                            "\$url" to url,
                                            "\$referer" to url,
                                            "\$viewport" to "1x1",
                                        ),
                                )
                            }
                    }
                    runCurrent()
                    h.client.track("Invalid")
                    runCurrent()
                }
                h.client.awaitFlush()
                assertEquals(4, h.events.size)
                h.events.forEach {
                    assertAudience(it)
                    assertEquals(JsonNull, it["properties"]!!.jsonObject["\$url"])
                    assertEquals(JsonNull, it["properties"]!!.jsonObject["\$referer"])
                }
            }
        }

    @Test
    fun `pending and dismissed events wait for grant and are transformed only once`() =
        runTest {
            for (consent in listOf(TrackingConsent.PENDING, TrackingConsent.DISMISSED)) {
                var transforms = 0
                Harness(
                    this,
                    consent = consent,
                    hooks =
                        listOf {
                            transforms++
                            it
                        },
                ).use { h ->
                    runCurrent()
                    h.client.track("Waiting")
                    runCurrent()
                    h.client.flush()
                    runCurrent()
                    h.client.awaitFlush()
                    assertTrue(h.events.isEmpty())
                    h.client.configure { tracking { this.consent = TrackingConsent.GRANTED } }
                    runCurrent()
                    h.client.awaitFlush()
                    assertEquals(
                        "Waiting",
                        h.events
                            .single()["event"]!!
                            .jsonPrimitive.content,
                    )
                    assertEquals(1, transforms)
                    assertAudience(h.events.single())
                }
            }
        }

    @Test
    fun `denial clears pending and buffered events and regrant resumes collection`() =
        runTest {
            Harness(this, consent = TrackingConsent.PENDING).use { h ->
                runCurrent()
                h.client.track("Pending")
                runCurrent()
                h.client.configure { tracking { consent = TrackingConsent.DENIED } }
                runCurrent()
                h.client.track("Denied")
                runCurrent()
                h.client.configure { tracking { consent = TrackingConsent.GRANTED } }
                runCurrent()
                h.client.track("Buffered")
                runCurrent()
                h.client.configure { tracking { consent = TrackingConsent.DENIED } }
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.events.isEmpty())
                h.client.configure { tracking { consent = TrackingConsent.GRANTED } }
                runCurrent()
                h.client.track("Resumed")
                runCurrent()
                h.client.awaitFlush()
                assertEquals(
                    "Resumed",
                    h.events
                        .single()["event"]!!
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun `grant to pending pauses buffered delivery until grant`() =
        runTest {
            Harness(this).use { h ->
                runCurrent()
                h.client.track("Buffered")
                runCurrent()
                h.client.configure { tracking { consent = TrackingConsent.PENDING } }
                runCurrent()
                h.client.track("Waiting")
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.events.isEmpty())
                h.client.configure { tracking { consent = TrackingConsent.GRANTED } }
                runCurrent()
                h.client.awaitFlush()
                assertEquals(setOf("Buffered", "Waiting"), h.events.map { it["event"]!!.jsonPrimitive.content }.toSet())
            }
        }

    @Test
    fun `audience ignores product persisted state and never replays across instances`() =
        runTest {
            val storage = InMemoryStorage()
            Harness(
                this,
                mode = TrackingMode.PRODUCT,
                consent = TrackingConsent.PENDING,
                storage = storage,
                provider = DefaultContextProvider,
            ).use { product ->
                runCurrent()
                product.client.track("Persisted Product")
                runCurrent()
            }
            Harness(this, storage = storage, consent = TrackingConsent.PENDING).use { first ->
                runCurrent()
                first.client.track("Discarded Audience")
                runCurrent()
            }
            Harness(this, storage = storage).use { second ->
                runCurrent()
                assertEquals(TrackingConsent.GRANTED, second.client.trackingConsent.value)
                second.client.track("New Audience")
                runCurrent()
                second.client.awaitFlush()
                assertEquals(
                    "New Audience",
                    second.events
                        .single()["event"]!!
                        .jsonPrimitive.content,
                )
                assertAudience(second.events.single())
            }
            Harness(
                this,
                mode = TrackingMode.PRODUCT,
                consent = TrackingConsent.PENDING,
                storage = storage,
                provider = DefaultContextProvider,
            ).use { product ->
                runCurrent()
                product.client.configure { tracking { consent = TrackingConsent.GRANTED } }
                runCurrent()
                product.client.awaitFlush()
                assertEquals(
                    "Persisted Product",
                    product.events
                        .single()["event"]!!
                        .jsonPrimitive.content,
                )
                assertTrue(product.events.single().containsKey("device_id"))
                assertFalse(product.events.single().containsKey("mode"))
            }
        }

    @Test
    fun `batch chunks retries and permanent failures retain final audience policy in memory`() =
        runTest {
            Harness(
                this,
                threshold = 3,
                maxBatchSize = 2,
                retries = 1,
                status = { if (it == 1) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK },
            ).use { h ->
                runCurrent()
                repeat(3) { h.client.track("Event $it") }
                runCurrent()
                h.client.awaitFlush()
                assertEquals(3, h.requests.size)
                assertTrue(h.requests.all { it.size <= 2 })
                h.events.forEach(::assertAudience)
            }
            Harness(this, status = { HttpStatusCode.BadRequest }).use { h ->
                runCurrent()
                h.client.track("Retained")
                runCurrent()
                h.client.awaitFlush()
                val queued =
                    h.client.eventQueue
                        .flush()
                        .single() as ApiPayload.Track
                assertEquals("audience", queued.payload.mode)
                assertNull(queued.payload.distinctId)
            }
        }

    @Test
    fun `denial cancels in flight audience delivery and does not replay on regrant`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            Harness(this, threshold = 1, retries = 1, beforeResponse = { count ->
                if (count == 1) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }).use { h ->
                runCurrent()
                h.client.track("Canceled")
                entered.await()
                h.client.configure { tracking { consent = TrackingConsent.DENIED } }
                runCurrent()
                h.client.configure { tracking { consent = TrackingConsent.GRANTED } }
                runCurrent()
                h.client.track("Resumed")
                runCurrent()
                h.client.awaitFlush()
                assertEquals(listOf("Canceled", "Resumed"), h.events.map { it["event"]!!.jsonPrimitive.content })
                assertTrue(
                    h.client.eventQueue
                        .flush()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun `reset uses memory and clears queues while optionally restoring consent`() =
        runTest {
            Harness(this, consent = TrackingConsent.PENDING).use { h ->
                runCurrent()
                h.client.track("Discarded")
                runCurrent()
                h.client.reset(resetDeviceId = true, resetTrackingConsent = true)
                runCurrent()
                h.client.track("After Reset")
                runCurrent()
                h.client.awaitFlush()
                assertEquals(
                    "After Reset",
                    h.events
                        .single()["event"]!!
                        .jsonPrimitive.content,
                )
                assertAudience(h.events.single())
            }
        }

    @Test
    fun `hook may drop or return identity event without emitting identity payload`() =
        runTest {
            Harness(this, hooks = listOf { null }).use { h ->
                runCurrent()
                h.client.track("Dropped")
                runCurrent()
                h.client.awaitFlush()
                assertTrue(h.events.isEmpty())
                h.client.configure { beforeSend = listOf { Event.Identify("injected", emptyMap()) } }
                runCurrent()
                h.client.track("Track Only")
                runCurrent()
                h.client.awaitFlush()
                assertAudience(h.events.single())
                assertEquals(listOf("/track"), h.endpoints)
            }
        }

    @Test
    fun `mode builders default to product and runtime updates preserve audience`() {
        val default = AltertableConfigBuilder().apply { apiKey = "key" }.build()
        assertEquals(TrackingMode.PRODUCT, default.mode)
        val audience = AltertableConfigBuilder().from(default).apply { mode = TrackingMode.AUDIENCE }.build()
        assertEquals(TrackingMode.AUDIENCE, AltertableConfigBuilder().from(audience).build().mode)
        val updated = RuntimeConfigBuilder().from(audience).apply { debug = true }.applyTo(audience)
        assertEquals(TrackingMode.AUDIENCE, updated.mode)
        assertFalse(RuntimeConfigBuilder::class.java.methods.any { it.name == "setMode" })
    }

    @Test
    fun `identity calls warn only in debug and never invoke hooks`() =
        runTest {
            val logs = mutableListOf<Pair<LogLevel, String>>()
            val client =
                AltertableClient(
                    AltertableConfig(
                        apiKey = "key",
                        mode = TrackingMode.AUDIENCE,
                        dispatcher = StandardTestDispatcher(testScheduler),
                        tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                        logger = AltertableLogger { level, message -> logs += level to message },
                        beforeSend = listOf { error("Identity hook must not run") },
                    ),
                    ForbiddenStorage(),
                )
            try {
                runCurrent()
                client.identify("user")
                client.updateTraits(emptyMap())
                client.alias("user")
                assertTrue(logs.isEmpty())
                client.configure { debug = true }
                runCurrent()
                client.identify("user")
                client.updateTraits(emptyMap())
                client.alias("user")
                assertEquals(3, logs.size)
                assertTrue(logs.all { it.first == LogLevel.WARN && it.second.contains("audience") })
            } finally {
                client.close()
            }
        }

    @Test
    fun `setup replaces modes and closes previous integrations`() =
        runTest {
            val closed = mutableListOf<TrackingMode>()
            val integration = AltertableIntegration { _, config -> java.io.Closeable { closed += config.mode } }

            fun config(mode: TrackingMode) =
                AltertableConfig(
                    apiKey = "key",
                    mode = mode,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    tracking = TrackingConfig(consent = TrackingConsent.PENDING),
                    integrations = listOf(integration),
                )
            val product = Altertable.setup(config(TrackingMode.PRODUCT))
            runCurrent()
            product.track("Discarded Product")
            runCurrent()
            val audience = Altertable.setup(config(TrackingMode.AUDIENCE), ForbiddenStorage())
            runCurrent()
            audience.track("Discarded Audience")
            runCurrent()
            val next = Altertable.setup(config(TrackingMode.PRODUCT)) as AltertableClient
            try {
                runCurrent()
                assertEquals(listOf(TrackingMode.PRODUCT, TrackingMode.AUDIENCE), closed)
                assertTrue(next.eventQueue.flush().isEmpty())
                assertTrue(next === Altertable.shared)
            } finally {
                next.close()
            }
        }

    @Test
    fun `legacy persisted product payload still decodes and emits unchanged wire fields`() {
        val legacy =
            """
            [{"type":"track","payload":{
              "timestamp":"2026-11-04T10:00:00.000Z","event":"Legacy","environment":"production",
              "device_id":"device-old","distinct_id":"user-old","anonymous_id":"anonymous-old",
              "session_id":"session-old","properties":{},"release":"1.0"
            },"endpoint":"/track"}]
            """.trimIndent()
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }
        val decoded = json.decodeFromString<List<ApiPayload>>(legacy).single() as ApiPayload.Track
        val wire = json.parseToJsonElement(json.encodeToString(decoded.payload)).jsonObject
        assertEquals("user-old", wire["distinct_id"]!!.jsonPrimitive.content)
        assertEquals("production", wire["environment"]!!.jsonPrimitive.content)
        assertFalse(wire.containsKey("mode"))
    }
}
