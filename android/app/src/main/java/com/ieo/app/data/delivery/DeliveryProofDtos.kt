package com.ieo.app.data.delivery

import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofStatus
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/delivery/dto와 같은 모양. 쓰지 않는 필드는 뺐다

/** path는 서버 주소 뒤에 붙이는 수하인 링크 */
@Serializable
data class DeliveryProofResponse(
    val path: String,
    val status: String,
    val signerName: String? = null,
    val signedAt: String? = null,
) {
    fun toDomain(baseUrl: String) = DeliveryProof(
        url = baseUrl.trimEnd('/') + path,
        status = DeliveryProofStatus.valueOf(status),
        signerName = signerName,
        signedAt = signedAt?.let(Instant::parse),
    )
}
