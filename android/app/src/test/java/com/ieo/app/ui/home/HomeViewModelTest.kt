package com.ieo.app.ui.home

import androidx.lifecycle.viewModelScope
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.testing.FakeNavigator
import com.ieo.app.testing.FakeSensors
import com.ieo.app.testing.FakeTripServer
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.MutableClock
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeTripServer(TripFixtures.trip(TripStatus.SCHEDULED))
    private val navigator = FakeNavigator()
    private val sensors = FakeSensors()
    private val clock = MutableClock(NOW)

    /**
     * [status] 상태의 운행으로 ViewModel을 만들어 [body]를 돌린다.
     * ViewModel은 내비 진행 상황을 계속 지켜보므로, 끝나면 멈춰야 테스트가 끝난다.
     */
    private fun homeTest(
        status: TripStatus = TripStatus.SCHEDULED,
        prepare: suspend () -> Unit = {},
        body: suspend TestScope.(HomeViewModel) -> Unit,
    ) = runTest(main.dispatcher) {
        server.trip = TripFixtures.trip(status)
        prepare()
        val vm = HomeViewModel(
            tripRepository = server,
            waitRepository = server,
            ledgerRepository = server,
            orderRepository = server,
            siteRepository = server,
            deliveryProofRepository = server,
            navigator = navigator,
            sensors = sensors,
            clock = clock,
            stateFactory = HomeStateFactory(clock) { 1_000_000L },
        )
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `안내 시작하면 출발 처리하고 상차지로 길 안내한다`() = homeTest { vm ->

        vm.startTrip("1")
        runCurrent()

        assertEquals(listOf("start"), server.calls)
        assertEquals(listOf("평택 포승"), navigator.guidedTo)
        assertTrue(vm.state.value is HomeState.Driving)
    }

    @Test
    fun `내비가 도착을 알리면 도착 처리하고 대기 화면으로 바뀐다`() = homeTest { vm ->
        vm.startTrip("1")
        runCurrent()

        navigator.arrive()
        runCurrent()

        assertEquals(listOf("start", "arrive PICKUP"), server.calls)
        assertNull(navigator.progress.value)
        val state = vm.state.value as HomeState.Waiting
        assertTrue(state.isPickup)
    }

    @Test
    fun `상차지 반경 안에서 3분 서 있으면 멈추기 시작한 시각으로 도착한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        // 상차지(평택 포승)에서 약 110m
        sensors.parkAt(GeoPoint(TripFixtures.PICKUP.latitude + 0.001, TripFixtures.PICKUP.longitude))
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(180L, (vm.state.value as HomeState.Driving).arrivingInSeconds)

        clock.instant = NOW.plusSeconds(180)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("arrive PICKUP"), server.calls)
        assertEquals(listOf<Instant?>(NOW), server.arrivedAt)
        assertTrue(vm.state.value is HomeState.Waiting)
    }

    @Test
    fun `상차지에서 멀리 서 있으면 도착하지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        sensors.parkAt(GeoPoint(TripFixtures.PICKUP.latitude + 0.05, TripFixtures.PICKUP.longitude))
        clock.instant = NOW.plusSeconds(600)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(server.calls.isEmpty())
        assertNull((vm.state.value as HomeState.Driving).arrivingInSeconds)
    }

    @Test
    fun `상차 끝나고 출발하면 하차지로 길 안내한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()
        vm.startWork()
        runCurrent()
        assertTrue((vm.state.value as HomeState.Waiting).isWorking)

        vm.depart()
        runCurrent()

        assertEquals(listOf("arrive PICKUP", "work PICKUP", "depart PICKUP"), server.calls)
        assertEquals(listOf("대구 성서"), navigator.guidedTo)
        assertTrue((vm.state.value as HomeState.Driving).isPickup.not())
    }

    @Test
    fun `상차지에 도착하면 상차지 정보 카드를 받아 대기 화면에 보여 준다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()

        assertEquals(listOf(1L), server.siteRequests)
        val site = (vm.state.value as HomeState.Waiting).site
        assertEquals(3.5, site?.heightLimitMeters)
        assertEquals(2, site?.notes?.size)
    }

    @Test
    fun `하차지에서 인수증 QR을 띄우고 수하인이 서명하면 운행을 마친다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()

        vm.requestDeliveryProof()
        runCurrent()
        assertEquals(DeliveryProofStatus.PENDING, vm.status.value.deliveryProof?.status)

        server.signDelivery()
        advanceTimeBy(3_001)
        runCurrent()

        assertEquals("김수하", vm.status.value.deliveryProof?.signerName)
        assertEquals(HomeViewModel.DELIVERY_SIGNED_MESSAGE, vm.status.value.message)
        assertTrue(vm.state.value is HomeState.Idle)
    }

    @Test
    fun `상차지에서는 인수증을 만들지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()

        vm.requestDeliveryProof()
        runCurrent()

        assertTrue("delivery proof" !in server.calls)
        assertNull(vm.status.value.deliveryProof)
    }

    @Test
    fun `대기 중에 증빙을 보내면 그 상하차지의 링크 창을 띄운다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()

        vm.sendWaitProof()
        runCurrent()

        assertEquals("http://localhost/api/public/wait-proofs/token-PICKUP", vm.status.value.proof?.url)
        vm.proofShown()
        assertNull(vm.status.value.proof)
    }

    @Test
    fun `상하차지에 있지 않으면 증빙을 만들지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.sendWaitProof()
        runCurrent()

        assertTrue(server.calls.none { it.startsWith("proof") })
        assertNull(vm.status.value.proof)
    }

    @Test
    fun `하차지를 떠나면 운행을 마치고 내 배차 목록으로 돌아간다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()

        vm.depart()
        runCurrent()

        assertTrue(vm.state.value is HomeState.Idle)
        assertEquals(HomeViewModel.TRIP_COMPLETED_MESSAGE, vm.status.value.message)
    }

    @Test
    fun `상차지 반경을 벗어나면 벗어난 시각으로 출발하고 하차지로 길 안내한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        sensors.parkAt(GeoPoint(TripFixtures.PICKUP.latitude + 0.001, TripFixtures.PICKUP.longitude))
        advanceTimeBy(1_001)
        runCurrent()

        clock.instant = NOW.plusSeconds(10)
        sensors.drive(GeoPoint(TripFixtures.PICKUP.latitude + 0.01, TripFixtures.PICKUP.longitude), speedKph = 15.0)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(30L, (vm.state.value as HomeState.Waiting).departingInSeconds)

        clock.instant = NOW.plusSeconds(40)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("arrive PICKUP", "depart PICKUP"), server.calls)
        assertEquals(listOf<Instant?>(NOW.plusSeconds(10)), server.departedAt)
        assertEquals(listOf("대구 성서"), navigator.guidedTo)
        assertTrue(vm.state.value is HomeState.Driving)
    }

    @Test
    fun `하차지에서 시속 20km 이상으로 1분 달리면 운행을 마친다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()
        sensors.drive(GeoPoint(TripFixtures.DROPOFF.latitude, TripFixtures.DROPOFF.longitude), speedKph = 30.0)
        advanceTimeBy(1_000)
        runCurrent()

        clock.instant = NOW.plusSeconds(60)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("arrive DROPOFF", "depart DROPOFF"), server.calls)
        assertEquals(listOf<Instant?>(NOW), server.departedAt)
        assertTrue(vm.state.value is HomeState.Idle)
    }

    @Test
    fun `반경 밖에서 도착을 눌렀으면 서 있는 동안 떠남으로 보지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        sensors.parkAt(GeoPoint(TripFixtures.PICKUP.latitude + 0.05, TripFixtures.PICKUP.longitude))
        vm.arrive()
        runCurrent()

        clock.instant = NOW.plusSeconds(600)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("arrive PICKUP"), server.calls)
        assertNull((vm.state.value as HomeState.Waiting).departingInSeconds)
    }

    @Test
    fun `위치를 받기 전에 홈을 열었으면 위치가 들어올 때 근처 오더로 다시 찾는다`() = homeTest { vm ->
        assertEquals(listOf<GeoPoint?>(null), server.ordersNear)
        assertTrue(!(vm.state.value as HomeState.Idle).nearby)

        val here = GeoPoint(TripFixtures.PICKUP.latitude, TripFixtures.PICKUP.longitude)
        sensors.parkAt(here)
        runCurrent()

        assertEquals(listOf(null, here), server.ordersNear)
        val state = vm.state.value as HomeState.Idle
        assertTrue(state.nearby)
        assertEquals(12_300L, state.orders.single().distanceMeters)
    }

    @Test
    fun `5km 넘게 움직이면 근처 오더를 다시 찾고 가까이 움직이면 그대로 둔다`() = homeTest { vm ->
        val here = GeoPoint(TripFixtures.PICKUP.latitude, TripFixtures.PICKUP.longitude)
        sensors.parkAt(here)
        runCurrent()

        // 약 1km 북쪽
        val near = GeoPoint(here.latitude + 0.01, here.longitude)
        sensors.drive(near)
        runCurrent()
        assertEquals(listOf(null, here), server.ordersNear)

        // 약 11km 북쪽
        val far = GeoPoint(here.latitude + 0.1, here.longitude)
        sensors.drive(far)
        runCurrent()
        assertEquals(listOf(null, here, far), server.ordersNear)
        assertTrue((vm.state.value as HomeState.Idle).nearby)
    }

    @Test
    fun `운행 중에는 움직여도 근처 오더를 찾지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        sensors.drive(GeoPoint(TripFixtures.PICKUP.latitude + 0.1, TripFixtures.PICKUP.longitude))
        runCurrent()

        assertEquals(listOf<GeoPoint?>(null), server.ordersNear)
        assertTrue(vm.state.value is HomeState.Driving)
    }

    @Test
    fun `운행 전 화면에 오늘 요약과 모집 중 오더를 서버에서 받아 보여 준다`() = homeTest { vm ->
        val state = vm.state.value as HomeState.Idle

        assertEquals(1, state.today.tripCount)
        assertEquals(1, state.orders.size)
        assertEquals(1, state.myTrips.size)
    }

    @Test
    fun `앱을 다시 켜면 진행 중인 운행의 대기 화면을 이어서 보여 준다`() = homeTest(
        status = TripStatus.TO_PICKUP,
        prepare = { server.arrive(1, StopType.PICKUP) },
    ) { vm ->
        assertTrue(vm.state.value is HomeState.Waiting)
    }
}
