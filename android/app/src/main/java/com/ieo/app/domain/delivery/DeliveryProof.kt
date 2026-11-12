package com.ieo.app.domain.delivery

import java.time.Instant

/**
 * 종이 인수증 (04 인수증). 결제 방식이 인수증인 운행만 [required].
 * 차주가 휴대폰으로 사진을 찍어 주선사에게 보낸다. 차량 앱은 보냈는지와 사진을 보기만 한다
 */
data class DeliveryProof(
    val required: Boolean,
    val status: DeliveryProofStatus,
    val sentAt: Instant?,
    val photoIds: List<Long>,
) {
    val isSent: Boolean get() = status == DeliveryProofStatus.SENT
}

enum class DeliveryProofStatus {
    /** 사진이 없다 */
    NONE,

    /** 사진을 올렸고 아직 안 보냈다 */
    PENDING,

    /** 주선사에게 보냈다 */
    SENT,
}
