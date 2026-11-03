package com.ieo.app.ui.home

import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AssignedTripMapperTest {

    // 한국 시각 2026-11-03 10:00
    private val mapper = AssignedTripMapper(Clock.fixed(Instant.parse("2026-11-03T01:00:00Z"), ZoneId.of("Asia/Seoul")))

    @Test
    fun `오늘 상차는 오늘과 한국 시각으로 보여 준다`() {
        val card = mapper.map(trip(pickupAt = Instant.parse("2026-11-03T06:00:00Z")))

        assertEquals("오늘", card.pickupDay)
        assertEquals("15:00", card.pickupTime)
    }

    @Test
    fun `한국 날짜로 다음 날이면 내일이다`() {
        // UTC로는 11월 3일이지만 한국은 11월 4일 오전 8시
        val card = mapper.map(trip(pickupAt = Instant.parse("2026-11-03T23:00:00Z")))

        assertEquals("내일", card.pickupDay)
        assertEquals("08:00", card.pickupTime)
    }

    @Test
    fun `그보다 먼 날은 날짜로 보여 준다`() {
        val card = mapper.map(trip(pickupAt = Instant.parse("2026-11-10T00:00:00Z")))

        assertEquals("11월 10일", card.pickupDay)
    }

    @Test
    fun `시각이 없으면 미정, 주선사가 없으면 직접 등록`() {
        val card = mapper.map(trip(pickupAt = null, brokerName = null))

        assertEquals("미정", card.pickupTime)
        assertEquals("직접 등록", card.brokerName)
    }

    @Test
    fun `운임과 결제 방식과 장소를 그대로 옮긴다`() {
        val card = mapper.map(trip(pickupAt = null))

        assertEquals("7", card.id)
        assertEquals(450_000L, card.fare)
        assertEquals("인수증", card.paymentType)
        assertEquals("평택 포승", card.pickup.name)
        assertEquals(128.4960, card.dropoff.longitude, 0.0)
    }

    private fun trip(pickupAt: Instant?, brokerName: String? = "○○물류") = Trip(
        id = 7,
        status = TripStatus.SCHEDULED,
        fare = 450_000,
        paymentTypeLabel = "인수증",
        brokerName = brokerName,
        plateNumber = "경기12가3456",
        pickup = TripStop("평택 포승", "경기 평택시 포승읍", 36.9690, 126.8410, 200, pickupAt),
        dropoff = TripStop("대구 성서", "대구 달서구", 35.8420, 128.4960, 200, null),
    )
}
