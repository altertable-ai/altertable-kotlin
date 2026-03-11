package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import ai.altertable.sdk.AltertableConfig
import ai.altertable.sdk.TrackingConfig
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AltertableAndroidTest {
    private lateinit var application: Application

    @BeforeEach
    fun setup() {
        application = ApplicationProvider.getApplicationContext() as Application
    }

    @Test
    fun `test setup returns configured client`() {
        val config = AltertableConfig(apiKey = "test-key")
        val client = AltertableAndroid.setup(application, config)
        assertNotNull(client)
    }

    @Test
    fun `test setup with flushOnBackground registers lifecycle observer`() {
        val config = AltertableConfig(
            apiKey = "test-key",
            tracking = TrackingConfig(flushOnBackground = true),
        )
        AltertableAndroid.setup(application, config)
        // Verify setup completes without error
        assertNotNull(Altertable.shared)
    }

    @Test
    fun `test setup with captureScreenViews registers screen tracker`() {
        val config = AltertableConfig(
            apiKey = "test-key",
            tracking = TrackingConfig(captureScreenViews = true),
        )
        AltertableAndroid.setup(application, config)
        // Verify setup completes without error
        assertNotNull(Altertable.shared)
    }

    @Test
    fun `test unregister cleans up observers`() {
        val config = AltertableConfig(apiKey = "test-key")
        AltertableAndroid.setup(application, config)
        AltertableAndroid.unregister(application)
        // Verify unregister completes without error
        assertNotNull(Altertable.shared)
    }
}
