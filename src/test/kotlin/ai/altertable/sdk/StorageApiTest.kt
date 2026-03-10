package ai.altertable.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageApiTest {

    @Test
    fun testInMemoryStorage() {
        val storage = InMemoryStorage()
        assertNull(storage.getItem("key1"))

        storage.setItem("key1", "value1")
        assertEquals("value1", storage.getItem("key1"))

        storage.setItem("key2", "value2")
        assertEquals("value2", storage.getItem("key2"))

        storage.removeItem("key1")
        assertNull(storage.getItem("key1"))

        storage.migrate("key2", "key3")
        assertNull(storage.getItem("key2"))
        assertEquals("value2", storage.getItem("key3"))
    }
}
