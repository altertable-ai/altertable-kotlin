@file:OptIn(AltertableInternal::class)

package ai.altertable.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SessionManagerTest {
    @Test
    fun testSessionGenerationAndRenew() = runBlocking {
        val storage = InMemoryStorage()
        val manager = SessionManager(storage, "test-api-key", "test-env")
        manager.initialize()

        val initialSession = manager.sessionId
        assertTrue(initialSession.startsWith("session-"))
        assertEquals(initialSession, manager.getSessionIdAndTouch())

        manager.renewSession()
        val newSession = manager.sessionId
        assertNotEquals(initialSession, newSession)
        assertEquals(newSession, manager.getSessionIdAndTouch())
    }
}
