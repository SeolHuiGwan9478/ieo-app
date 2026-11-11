package com.ieo.app.testing

import com.ieo.app.domain.ledger.LedgerEntry
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderSite
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitStatus
import java.time.Instant
import java.time.LocalDate

/** 테스트용 운행·대기 기록 */
object TripFixtures {

    /** 테스트의 "지금" (한국 15:00) */
    val NOW: Instant = Instant.parse("2026-11-03T06:00:00Z")

    val PICKUP = TripStop("평택 포승", "경기 평택시 포승읍", 36.9690, 126.8410, 200, NOW, siteId = 1)
    val DROPOFF = TripStop("대구 성서", "대구 달서구", 35.8420, 128.4960, 200, null, siteId = 2)

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

    /** 높이 제한 3.5m, 지게차 있음, 메모 3개 */
    fun site(id: Long) = SiteCard(
        id = id,
        name = if (id == PICKUP.siteId) PICKUP.name else DROPOFF.name,
        heightLimitMeters = 3.5,
        heightWarning = HeightWarning.CAUTION,
        forklift = true,
        parking = null,
        averageWaitMinutes = 80,
        notes = listOf("정문 말고 후문으로", "점심시간 12~13시 상차 안 함", "경비실에 차번 말하기"),
    )

    fun order(
        id: Long = 11,
        pickupFrom: Instant = Instant.parse("2026-11-03T21:00:00Z"),
        myRequest: DispatchRequest? = null,
        distanceMeters: Long? = null,
    ) = Order(
        id = id,
        brokerName = "△△운송",
        pickup = OrderSite("칠곡", "경북 칠곡군", 35.99, 128.40),
        dropoff = OrderSite("부산 강서", "부산 강서구", 35.08, 128.86),
        // 기본값은 한국 내일 06:00
        pickupFrom = pickupFrom,
        pickupUntil = pickupFrom.plusSeconds(7200),
        dropoffBy = pickupFrom.plusSeconds(43_200),
        tonnageLabel = "5톤",
        bodyTypeLabel = "윙바디",
        cargoType = "파렛트",
        weightKg = 4200,
        loadingMethodLabel = "지게차",
        fare = 380_000,
        paymentTypeLabel = "인수증",
        expectedPaymentDays = 30,
        distanceMeters = distanceMeters,
        myRequest = myRequest,
    )

    fun entry(
        id: Long = 21,
        workDate: LocalDate = LocalDate.of(2026, 11, 3),
        fare: Long = 450_000,
        brokerName: String? = "○○물류",
        expectedPaymentDate: LocalDate? = LocalDate.of(2026, 12, 3),
        paid: Boolean = false,
        overdue: Boolean = false,
    ) = LedgerEntry(
        id = id,
        auto = true,
        workDate = workDate,
        description = "평택 포승 → 대구 성서",
        fare = fare,
        paymentType = PaymentType.RECEIPT,
        brokerName = brokerName,
        distanceKm = 312,
        waitMinutes = 130,
        expectedPaymentDate = expectedPaymentDate,
        paid = paid,
        overdue = overdue,
        extraIncomes = emptyList(),
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
