package ai.altertable.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun trackPayload(eventSuffix: Int) =
    ApiPayload.Track(
        TrackPayload(
            timestamp = "2024-01-01T00:00:00Z",
            event = "event_$eventSuffix",
            environment = "test",
            deviceId = "device-1",
            distinctId = "user-1",
            anonymousId = null,
            sessionId = "session-1",
            properties = kotlinx.serialization.json.buildJsonObject {},
            release = null,
        ),
    )

class EventQueueTest {
    @Test
    fun `enqueue and flush returns items`() =
        runBlocking {
            val queue = EventQueue(maxSize = 10)
            queue.enqueue(trackPayload(1))
            val items = queue.flush()
            assertEquals(1, items.size)
            assertEquals("event_1", (items[0] as ApiPayload.Track).payload.event)
        }

    @Test
    fun `flush clears queue`() =
        runBlocking {
            val queue = EventQueue(maxSize = 10)
            queue.enqueue(trackPayload(1))
            queue.flush()
            val afterFlush = queue.flush()
            assertEquals(0, afterFlush.size)
        }

    @Test
    fun `clear empties queue`() =
        runBlocking {
            val queue = EventQueue(maxSize = 10)
            queue.enqueue(trackPayload(1))
            queue.clear()
            val items = queue.flush()
            assertEquals(0, items.size)
        }

    @Test
    fun `overflow drops oldest`() =
        runBlocking {
            val queue = EventQueue(maxSize = 3)
            queue.enqueue(trackPayload(1))
            queue.enqueue(trackPayload(2))
            queue.enqueue(trackPayload(3))
            queue.enqueue(trackPayload(4)) // Should drop first (event_1)
            val items = queue.flush()
            assertEquals(3, items.size)
            assertEquals("event_2", (items[0] as ApiPayload.Track).payload.event)
            assertEquals("event_3", (items[1] as ApiPayload.Track).payload.event)
            assertEquals("event_4", (items[2] as ApiPayload.Track).payload.event)
        }

    @Test
    fun `concurrent enqueue and flush`() =
        runTest {
            val queue = EventQueue(maxSize = 100)
            repeat(50) { i ->
                queue.enqueue(trackPayload(i))
            }
            val items = queue.flush()
            assertEquals(50, items.size)
            assertTrue(items.all { it is ApiPayload.Track })
        }
}
