package ai.altertable.sdk

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(AltertableInternal::class)
class AltertableClientLifecycleTest {
    @Test
    fun `setup returns configured client`() {
        val config = AltertableConfig(apiKey = "test-key")
        val client = Altertable.setup(config)
        assertNotNull(client)
        assertTrue(client.trackingConsent.value == TrackingConsent.GRANTED)
    }

    @Test
    fun `shared returns same instance as setup`() {
        val config = AltertableConfig(apiKey = "test-key")
        val client = Altertable.setup(config)
        val shared = Altertable.shared
        assertNotNull(shared)
        assertTrue(client === shared)
    }

    @Test
    fun `double setup replaces instance`() {
        val client1 = Altertable.setup(AltertableConfig(apiKey = "key1"))
        val client2 = Altertable.setup(AltertableConfig(apiKey = "key2"))
        val shared = Altertable.shared
        assertNotNull(shared)
        assertTrue(client2 === shared)
        assertTrue(client1 !== client2)
    }

    @Test
    fun `setup with blank apiKey throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Altertable.setup(AltertableConfig(apiKey = ""))
        }
    }

    @Test
    fun `setup with blank apiKey throws for whitespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            Altertable.setup(AltertableConfig(apiKey = "   "))
        }
    }
}
