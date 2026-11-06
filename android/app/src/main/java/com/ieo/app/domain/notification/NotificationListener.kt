package com.ieo.app.domain.notification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 로그인한 동안 서버 알림 연결을 붙잡고 있는다 (09 알림, SSE).
 * - 끊기면 다시 붙는다. 연달아 실패하면 [RETRY_DELAYS_MS]만큼 점점 오래 기다린다. 붙으면 처음부터 센다
 * - 다시 붙을 때 마지막으로 받은 id를 주어 놓친 알림부터 받는다. 같은 알림이 두 번 와도 한 번만 내보낸다
 * - 앱 전체에서 하나. 화면은 [received]를 지켜본다. 앱을 켠 직후처럼 지켜보는 화면이 아직 없으면 뜰 때까지 기다렸다가 내보낸다.
 *   내보낸 뒤에야 마지막 id를 올린다. 그래야 내보내기 전에 앱이 꺼져도 다음에 다시 받는다
 */
class NotificationListener(
    private val scope: CoroutineScope,
    private val repository: NotificationRepository,
    private val store: LastNotificationStore,
) {

    private val _received = MutableSharedFlow<AppNotification>(extraBufferCapacity = 16)
    val received: SharedFlow<AppNotification> = _received.asSharedFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job = scope.launch { listen() }
    }

    /** 로그아웃하면 멈추고 마지막 id도 지운다. 다른 사람이 로그인하면 처음부터 */
    fun stop() {
        job?.cancel()
        job = null
        store.lastId = null
    }

    private suspend fun CoroutineScope.listen() {
        var failures = 0
        while (isActive) {
            try {
                repository.stream(store.lastId).collect { event ->
                    when (event) {
                        NotificationStreamEvent.Connected -> failures = 0
                        is NotificationStreamEvent.Received -> deliver(event.notification)
                    }
                }
                // 서버가 연결을 닫았다(30분마다). 바로 다시 붙는다
                delay(RECONNECT_DELAY_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(RETRY_DELAYS_MS[failures.coerceAtMost(RETRY_DELAYS_MS.lastIndex)])
                failures++
            }
        }
    }

    private suspend fun deliver(notification: AppNotification) {
        val last = store.lastId
        if (last != null && notification.id <= last) return
        _received.subscriptionCount.first { it > 0 }
        _received.emit(notification)
        store.lastId = notification.id
    }

    companion object {
        const val RECONNECT_DELAY_MS = 1_000L
        val RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L)
    }
}
