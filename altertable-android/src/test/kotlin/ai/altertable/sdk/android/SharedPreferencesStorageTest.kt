@file:OptIn(ai.altertable.sdk.AltertableInternal::class)

package ai.altertable.sdk.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesStorageTest {
    private lateinit var context: Context
    private lateinit var storage: SharedPreferencesStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = SharedPreferencesStorage.create(context)
    }

    @Test
    fun `test getItem returns null for non-existent key`() = runBlocking {
        assertNull(storage.get("non_existent_key"))
    }

    @Test
    fun `test setItem and getItem round-trip`() = runBlocking {
        storage.set("test_key", "test_value")
        assertEquals("test_value", storage.get("test_key"))
    }

    @Test
    fun `test removeItem removes key`() = runBlocking {
        storage.set("test_key", "test_value")
        storage.remove("test_key")
        assertNull(storage.get("test_key"))
    }

    @Test
    fun `test migrate moves value from one key to another`() = runBlocking {
        storage.set("old_key", "migrated_value")
        storage.migrate("old_key", "new_key")
        assertEquals("migrated_value", storage.get("new_key"))
        assertNull(storage.get("old_key"))
    }

    @Test
    fun `test migrate does nothing if source key does not exist`() = runBlocking {
        storage.migrate("non_existent", "new_key")
        assertNull(storage.get("new_key"))
    }
}
