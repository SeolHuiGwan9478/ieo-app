package com.ieo.app.ui.home

import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateFactoryTest {

    // 기기 시계(elapsedRealtime)는 1,000,000ms
    private val factory = HomeStateFactory(clock = Clock.fixed(NOW, ZoneOffset.UTC), elapsedRealtime = { 1_000_000L })

    @Test
    fun `진행 중인 운행이 없으면 내 배차 목록`() {
        val state = factory.create(HomeSnapshot(upcoming = listOf(TripFixtures.trip(TripStatus.SCHEDULED))), navi = null)

        state as HomeState.Idle
        assertEquals(1, state.myTrips.size)
        assertEquals("15:00", state.myTrips[0].pickupTime)
    }

    @Test
    fun `운행 전 화면에 오늘 요약, 가장 밀린 미수금, 모집 중 오더를 보여 준다`() {
        val snapshot = HomeSnapshot(
            today = DailySummary(LocalDate.of(2026, 11, 3), tripCount = 2, netProfit = 610_000),
            receivables = Receivables(450_000, 450_000, 0, listOf(TripFixtures.entry(expectedPaymentDate = LocalDate.of(2026, 10, 31), overdue = true))),
            orders = listOf(TripFixtures.order()),
        )

        val state = factory.create(snapshot, navi = null) as HomeState.Idle

        assertEquals(TodaySummary(tripCount = 2, netProfit = 610_000), state.today)
        assertEquals(UnpaidAlert("○○물류", 450_000, overdueDays = 3), state.unpaid)
        assertFalse(state.nearby)
        assertEquals("칠곡 → 부산 강서", state.orders.single().let { "${it.pickup} → ${it.dropoff}" })
    }

    @Test
    fun `장부를 못 받았으면 0건, 미수금 경고 없음`() {
        val state = factory.create(HomeSnapshot(), navi = null) as HomeState.Idle

        assertEquals(TodaySummary(tripCount = 0, netProfit = 0), state.today)
        assertNull(state.unpaid)
    }

    @Test
    fun `상차지로 가는 중이면 내비의 남은 시간으로 도착 시각을 보여 준다`() {
        val navi = NaviProgress("평택 포승", totalMeters = 10_000, remainMeters = 2_500, remainSeconds = 600)

        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.TO_PICKUP)), navi)

        state as HomeState.Driving
        assertTrue(state.isPickup)
        assertEquals("평택 포승", state.destinationName)
        // 한국 15:00 + 10분
        assertEquals("15:10", state.etaText)
        assertEquals(0.75f, state.progress!!, 0.001f)
        assertTrue(state.naviActive)
    }

    @Test
    fun `운송 중이면 하차지로 가는 중, 내비가 꺼져 있으면 버튼을 보여 준다`() {
        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.IN_TRANSIT)), navi = null)

        state as HomeState.Driving
        assertFalse(state.isPickup)
        assertEquals("대구 성서", state.destinationName)
        assertNull(state.etaText)
        assertFalse(state.naviActive)
    }

    @Test
    fun `다른 상하차지의 정보 카드는 대기 화면에 붙이지 않는다`() {
        val snapshot = HomeSnapshot(
            current = TripFixtures.trip(TripStatus.AT_PICKUP),
            wait = TripFixtures.wait(StopType.PICKUP),
            site = TripFixtures.site(TripFixtures.DROPOFF.siteId),
        )

        val state = factory.create(snapshot, navi = null) as HomeState.Waiting

        assertNull(state.site)
    }

    @Test
    fun `내비가 길 안내 요청을 받지 못했으면 버튼을 보여 준다`() {
        val navi = NaviProgress("평택 포승", failed = true)

        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.TO_PICKUP)), navi)

        state as HomeState.Driving
        assertFalse(state.naviActive)
    }

    @Test
    fun `상차지에 있으면 서버 시각을 기기 시계로 바꿔 대기 타이머를 만든다`() {
        // 10분 전 도착, 대기비는 5분 전부터
        val wait = TripFixtures.wait(arrivedAt = NOW.minusSeconds(600), chargeableFrom = NOW.minusSeconds(300))

        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.AT_PICKUP), wait = wait), navi = null)

        state as HomeState.Waiting
        assertEquals(1_000_000L - 600_000L, state.waitStartedAt)
        assertEquals(1_000_000L - 300_000L, state.chargeableFrom)
        assertFalse(state.isWorking)
        assertEquals(TripFixtures.POLICY, state.policy)
    }

    @Test
    fun `작업을 시작했으면 작업 중`() {
        val wait = TripFixtures.wait(workStartedAt = NOW.minusSeconds(60))

        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.AT_PICKUP), wait = wait), navi = null)

        state as HomeState.Waiting
        assertEquals(1_000_000L - 60_000L, state.workStartedAt)
    }

    @Test
    fun `상차지에 있는데 대기 기록이 없으면 도착 버튼으로 다시 받게 한다`() {
        val other = TripFixtures.wait(stop = StopType.DROPOFF)

        val state = factory.create(HomeSnapshot(current = TripFixtures.trip(TripStatus.AT_PICKUP), wait = other), navi = null)

        state as HomeState.Driving
        assertFalse(state.naviActive)
    }
}
