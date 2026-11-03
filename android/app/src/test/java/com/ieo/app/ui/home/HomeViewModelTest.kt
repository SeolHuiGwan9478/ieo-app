package com.ieo.app.ui.home

import androidx.lifecycle.viewModelScope
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.testing.FakeNavigator
import com.ieo.app.testing.FakeTripServer
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
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
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val vm = HomeViewModel(
            tripRepository = server,
            waitRepository = server,
            navigator = navigator,
            stateFactory = HomeStateFactory(AssignedTripMapper(clock), clock) { 1_000_000L },
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
    fun `하차지를 떠나면 운행을 마치고 내 배차 목록으로 돌아간다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()

        vm.depart()
        runCurrent()

        assertTrue(vm.state.value is HomeState.Idle)
        assertEquals(HomeViewModel.TRIP_COMPLETED_MESSAGE, vm.status.value.message)
    }

    @Test
    fun `앱을 다시 켜면 진행 중인 운행의 대기 화면을 이어서 보여 준다`() = homeTest(
        status = TripStatus.TO_PICKUP,
        prepare = { server.arrive(1, StopType.PICKUP) },
    ) { vm ->
        assertTrue(vm.state.value is HomeState.Waiting)
    }
}
