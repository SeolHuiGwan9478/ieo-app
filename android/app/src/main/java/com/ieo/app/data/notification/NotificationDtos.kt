package com.ieo.app.data.notification

import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import java.time.Instant
import kotlinx.serialization.Serializable

/** 서버 domain/notification/dto/NotificationResponse와 같은 모양 */
@Serializable
data class NotificationResponse(
    val id: Long,
    val type: String,
    val message: String,
    val dispatchRequestId: Long? = null,
    val orderId: Long? = null,
    val createdAt: String,
) {
    /** 모르는 종류(서버가 새로 늘린 것)면 null: 앱이 이해하지 못하는 알림은 건너뛴다 */
    fun toDomain(): AppNotification? {
        val known = NotificationType.entries.firstOrNull { it.name == type } ?: return null
        return AppNotification(id, known, message, dispatchRequestId, orderId, Instant.parse(createdAt))
    }
}
