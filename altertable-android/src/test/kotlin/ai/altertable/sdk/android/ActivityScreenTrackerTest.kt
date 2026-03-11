package ai.altertable.sdk.android

import ai.altertable.sdk.AltertableClient
import ai.altertable.sdk.AltertableConfig
import android.app.Activity
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ActivityScreenTrackerTest {
    private lateinit var client: AltertableClient
    private lateinit var tracker: ActivityScreenTracker

    @BeforeEach
    fun setup() {
        val config = AltertableConfig(
            apiKey = "test-key",
            trackingConsent = ai.altertable.sdk.TrackingConsentState.PENDING,
        )
        client = AltertableClient(config)
        tracker = ActivityScreenTracker(client)
    }

    @Test
    fun `test onActivityResumed calls screen without crashing`() {
        val activity = Robolectric.setupActivity(TestActivity::class.java)
        tracker.onActivityResumed(activity)
        // Verify the call completes without error
        assertNotNull(tracker)
    }

    @Test
    fun `test tracker handles activity lifecycle callbacks`() {
        val activity = Robolectric.setupActivity(TestActivity::class.java)
        tracker.onActivityCreated(activity, null)
        tracker.onActivityStarted(activity)
        tracker.onActivityResumed(activity)
        tracker.onActivityPaused(activity)
        tracker.onActivityStopped(activity)
        tracker.onActivitySaveInstanceState(activity, null)
        tracker.onActivityDestroyed(activity)
        // Verify all callbacks complete without error
        assertNotNull(tracker)
    }
}

class TestActivity : Activity()
