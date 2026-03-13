@file:OptIn(ai.altertable.sdk.AltertableInternal::class)

package ai.altertable.sdk.android

import ai.altertable.sdk.ContextProperties
import androidx.test.core.app.ApplicationProvider
import android.content.Context as AndroidContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AndroidContextProviderTest {
    private lateinit var provider: AndroidContextProvider

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<AndroidContext>()
        provider = AndroidContextProvider(context)
    }

    @Test
    fun `test getSystemProperties includes all required keys`() {
        val properties = provider.getSystemProperties()

        assertTrue(properties.containsKey(ContextProperties.LIB))
        assertTrue(properties.containsKey(ContextProperties.LIB_VERSION))
        assertTrue(properties.containsKey(ContextProperties.APP_NAME))
        assertTrue(properties.containsKey(ContextProperties.APP_VERSION))
        assertTrue(properties.containsKey(ContextProperties.APP_BUILD))
        assertTrue(properties.containsKey(ContextProperties.APP_NAMESPACE))
        assertTrue(properties.containsKey(ContextProperties.OS))
        assertTrue(properties.containsKey(ContextProperties.OS_VERSION))
        assertTrue(properties.containsKey(ContextProperties.DEVICE_MANUFACTURER))
        assertTrue(properties.containsKey(ContextProperties.DEVICE_MODEL))
        assertTrue(properties.containsKey(ContextProperties.DEVICE_NAME))
        assertTrue(properties.containsKey(ContextProperties.DEVICE_TYPE))
    }

    @Test
    fun `test OS is Android`() {
        val properties = provider.getSystemProperties()
        assertEquals("Android", properties[ContextProperties.OS])
    }

    @Test
    fun `test library name matches`() {
        val properties = provider.getSystemProperties()
        assertEquals(ContextProperties.LIBRARY_NAME, properties[ContextProperties.LIB])
    }

    @Test
    fun `test device type is Mobile or Tablet`() {
        val properties = provider.getSystemProperties()
        val deviceType = properties[ContextProperties.DEVICE_TYPE] as String
        assertTrue(deviceType == "Mobile" || deviceType == "Tablet")
    }

    @Test
    fun `test viewport may be present`() {
        val properties = provider.getSystemProperties()
        // Viewport is optional, so we just check it doesn't crash
        assertNotNull(properties)
    }
}
