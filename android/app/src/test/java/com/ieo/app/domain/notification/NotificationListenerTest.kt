package com.ieo.app.domain.notification

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationListenerTest {

    /** 연결할 때마다 다음 대본을 쓴다. 대본이 떨어지면 붙은 채로 기다린다 */
    private class ScriptedServer(vararg scripts: Flow<NotificationStreamEvent>) : NotificationRepository {
        private val scripts = ArrayDeque(scripts.toList())
        val afters = mutableListOf<Long?>()

        override fun stream(after: Long?): Flow<NotificationStreamEvent> {
            afters += after
            return scripts.removeFirstOrNull() ?: flow {
                emit(NotificationStreamEvent.Connected)
                awaitCancellation()
            }
        }
    }

    private class MemoryStore(override var lastId: Long? = null) : LastNotificationStore

    private fun notification(id: Long, type: NotificationType = NotificationType.DISPATCH_CONFIRMED) =
        AppNotification(id, type, "알림 $id", dispatchRequestId = 1, orderId = 1, createdAt = Instant.EPOCH)

    private fun received(vararg ids: Long) = ids.map { NotificationStreamEvent.Received(notification(it)) }

    private fun TestScope.listening(listener: NotificationListener): MutableList<AppNotification> {
        val got = mutableListOf<AppNotification>()
        backgroundScope.launch { listener.received.collect { got += it } }
        runCurrent()
        return got
    }

    @Test
    fun `받은 알림을 내보내고 서버가 닫으면 마지막 id로 다시 붙어 같은 알림은 한 번만 내보낸다`() = runTest {
        val server = ScriptedServer(
            flow {
                emit(NotificationStreamEvent.Connected)
                received(3, 4).forEach { emit(it) }
            },
            flow {
                emit(NotificationStreamEvent.Connected)
                received(4, 5).forEach { emit(it) }
                awaitCancellation()
            },
        )
        val store = MemoryStore(lastId = 2)
        val listener = NotificationListener(backgroundScope, server, store)
        val got = listening(listener)

        listener.start()
        runCurrent()
        assertEquals(listOf(3L, 4L), got.map { it.id })
        assertEquals(listOf<Long?>(2), server.afters)

        advanceTimeBy(NotificationListener.RECONNECT_DELAY_MS)
        runCurrent()
        assertEquals(listOf<Long?>(2, 4), server.afters)
        assertEquals(listOf(3L, 4L, 5L), got.map { it.id })
        assertEquals(5L, store.lastId)
    }

    @Test
    fun `화면이 지켜보기 전에 온 알림은 잃지 않고 기다렸다가 내보낸다`() = runTest {
        val server = ScriptedServer(
            flow {
                emit(NotificationStreamEvent.Connected)
                received(4).forEach { emit(it) }
                awaitCancellation()
            },
        )
        val store = MemoryStore(lastId = 3)
        val listener = NotificationListener(backgroundScope, server, store)

        listener.start()
        runCurrent()
        assertEquals(3L, store.lastId)

        val got = listening(listener)
        runCurrent()

        assertEquals(listOf(4L), got.map { it.id })
        assertEquals(4L, store.lastId)
    }

    @Test
    fun `연결이 실패하면 점점 오래 기다렸다가 다시 붙는다`() = runTest {
        val failing = flow<NotificationStreamEvent> { throw IOException("끊김") }
        val server = ScriptedServer(failing, failing)
        val listener = NotificationListener(backgroundScope, server, MemoryStore())
        listening(listener)

        listener.start()
        runCurrent()
        assertEquals(1, server.afters.size)

        advanceTimeBy(NotificationListener.RETRY_DELAYS_MS[0])
        runCurrent()
        assertEquals(2, server.afters.size)

        advanceTimeBy(NotificationListener.RETRY_DELAYS_MS[1] - 1)
        runCurrent()
        assertEquals(2, server.afters.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, server.afters.size)
    }

    @Test
    fun `로그아웃하면 끊고 마지막 id를 지운다`() = runTest {
        val store = MemoryStore(lastId = 9)
        val listener = NotificationListener(backgroundScope, ScriptedServer(), store)
        listener.start()
        runCurrent()

        listener.stop()

        assertFalse(listener.isRunning)
        assertNull(store.lastId)
    }
}
