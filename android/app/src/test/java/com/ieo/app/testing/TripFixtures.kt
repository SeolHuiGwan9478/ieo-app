package com.ieo.app.testing

import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderSite
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitStatus
import java.time.Instant

/** 테스트용 운행·대기 기록 */
object TripFixtures {

    /** 테스트의 "지금" (한국 15:00) */
    val NOW: Instant = Instant.parse("2026-11-03T06:00:00Z")

    val PICKUP = TripStop("평택 포승", "경기 평택시 포승읍", 36.9690, 126.8410, 200, NOW)
    val DROPOFF = TripStop("대구 성서", "대구 달서구", 35.8420, 128.4960, 200, null)

    val POLICY = WaitPolicy(freeMinutes = 120, unitMinutes = 30, unitAmount = 10_000, maxAmount = 50_000)

    fun trip(status: TripStatus, id: Long = 1) = Trip(
        id = id,
        status = status,
        fare = 450_000,
        paymentTypeLabel = "인수증",
        brokerName = "○○물류",
        plateNumber = "경기12가3456",
        pickup = PICKUP,
        dropoff = DROPOFF,
    )

    fun order() = Order(
        id = 11,
        brokerName = "△△운송",
        pickup = OrderSite("칠곡", "경북 칠곡군", 35.99, 128.40),
        dropoff = OrderSite("부산 강서", "부산 강서구", 35.08, 128.86),
        // 한국 내일 06:00
        pickupFrom = Instant.parse("2026-11-03T21:00:00Z"),
        tonnageLabel = "5톤",
        bodyTypeLabel = "윙바디",
        fare = 380_000,
        paymentTypeLabel = "인수증",
        distanceMeters = null,
    )

    fun wait(
        stop: StopType = StopType.PICKUP,
        arrivedAt: Instant = NOW.minusSeconds(600),
        chargeableFrom: Instant = arrivedAt,
        workStartedAt: Instant? = null,
    ) = WaitRecord(
        stopType = stop,
        status = if (workStartedAt == null) WaitStatus.WAITING else WaitStatus.WORKING,
        arrivedAt = arrivedAt,
        workStartedAt = workStartedAt,
        departedAt = null,
        chargeableFrom = chargeableFrom,
        chargeableAmount = null,
        policy = POLICY,
    )
}
