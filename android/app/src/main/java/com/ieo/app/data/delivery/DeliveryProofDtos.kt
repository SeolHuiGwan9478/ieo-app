package com.ieo.app.data.delivery

import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofStatus
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/delivery/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class ProofPhotoResponse(val id: Long)

@Serializable
data class DeliveryProofResponse(
    val required: Boolean,
    val status: String,
    val sentAt: String? = null,
    val photos: List<ProofPhotoResponse> = emptyList(),
) {
    fun toDomain() = DeliveryProof(
        required = required,
        status = DeliveryProofStatus.valueOf(status),
        sentAt = sentAt?.let(Instant::parse),
        photoIds = photos.map { it.id },
    )
}
