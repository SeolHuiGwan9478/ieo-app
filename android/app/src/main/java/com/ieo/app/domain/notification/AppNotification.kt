package com.ieo.app.domain.notification

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 서버가 차주에게 보낸 알림 한 건 (09 알림). [message]는 화면과 음성에 그대로 쓴다 */
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val message: String,
    val dispatchRequestId: Long,
    val orderId: Long,
    val createdAt: Instant,
)

enum class NotificationType {
    /** 주선사가 확정했다. 내 배차에 운행이 생겼다 */
    DISPATCH_CONFIRMED,

    /** 다른 차주로 마감됐거나 주선사가 오더를 내렸다 */
    DISPATCH_DECLINED,

    /** 10분 안에 확정되지 않았다 */
    DISPATCH_EXPIRED,
}

/** 알림 연결에서 오는 것 */
sealed interface NotificationStreamEvent {
    /** 서버에 붙었다 */
    data object Connected : NotificationStreamEvent

    data class Received(val notification: AppNotification) : NotificationStreamEvent
}

/** 서버 알림 연결 (SSE). 서버가 닫으면 흐름이 끝나고, 연결이 실패하면 예외로 끝난다 */
interface NotificationRepository {
    /** [after] 다음 알림부터. 놓친 것을 먼저 받고 이어서 새 알림을 받는다 */
    fun stream(after: Long?): Flow<NotificationStreamEvent>
}

/** 마지막으로 받은 알림 id. 앱을 껐다 켜도 이어 받으려고 기기에 남긴다 */
interface LastNotificationStore {
    var lastId: Long?
}
