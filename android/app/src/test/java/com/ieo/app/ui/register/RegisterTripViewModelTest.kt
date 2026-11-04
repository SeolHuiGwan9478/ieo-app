package com.ieo.app.ui.register

import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.navi.NaviPlace
import com.ieo.app.domain.navi.NaviPlaceSource
import com.ieo.app.domain.trip.StopChoice
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.testing.FakeTripServer
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.TripFixtures
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterTripViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    // 한국 15:00. 이 운행은 평택 포승(siteId 1) → 대구 성서(siteId 2)
    private val server = FakeTripServer(TripFixtures.trip(TripStatus.COMPLETED))
    private val navi = FakeNaviPlaces()
    private val vm = RegisterTripViewModel(server, navi, Clock.fixed(TripFixtures.NOW, ZoneOffset.UTC))

    @Test
    fun `상차지는 지금 여기를 먼저 고르고, 다녀온 곳과 내비 저장 장소를 늘어놓는다`() = runTest(main.dispatcher) {
        vm.start()
        runCurrent()

        val state = vm.state.value
        assertEquals(listOf("지금 여기", "평택 포승", "대구 성서", "이천 창고"), state.pickupOptions.map { it.title })
        assertEquals(listOf("평택 포승", "대구 성서", "이천 창고"), state.dropoffOptions.map { it.title })
        assertEquals("지금 여기", state.pickup?.title)
        assertEquals(listOf("○○물류"), state.brokerOptions)
    }

    @Test
    fun `내비에 있는 곳이 다녀온 곳과 주소가 같으면 한 번만 보여 준다`() = runTest(main.dispatcher) {
        navi.saved += NaviPlace("평택 물류", TripFixtures.PICKUP.address, 36.97, 126.84, NaviPlace.Kind.RECENT)
        vm.start()
        runCurrent()

        assertEquals(1, vm.state.value.dropoffOptions.count { it.address == TripFixtures.PICKUP.address })
    }

    @Test
    fun `하차지를 고르지 않으면 등록하지 않는다`() = runTest(main.dispatcher) {
        vm.start()
        runCurrent()

        vm.submit(fare = 300_000, brokerName = null)
        runCurrent()

        assertEquals(RegisterTripViewModel.DROPOFF_REQUIRED_MESSAGE, vm.state.value.message)
        assertTrue(server.registered.isEmpty())
    }

    @Test
    fun `운임이 없으면 등록하지 않는다`() = runTest(main.dispatcher) {
        vm.start()
        runCurrent()
        vm.selectDropoff("site-2")

        vm.submit(fare = 0, brokerName = null)
        runCurrent()

        assertEquals(RegisterTripViewModel.FARE_REQUIRED_MESSAGE, vm.state.value.message)
    }

    @Test
    fun `내일 상차로 등록하면 한국 시각으로 예정 시각을 보내고 창을 닫는다`() = runTest(main.dispatcher) {
        vm.start()
        runCurrent()
        vm.selectPickup("site-1")
        vm.selectDropoff("site-2")
        vm.selectDay(PlannedDay.TOMORROW)
        vm.shiftTime(30)
        vm.selectPayment(PaymentType.PREPAID)

        vm.submit(fare = 280_000, brokerName = "  한빛물류 ")
        runCurrent()

        val trip = server.registered.single()
        assertEquals(StopChoice.Known(1, "평택 포승", TripFixtures.PICKUP.address), trip.pickup)
        // 다음 정시 16:00 + 30분, 내일 → 2026-11-04 16:30 한국 = 07:30 UTC
        assertEquals(Instant.parse("2026-11-04T07:30:00Z"), trip.pickupAt)
        assertEquals(PaymentType.PREPAID, trip.paymentType)
        assertEquals("한빛물류", trip.brokerName)
        assertTrue(vm.state.value.done)
        assertEquals(RegisterTripViewModel.REGISTERED_MESSAGE, vm.state.value.message)
    }

    @Test
    fun `상차 예정이 미정이면 시각을 보내지 않는다`() = runTest(main.dispatcher) {
        vm.start()
        runCurrent()
        vm.selectDropoff("site-2")
        vm.selectDay(PlannedDay.UNKNOWN)

        vm.submit(fare = 280_000, brokerName = "")
        runCurrent()

        val trip = server.registered.single()
        assertTrue(trip.pickup is StopChoice.New)
        assertNull(trip.pickupAt)
        assertNull(trip.brokerName)
    }

    private class FakeNaviPlaces : NaviPlaceSource {
        val saved = mutableListOf(NaviPlace("이천 창고", "경기 이천시 마장면", 37.24, 127.36, NaviPlace.Kind.BOOKMARK))

        override suspend fun savedPlaces(): List<NaviPlace> = saved

        override suspend fun currentPlace() = NaviPlace("경기 평택시 포승읍 원정리", "경기 평택시 포승읍 원정리", 36.96, 126.83, NaviPlace.Kind.CURRENT)
    }
}
