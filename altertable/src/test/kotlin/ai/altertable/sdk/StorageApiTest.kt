package ai.altertable.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageApiTest {
    @Test
    fun testInMemoryStorage() {
        val storage = InMemoryStorage()
        assertNull(storage["key1"])

        storage["key1"] = "value1"
        assertEquals("value1", storage["key1"])

        storage["key2"] = "value2"
        assertEquals("value2", storage["key2"])

        storage.remove("key1")
        assertNull(storage["key1"])

        storage.migrate("key2", "key3")
        assertNull(storage["key2"])
        assertEquals("value2", storage["key3"])
    }
}
