package com.ieo.app.domain.trip

import com.ieo.app.domain.ledger.PaymentType
import java.time.Instant

/**
 * 다른 앱에서 받은 배차를 직접 등록 (TRIP-001). 차량은 기본 차량으로 등록된다.
 * 주선사가 Ieo에 없을 수 있어 이름만 남긴다.
 */
data class NewTrip(
    val pickup: StopChoice,
    val dropoff: StopChoice,
    /** 상차 예정 시각. 모르면 null (대기비를 진입 시각부터 센다) */
    val pickupAt: Instant?,
    val fare: Long,
    val paymentType: PaymentType,
    val brokerName: String?,
)

/** 상하차지 고르기. 이미 있는 곳은 id로, 처음 가는 곳은 좌표로 보낸다 (서버가 100m 안의 같은 곳을 찾아 쓴다) */
sealed interface StopChoice {
    val name: String
    val address: String

    data class Known(val siteId: Long, override val name: String, override val address: String) : StopChoice

    data class New(
        override val name: String,
        override val address: String,
        val latitude: Double,
        val longitude: Double,
    ) : StopChoice
}
