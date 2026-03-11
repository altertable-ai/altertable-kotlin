package ai.altertable.sdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private fun trackPayload() =
    ApiPayload.Track(
        TrackPayload(
            timestamp = "2024-01-01T00:00:00Z",
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
                baseUrl = "https://api.example.com",
                environment = "test",
            )
        val transport = Transport(config, engine)
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
                baseUrl = "https://api.example.com",
                environment = "test",
            )
        val transport = Transport(config, engine)
        try {
            val error =
                assertThrows(AltertableError.Api::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
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
                baseUrl = "https://api.example.com",
                environment = "test",
            )
        val transport = Transport(config, engine)
        try {
            val error =
                assertThrows(AltertableError.Api::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
            assertEquals(500, error.status)
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
                baseUrl = "https://api.example.com",
                environment = "test",
                maxRetries = 2,
            )
        val transport = Transport(config, engine)
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
                baseUrl = "https://api.example.com",
                environment = "test",
            )
        val transport = Transport(config, engine)
        try {
            val error =
                assertThrows(AltertableError.Api::class.java) {
                    runBlocking { transport.post(trackPayload()) }
                }
            assertEquals(400, error.status)
        } finally {
            transport.close()
        }
    }
}
