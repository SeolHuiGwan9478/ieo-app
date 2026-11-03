package com.ieo.app.domain.order

import java.time.Instant

/** 오더 마켓에 올라온 오더 (서버 OrderResponse) */
data class Order(
    val id: Long,
    val brokerName: String,
    val pickup: OrderSite,
    val dropoff: OrderSite,
    /** 상차 가능 시작 시각 */
    val pickupFrom: Instant,
    /** "5톤", "윙바디" 같은 서버 표기 */
    val tonnageLabel: String,
    val bodyTypeLabel: String,
    val fare: Long,
    val paymentTypeLabel: String,
    /** 위치를 보내고 찾았을 때만: 지금 위치에서 상차지까지 거리 */
    val distanceMeters: Long?,
) {
    val vehicleLabel: String get() = "$tonnageLabel $bodyTypeLabel"
}

data class OrderSite(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
