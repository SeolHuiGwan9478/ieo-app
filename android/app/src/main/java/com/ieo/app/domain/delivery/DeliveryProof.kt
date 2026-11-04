package com.ieo.app.domain.delivery

import java.time.Instant

/**
 * 전자인수증 (04 인수증). 하차지에서 수하인이 QR로 링크를 열어 서명한다 (POD-003, 004).
 * 서명하면 서버가 운행을 끝내고 장부의 지급 예정일 계산을 시작한다 (POD-008).
 */
data class DeliveryProof(
    /** 수하인에게 보여 줄 링크. 차량 화면 QR에 넣는다 */
    val url: String,
    val status: DeliveryProofStatus,
    val signerName: String?,
    val signedAt: Instant?,
) {
    /** 서명이나 종이 인수증 사진으로 인수가 확인됐다 */
    val isConfirmed: Boolean
        get() = status != DeliveryProofStatus.PENDING
}

enum class DeliveryProofStatus {
    /** 서명 전 */
    PENDING,
    SIGNED,

    /** 서명 대신 종이 인수증 사진 (POD-009) */
    PHOTO,

    /** 주선사에게 전달됨 */
    DELIVERED,
}
