package ai.altertable.sdk.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesStorageTest {
    private lateinit var context: Context
    private lateinit var storage: SharedPreferencesStorage

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = SharedPreferencesStorage.create(context)
    }

    @Test
    fun `test getItem returns null for non-existent key`() {
        assertNull(storage["non_existent_key"])
    }

    @Test
    fun `test setItem and getItem round-trip`() {
        storage["test_key"] = "test_value"
        assertEquals("test_value", storage["test_key"])
    }

    @Test
    fun `test removeItem removes key`() {
        storage["test_key"] = "test_value"
        storage.remove("test_key")
        assertNull(storage["test_key"])
    }

    @Test
    fun `test migrate moves value from one key to another`() {
        storage["old_key"] = "migrated_value"
        storage.migrate("old_key", "new_key")
        assertEquals("migrated_value", storage["new_key"])
        assertNull(storage["old_key"])
    }

    @Test
    fun `test migrate does nothing if source key does not exist`() {
        storage.migrate("non_existent", "new_key")
        assertNull(storage["new_key"])
    }
}
