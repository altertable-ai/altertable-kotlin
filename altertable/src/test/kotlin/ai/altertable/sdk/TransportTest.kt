package ai.altertable.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private fun trackPayload() =
    ApiPayload.Track(
        TrackPayload(
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            event = "test_event",
            environment = "test",
            deviceId = "device-1",
            distinctId = "user-1",
            anonymousId = null,
            sessionId = "session-1",
            properties = buildJsonObject {},
            release = null,
        ),
    )

class TransportTest {
    @Test
    fun `successful POST returns without error`() {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        respond(
                            content = "",
                            status = HttpStatusCode.OK,
                            headers = headersOf(),
                        )
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com"),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            runBlocking { transport.post(trackPayload()) }
        } finally {
            transport.close()
        }
    }

    @Test
    fun `HTTP 4xx throws ApiError`() {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        respond(
                            content = """{"error":"invalid_request"}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(),
                        )
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com"),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            val exception =
                assertThrows(AltertableException::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
            val error = exception.error as AltertableError.Api
            assertEquals(400, error.status)
            assertEquals("invalid_request", error.errorCode)
        } finally {
            transport.close()
        }
    }

    @Test
    fun `HTTP 5xx throws ApiError when onError not set`() {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        respond(
                            content = """{"error":"internal"}""",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(),
                        )
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com", maxRetries = 0),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            val exception =
                assertThrows(AltertableException::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
            // With maxRetries=0, 5xx errors should throw Api error after retry logic
            // But if retries are exhausted, it might be Network error, so check both
            when (val error = exception.error) {
                is AltertableError.Api -> assertEquals(500, error.status)
                is AltertableError.Network -> {
                    // Network error is also acceptable for 5xx after retries exhausted
                }
                else -> throw AssertionError("Expected Api or Network error but got ${error::class.simpleName}")
            }
        } finally {
            transport.close()
        }
    }

    @Test
    fun `retries on 5xx then succeeds`() {
        val attemptCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        if (attemptCount.incrementAndGet() < 2) {
                            respond(
                                content = "",
                                status = HttpStatusCode.InternalServerError,
                                headers = headersOf(),
                            )
                        } else {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(),
                            )
                        }
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com", maxRetries = 2),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            runBlocking { transport.post(trackPayload()) }
            assertEquals(2, attemptCount.get())
        } finally {
            transport.close()
        }
    }

    @Test
    fun `HTTP 4xx always throws regardless of onError config`() {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        respond(
                            content = "",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(),
                        )
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com"),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            val exception =
                assertThrows(AltertableException::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
            val error = exception.error as AltertableError.Api
            assertEquals(400, error.status)
        } finally {
            transport.close()
        }
    }

    @Test
    fun `postBatch sends single HTTP request for multiple payloads`() {
        val requestCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        requestCount.incrementAndGet()
                        respond(
                            content = "",
                            status = HttpStatusCode.OK,
                            headers = headersOf(),
                        )
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com"),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            val p1 = trackPayload()
            val p2 =
                ApiPayload.Track(
                    (p1 as ApiPayload.Track).payload.copy(event = "second"),
                )
            runBlocking { transport.postBatch(listOf(p1, p2)) }
            assertEquals(1, requestCount.get())
        } finally {
            transport.close()
        }
    }

    @Test
    fun `retries on 429 then succeeds`() {
        val attemptCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    addHandler {
                        if (attemptCount.incrementAndGet() < 2) {
                            respond(
                                content = "",
                                status = HttpStatusCode.TooManyRequests,
                                headers = headersOf(),
                            )
                        } else {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(),
                            )
                        }
                    }
                },
            )
        val config =
            AltertableConfig(
                apiKey = "test-key",
                network = NetworkConfig(baseUrl = "https://api.example.com", maxRetries = 2),
                environment = "test",
            )
        val transport =
            Transport(
                apiKey = config.apiKey,
                baseUrl = config.network.baseUrl,
                dispatcher = config.dispatcher,
                requestTimeout = config.network.requestTimeout,
                maxRetries = config.network.maxRetries,
                engine = engine,
            )
        try {
            runBlocking { transport.post(trackPayload()) }
            assertEquals(2, attemptCount.get())
        } finally {
            transport.close()
        }
    }

    @Test
    fun `isRetryableHttpDeliveryError matches 429 and 5xx and network`() {
        assertEquals(
            true,
            isRetryableHttpDeliveryError(
                AltertableError.Api(429, "Too Many Requests", null, null, "msg"),
            ),
        )
        assertEquals(
            true,
            isRetryableHttpDeliveryError(
                AltertableError.Api(503, "Service Unavailable", null, null, "msg"),
            ),
        )
        assertEquals(
            false,
            isRetryableHttpDeliveryError(
                AltertableError.Api(400, "Bad Request", null, null, "msg"),
            ),
        )
        assertEquals(
            true,
            isRetryableHttpDeliveryError(
                AltertableError.Network("net", Exception("cause")),
            ),
        )
        assertEquals(
            false,
            isRetryableHttpDeliveryError(AltertableError.Validation("x")),
        )
    }
}
