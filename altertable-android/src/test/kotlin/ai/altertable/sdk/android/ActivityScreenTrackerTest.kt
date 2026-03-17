package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import ai.altertable.sdk.AltertableConfig
import ai.altertable.sdk.TrackingConfig
import ai.altertable.sdk.TrackingConsent
import android.app.Activity
import android.os.Bundle
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ActivityScreenTrackerTest {
    private lateinit var client: Altertable
    private lateinit var tracker: ActivityScreenTracker

    @Before
    fun setup() {
        val config = AltertableConfig(
            apiKey = "test-key",
            tracking = TrackingConfig(consent = TrackingConsent.PENDING),
        )
        client = Altertable.create(config)
        tracker = ActivityScreenTracker(client)
    }

    @Test
    fun `test onActivityResumed calls screen without crashing`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        tracker.onActivityResumed(activity)
        // Verify the call completes without error
        assertNotNull(tracker)
    }

    @Test
    fun `test tracker handles activity lifecycle callbacks`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        tracker.onActivityCreated(activity, null)
        tracker.onActivityStarted(activity)
        tracker.onActivityResumed(activity)
        tracker.onActivityPaused(activity)
        tracker.onActivityStopped(activity)
        tracker.onActivitySaveInstanceState(activity, Bundle())
        tracker.onActivityDestroyed(activity)
        // Verify all callbacks complete without error
        assertNotNull(tracker)
    }
}

class TestActivity : Activity()
