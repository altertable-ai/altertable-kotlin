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
            try {
                client.identify("")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `identify with whitespace userId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.identify("   ")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `alias with blank newUserId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.alias("")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `track with blank event emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.track("")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `track with whitespace event emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.track("   ")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `identify with reserved userId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.identify("null")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `identify with userId exceeding max length emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                val longId = "a".repeat(1025)
                client.identify(longId)
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }

    @Test
    fun `alias with reserved newUserId emits error`() =
        runBlocking {
            val client = clientWithErrorCapture()
            try {
                client.identify("valid_user")
                client.alias("undefined")
                val capturedError = withTimeout(1000) { client.errors.first() }
                assertNotNull(capturedError)
                assertInstanceOf(AltertableError::class.java, capturedError)
            } finally {
                client.close()
            }
        }
}
