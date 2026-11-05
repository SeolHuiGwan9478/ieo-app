package com.ieo.app.ui.home

import androidx.lifecycle.viewModelScope
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.recommendation.NextOrders
import com.ieo.app.domain.recommendation.RecommendationAnswer
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.voice.AnnouncementPriority
import com.ieo.app.testing.FakeAnnouncer
import com.ieo.app.testing.FakeLocationTracker
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
    private val announcer = FakeAnnouncer()
    private val tracker = FakeLocationTracker()

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
            announcer = announcer,
            locationTracker = tracker,
            recommendationRepository = server,
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
    fun `출발하면 그 운행의 위치를 보내기 시작하고 하차지를 떠나 끝나면 멈춘다`() = homeTest { vm ->
        assertEquals(listOf<Long?>(null), tracker.tracked)

        vm.startTrip("1")
        runCurrent()
        assertEquals(listOf<Long?>(null, 1), tracker.tracked)

        server.trip = server.trip.copy(status = TripStatus.COMPLETED)
        vm.refresh()
        runCurrent()
        assertEquals(listOf<Long?>(null, 1, null), tracker.tracked)
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
    fun `운송 중 67%를 지나면 다음 배차 추천을 말하고 주행 화면에 보여 준다`() = homeTest(TripStatus.IN_TRANSIT, prepare = {
        server.nextOrders = ApiResult.Success(
            NextOrders(
                freeAt = NOW,
                items = listOf(NextOrder(TripFixtures.order(), emptyMeters = 17_000, fuelCost = 7_771, netProfit = 372_229, longWait = true)),
            ),
        )
    }) { vm ->
        navigator.progress.value = NaviProgress("대구 성서", totalMeters = 300_000, remainMeters = 90_000, remainSeconds = 3_600)
        runCurrent()

        val driving = vm.state.value as HomeState.Driving
        assertEquals(Recommendation("칠곡 → 부산 강서", "06:00", 372_229), driving.recommendation)
        assertEquals(
            "다음 배차 추천이에요. 대구 성서 하차 후 17분 거리, 칠곡 06:00 상차, 부산 강서행 5톤, 실수익 372,229원 건이 있어요. 대기가 길었던 곳이에요.",
            announcer.texts.last(),
        )
        assertEquals(listOf(Triple(1L, 11L, null as RecommendationAnswer?)), server.answers)
    }

    @Test
    fun `하차지에 도착하면 추천 5건을 카드로 보여 주고 수락하면 배차 요청을 보낸다`() = homeTest(TripStatus.IN_TRANSIT, prepare = {
        server.nextOrders = ApiResult.Success(
            NextOrders(
                freeAt = NOW,
                items = listOf(NextOrder(TripFixtures.order(), emptyMeters = 17_000, fuelCost = 7_771, netProfit = 372_229, longWait = true)),
            ),
        )
    }) { vm ->
        assertTrue(server.recommendRequests.isEmpty())
        vm.arrive()
        runCurrent()

        val card = (vm.state.value as HomeState.Waiting).nextOrders.single()
        assertEquals("칠곡 → 부산 강서", card.route)
        assertEquals("06:00", card.pickupTime)
        assertEquals(372_229L, card.netProfit)
        assertEquals(listOf(1L), server.recommendRequests)

        vm.acceptNextOrder(card.orderId)
        runCurrent()

        assertEquals(listOf(Triple(1L, 11L, RecommendationAnswer.ACCEPTED as RecommendationAnswer?)), server.answers)
        assertTrue((vm.state.value as HomeState.Waiting).nextOrders.isEmpty())
        assertEquals(HomeViewModel.NEXT_ORDER_REQUESTED_MESSAGE, vm.status.value.message)
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
    fun `도착하면 대기 시작과 무료 대기 시간을 말한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()

        assertEquals(listOf("평택 포승에 도착했어요. 대기를 시작해요. 무료 대기는 2시간이에요."), announcer.texts)
        assertEquals(AnnouncementPriority.TRIP, announcer.said.single().priority)
    }

    @Test
    fun `상차지 3km 안에 들어오면 정보 카드를 한 번만 읽어 준다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        // 약 5.5km 밖에서는 읽지 않는다
        sensors.drive(GeoPoint(TripFixtures.PICKUP.latitude + 0.05, TripFixtures.PICKUP.longitude))
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(announcer.said.isEmpty())

        // 약 2.2km
        sensors.drive(GeoPoint(TripFixtures.PICKUP.latitude + 0.02, TripFixtures.PICKUP.longitude))
        advanceTimeBy(5_000)
        runCurrent()

        val said = announcer.said.single()
        // 픽스처 정보 카드는 높이 여유가 아슬아슬(CAUTION)해서 경고로 말한다
        assertEquals(AnnouncementPriority.WARNING, said.priority)
        assertTrue(said.text.endsWith("평택 포승은 높이 제한 3.5미터, 지게차 있음, 평균 대기 1시간 20분."))
    }

    @Test
    fun `무료 대기가 끝나는 순간 대기비가 붙는다고 말한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()
        // 픽스처 대기: 10분 전 도착, 무료 2시간 → 1시간 50분 뒤
        clock.instant = NOW.plusSeconds(6_600)
        advanceTimeBy(6_600_000)
        runCurrent()

        assertEquals("지금부터 대기비가 붙어요. 30분마다 1만 원이에요.", announcer.texts.last())
    }

    @Test
    fun `작업을 시작했으면 대기비 시작을 말하지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()
        vm.startWork()
        runCurrent()

        advanceTimeBy(7_200_000)
        runCurrent()

        assertTrue(announcer.texts.none { it.startsWith("지금부터 대기비") })
    }

    @Test
    fun `상차지를 떠나면 하차지 안내를, 하차지를 떠나면 운행 완료를 말한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()
        vm.depart()
        runCurrent()
        assertEquals("평택 포승을 떠났어요. 대구 성서로 안내할게요.", announcer.texts.last())

        vm.arrive()
        runCurrent()
        vm.depart()
        runCurrent()
        assertEquals("운행을 마쳤어요. 장부에 45만 원을 기록했어요.", announcer.texts.last())
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
