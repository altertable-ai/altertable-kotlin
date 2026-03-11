package ai.altertable.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityManagerTest {
    @Test
    fun testIdentifyAndReset() {
        val storage = InMemoryStorage()
        val manager = IdentityManager(storage, "test-api-key", "test-env")

        val initialDistinctId = manager.distinctId
        val initialDeviceId = manager.deviceId
        assertNull(manager.anonymousId)
        assertTrue(initialDistinctId.startsWith("anonymous-"))
        assertTrue(initialDeviceId.startsWith("device-"))

        manager.identify("user-123")
        assertEquals("user-123", manager.distinctId)
        assertEquals(initialDistinctId, manager.anonymousId)

        manager.reset(resetDeviceId = true)
        assertNotEquals(initialDistinctId, manager.distinctId)
        assertNotEquals(initialDeviceId, manager.deviceId)
        assertNull(manager.anonymousId)
    }

    @Test
    fun testReservedIds() {
        val storage = InMemoryStorage()
        val manager = IdentityManager(storage, "test-api-key", "test-env")

        val initialDistinctId = manager.distinctId
        manager.identify("null")
        // Should not identify because 'null' is reserved
        assertEquals(initialDistinctId, manager.distinctId)
    }
}
