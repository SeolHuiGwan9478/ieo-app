package com.ieo.app.data.notification

import com.ieo.app.domain.notification.NotificationRepository
import com.ieo.app.domain.notification.NotificationStreamEvent
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * `GET /api/notifications/stream`을 SSE로 붙잡는다. 토큰·갱신·로그아웃은 로그인 후 클라이언트가 그대로 한다.
 * 서버가 20초마다 빈 줄을 보내므로, 1분 동안 아무것도 오지 않으면 끊긴 것으로 본다
 */
class SseNotificationRepository(
    client: OkHttpClient,
    private val baseUrl: String,
    private val json: Json,
) : NotificationRepository {

    private val factory = EventSources.createFactory(
        client.newBuilder().readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
    )

    override fun stream(after: Long?): Flow<NotificationStreamEvent> = callbackFlow {
        val url = baseUrl.trimEnd('/') + "/api/notifications/stream" + (after?.let { "?after=$it" } ?: "")
        val request = Request.Builder()
            .url(url)
            // 오류 응답(JSON)도 받을 수 있게
            .header("Accept", "text/event-stream, application/json")
            .build()
        val source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(NotificationStreamEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type != EVENT_NAME) return
                val notification = runCatching { json.decodeFromString<NotificationResponse>(data).toDomain() }.getOrNull()
                    ?: return
                trySend(NotificationStreamEvent.Received(notification))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("알림 연결 실패: HTTP ${response?.code}"))
            }
        })
        awaitClose { source.cancel() }
    }

    companion object {
        const val EVENT_NAME = "notification"
        private const val READ_TIMEOUT_SECONDS = 60L
    }
}
