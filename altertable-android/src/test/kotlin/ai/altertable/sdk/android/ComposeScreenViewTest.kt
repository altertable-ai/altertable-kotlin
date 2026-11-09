package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import ai.altertable.sdk.AltertableConfig
import ai.altertable.sdk.NetworkConfig
import ai.altertable.sdk.TrackingConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ComposeScreenViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `screenView tracks once per provided client and not on recomposition`() {
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()
            val first = createClient(server)
            val second = createClient(server)
            var client by mutableStateOf<Altertable>(first)
            var recompositions by mutableIntStateOf(0)
            try {
                composeRule.setContent {
                    recompositions
                    ProvideAltertable(client) {
                        Layout(
                            content = {},
                            modifier = Modifier.screenView("Home"),
                        ) { _, constraints ->
                            layout(constraints.minWidth, constraints.minHeight) {}
                        }
                    }
                }
                composeRule.waitForIdle()
                first.awaitFlush()
                assertEquals(1, server.requestCount)

                composeRule.runOnIdle { client = second }
                composeRule.waitForIdle()
                second.awaitFlush()
                assertEquals(2, server.requestCount)

                composeRule.runOnIdle { recompositions++ }
                composeRule.waitForIdle()
                second.awaitFlush()
                assertEquals(2, server.requestCount)
                requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            } finally {
                first.close()
                second.close()
                server.shutdown()
            }
        }
    }

    private fun createClient(server: MockWebServer): Altertable =
        Altertable.create(
            AltertableConfig(
                apiKey = "key",
                network = NetworkConfig(baseUrl = server.url("/").toString().trimEnd('/')),
                tracking = TrackingConfig(flushEventThreshold = 1, flushIntervalMs = 60_000),
            ),
        )
}
