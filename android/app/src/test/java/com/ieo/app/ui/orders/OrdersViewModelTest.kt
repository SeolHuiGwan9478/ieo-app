package com.ieo.app.ui.orders

import com.ieo.app.core.network.ApiError
import com.ieo.app.domain.order.ReportType
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.testing.FakeOrderMarket
import com.ieo.app.testing.FakeSensors
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrdersViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    // 오늘(한국 11월 3일) 17:00 상차 1건, 내일 06:00 상차 1건
    private val market = FakeOrderMarket(
        mutableListOf(
            TripFixtures.order(id = 1, pickupFrom = Instant.parse("2026-11-03T08:00:00Z")),
            TripFixtures.order(id = 2),
        ),
    )
    private val sensors = FakeSensors()
    private val viewModel = OrdersViewModel(market, sensors, Clock.fixed(NOW, ZoneOffset.UTC), elapsedRealtime = { 1_000_000L })

    @Test
    fun `차량 위치를 알면 그 위치로 근처 오더를 찾는다`() = runTest(main.dispatcher) {
        val here = GeoPoint(36.9690, 126.8410)
        sensors.parkAt(here)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf<GeoPoint?>(here), market.searchedNear)
        assertTrue(viewModel.state.value.nearby)
    }

    @Test
    fun `거리와 최소 운임 칩을 고르면 그 조건으로 서버에 다시 묻는다`() = runTest(main.dispatcher) {
        sensors.parkAt(GeoPoint(36.9690, 126.8410))
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(com.ieo.app.domain.order.OrderSearch(50, null), market.lastSearch)

        viewModel.selectRadius(OrderRadius.NEAR)
        viewModel.selectMinFare(OrderMinFare.OVER_30)
        advanceUntilIdle()

        assertEquals(com.ieo.app.domain.order.OrderSearch(20, 300_000), market.lastSearch)
        assertEquals(OrderRadius.NEAR, viewModel.state.value.radius)
    }

    @Test
    fun `위치를 모르면 모집 중 오더 전체에서 찾는다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf<GeoPoint?>(null), market.searchedNear)
        assertFalse(viewModel.state.value.nearby)
    }

    @Test
    fun `날짜 칩으로 오늘·내일 오더를 거른다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.rows.size)

        viewModel.selectFilter(OrderDayFilter.TODAY)
        assertEquals(listOf(1L), viewModel.state.value.rows.map { it.id })

        viewModel.selectFilter(OrderDayFilter.TOMORROW)
        assertEquals(listOf(2L), viewModel.state.value.rows.map { it.id })
    }

    @Test
    fun `배차를 요청하면 요청 중 띠가 생기고 10분 타이머가 시작된다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.open(1)

        viewModel.request()
        advanceUntilIdle()

        assertEquals(listOf("request 1"), market.calls)
        val pending = viewModel.state.value.pending!!
        assertEquals(1_000_000L + 600_000L, pending.expiresAt)
        assertTrue(viewModel.state.value.detail!!.requested)
        assertEquals(OrdersViewModel.REQUESTED_MESSAGE, viewModel.state.value.message)
    }

    @Test
    fun `철회하면 띠가 사라진다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.open(1)
        viewModel.request()
        advanceUntilIdle()

        viewModel.withdraw()
        advanceUntilIdle()

        assertEquals(listOf("request 1", "withdraw 99"), market.calls)
        assertNull(viewModel.state.value.pending)
    }

    @Test
    fun `요청이 거절되면 서버 문장을 보여 준다`() = runTest(main.dispatcher) {
        market.requestError = ApiError.Server(409, "DISPATCH_409_LIMIT", "배차 요청은 동시에 3건까지 할 수 있습니다.")
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.open(1)

        viewModel.request()
        advanceUntilIdle()

        assertEquals("배차 요청은 동시에 3건까지 할 수 있습니다.", viewModel.state.value.message)
        assertNull(viewModel.state.value.pending)
    }

    @Test
    fun `신고하면 사유와 함께 보내고 상세를 닫는다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.open(2)
        viewModel.startReport()
        assertTrue(viewModel.state.value.reporting)

        viewModel.report(ReportType.OVERLOAD)
        advanceUntilIdle()

        assertEquals(listOf("report 2 OVERLOAD"), market.calls)
        assertNull(viewModel.state.value.detail)
    }

    @Test
    fun `상세에는 화물 정보를 이어 붙여 보여 준다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.open(2)

        val lines = viewModel.state.value.detail!!.lines.toMap()
        assertEquals("파렛트 · 4.2톤 · 지게차", lines["화물"])
        assertEquals("내일 06:00 ~ 08:00 · 경북 칠곡군", lines["상차"])
    }
}
