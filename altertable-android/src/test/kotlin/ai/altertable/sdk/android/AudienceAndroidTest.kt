@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import ai.altertable.sdk.TrackingConsent
import ai.altertable.sdk.TrackingMode
import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AudienceAndroidTest {
    @Test
    fun `audience DSL setup screen lifecycle and background flush leave product preferences intact`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("altertable_sdk", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("product-state", "preserved").commit()
        val before = prefs.all.toMap()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        val client = AltertableAndroid.setup(app) {
            apiKey = "key"
            mode = TrackingMode.AUDIENCE
            dispatcher = StandardTestDispatcher(testScheduler)
            network { baseUrl = server.url("/").toString().trimEnd('/') }
            tracking { consent = TrackingConsent.PENDING }
        }
        try {
            runCurrent()
            val activity = Robolectric.buildActivity(Activity::class.java).setup()
            runCurrent()
            ProcessLifecycleObserver(client).onStop(ProcessLifecycleOwner.get())
            runCurrent()
            client.awaitFlush()
            assertEquals(0, server.requestCount)
            client.identify("ignored")
            client.alias("ignored")
            client.updateTraits(mapOf("email" to "ignored"))
            client.configure { tracking { consent = TrackingConsent.GRANTED } }
            runCurrent()
            client.awaitFlush()
            assertEquals(1, server.requestCount)
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val array = JSONArray(request.body.readUtf8())
            val events = (0 until array.length()).map { array.getJSONObject(it) }
            assertTrue(events.any { it.getString("event") == "\$screen" })
            for (event in events) {
                assertEquals("audience", event.getString("mode"))
                assertEquals(4, event.length())
                val properties = event.getJSONObject("properties")
                for (key in listOf("\$viewport", "\$device", "\$device_model", "\$os_version", "\$app_namespace")) {
                    assertFalse(properties.has(key))
                }
            }
            activity.pause().stop().destroy()
            runCurrent()
            assertEquals(before, prefs.all)
        } finally {
            client.close()
            AltertableAndroid.unregister(app)
            server.shutdown()
        }
    }

    @Test
    fun `repeated setup switches mode and keeps a fresh client lifecycle`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val product = AltertableAndroid.setup(app) { apiKey = "key" }
        val audience = AltertableAndroid.setup(app) { apiKey = "key"; mode = TrackingMode.AUDIENCE }
        try {
            assertNotSame(product, audience)
            assertSame(audience, Altertable.shared)
        } finally {
            audience.close()
            AltertableAndroid.unregister(app)
        }
    }
}
