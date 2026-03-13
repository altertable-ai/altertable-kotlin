package ai.altertable.sdk

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class InputValidationTest {
    private fun clientWithErrorCapture() = AltertableClient(AltertableConfig(apiKey = "key"))

    @Test
    fun `identify with blank userId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.identify("")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `identify with whitespace userId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.identify("   ")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `alias with blank newUserId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.alias("")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `track with blank event emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.track("")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `track with whitespace event emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.track("   ")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `identify with reserved userId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.identify("null")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `identify with userId exceeding max length emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            val longId = "a".repeat(1025)
            client.identify(longId)
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }

    @Test
    fun `alias with reserved newUserId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            client.identify("valid_user")
            client.alias("undefined")
            val capturedError = withTimeout(1000) { client.errors.first() }
            assertNotNull(capturedError)
            assertInstanceOf(AltertableError::class.java, capturedError)
        }
}
