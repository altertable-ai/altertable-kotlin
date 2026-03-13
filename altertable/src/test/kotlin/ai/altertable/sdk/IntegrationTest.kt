package ai.altertable.sdk

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.Socket

@Tag("integration")
class IntegrationTest {
    companion object {
        private const val BASE_URL = "http://localhost:15001"
        private const val MOCK_PORT = 15001

        @JvmStatic
        @BeforeAll
        fun setup() {
            assumeTrue(
                isMockAvailable(),
                "Altertable mock not available at localhost:$MOCK_PORT. " +
                    "Start with: docker run -p $MOCK_PORT:$MOCK_PORT ghcr.io/altertable-ai/altertable-mock:latest",
            )
        }

        private fun isMockAvailable(): Boolean =
            try {
                Socket("localhost", MOCK_PORT).close()
                true
            } catch (_: Exception) {
                false
            }
    }

    @Test
    fun `test track, identify, alias success`() =
        runBlocking {
            val config =
                AltertableConfig(
                    apiKey = "valid_api_key",
                    environment = "production",
                    network = NetworkConfig(baseUrl = BASE_URL, maxRetries = 0),
                )
            val client = AltertableClient(config)
            try {
                client.identify("user_123")
                delay(300)
                client.track("Item Viewed", mapOf("item_id" to "123"))
                client.alias("new_user_123")

                delay(1500)

                val caughtError = withTimeoutOrNull(100) { client.errors.first() }
                val errorMsg = "An error occurred: ${caughtError?.message}"
                assertTrue(caughtError == null, errorMsg)
            } finally {
                client.close()
            }
        }

    @Test
    fun `test invalid environment response is stable`() =
        runBlocking {
            val config =
                AltertableConfig(
                    apiKey = "valid_api_key",
                    environment = "missing_env",
                    network = NetworkConfig(baseUrl = BASE_URL, maxRetries = 0),
                )
            val client = AltertableClient(config)
            try {
                client.track("Item Viewed")
                delay(1500)

                val apiError =
                    withTimeoutOrNull(1500) {
                        client.errors.first { it is AltertableError.Api } as? AltertableError.Api
                    }
                assertTrue(
                    apiError == null || apiError.status == 400 || apiError.status == 422,
                    "Expected null or 400/422 for invalid environment, got ${apiError?.status}",
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `test invalid api key response is stable`() =
        runBlocking {
            val config =
                AltertableConfig(
                    apiKey = "invalid_key",
                    network = NetworkConfig(baseUrl = BASE_URL, maxRetries = 0),
                )
            val client = AltertableClient(config)
            try {
                client.track("Item Viewed")
                delay(1500)

                val apiError =
                    withTimeoutOrNull(1500) {
                        client.errors.first { it is AltertableError.Api } as? AltertableError.Api
                    }
                assertTrue(
                    apiError == null || apiError.status == 401 || apiError.status == 403,
                    "Expected null or 401/403 for invalid API key, got ${apiError?.status}",
                )
            } finally {
                client.close()
            }
        }
}
