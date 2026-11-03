package com.ieo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 화면 위에 잠깐 띄우는 것들 */
data class HomeStatus(
    /** 서버 요청 중. 버튼을 두 번 누르지 않게 한다 */
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 ([HomeViewModel.messageShown]) */
    val message: String? = null,
)

/** 홈 화면이 ViewModel에 보내는 동작 */
interface HomeActions {
    /** 내 배차에서 "안내 시작": 출발 처리 후 상차지로 길 안내 */
    fun startTrip(tripId: String)

    /** 상하차지 도착 */
    fun arrive()

    /** "상차 시작" · "하차 시작" */
    fun startWork()

    /** 떠남. 상차지면 하차지로 길 안내, 하차지면 운행 완료 */
    fun depart()

    /** 내비 안내가 꺼졌을 때 다시 켜기 */
    fun resumeGuidance()

    fun refresh()
}

/**
 * 홈 화면: 운행 진행(출발 → 상차지 도착 → 상차 → 출발 → 하차지 도착 → 하차 → 완료)을 서버에 알리고,
 * 그 결과로 화면 상태를 바꾼다. 화면을 고르는 규칙은 [HomeStateFactory]가 갖고 있다.
 *
 * 상하차지 도착은 차량 내비가 "목적지 도착"을 알리면 자동으로 처리한다.
 * TODO: 위치 반경(지오펜스) + 3분 정차(기어·속도)로 판단하기 (WAIT-001, 에뮬레이터 확인 뒤)
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    private val waitRepository: WaitRepository,
    private val ledgerRepository: LedgerRepository,
    private val orderRepository: OrderRepository,
    private val navigator: Navigator,
    private val stateFactory: HomeStateFactory = HomeStateFactory(),
) : ViewModel(), HomeActions {

    private val snapshot = MutableStateFlow(HomeSnapshot())

    val state: StateFlow<HomeState> = combine(snapshot, navigator.progress, stateFactory::create)
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateFactory.create(HomeSnapshot(), null))

    private val _status = MutableStateFlow(HomeStatus())
    val status: StateFlow<HomeStatus> = _status.asStateFlow()

    init {
        navigator.connect()
        refresh()
        arriveWhenNaviArrives()
        keepNaviProgressFresh()
    }

    override fun onCleared() {
        navigator.release()
    }

    override fun refresh() {
        viewModelScope.launch { load() }
    }

    override fun startTrip(tripId: String) = runAction {
        val trip = snapshot.value.upcoming.firstOrNull { it.id.toString() == tripId } ?: return@runAction
        val started = if (trip.status == TripStatus.SCHEDULED) tripRepository.start(trip.id).orShow() ?: return@runAction else trip
        snapshot.update { it.copy(current = started, wait = null) }
        started.currentStop?.let { guideTo(started.stop(it)) }
    }

    override fun arrive() = runAction {
        val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return@runAction
        val stop = trip.currentStop ?: return@runAction
        val record = waitRepository.arrive(trip.id, stop).orShow() ?: return@runAction
        navigator.stopGuidance()
        reloadCurrent(record)
    }

    override fun startWork() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        val record = waitRepository.startWork(trip.id, stop).orShow() ?: return@runAction
        snapshot.update { it.copy(wait = record) }
    }

    override fun depart() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        waitRepository.depart(trip.id, stop).orShow() ?: return@runAction
        when (stop) {
            StopType.PICKUP -> {
                reloadCurrent(wait = null)
                guideTo(trip.dropoff)
            }
            StopType.DROPOFF -> {
                snapshot.update { it.copy(current = null, wait = null) }
                show(TRIP_COMPLETED_MESSAGE)
                load()
            }
        }
    }

    override fun resumeGuidance() {
        val trip = snapshot.value.current ?: return
        trip.currentStop?.let { guideTo(trip.stop(it)) }
    }

    fun messageShown() = _status.update { it.copy(message = null) }

    /**
     * 홈에 필요한 것을 한꺼번에 받는다: 내 배차, 진행 중인 운행, 오늘 요약, 미수금, 모집 중 오더.
     * 하나가 실패해도 나머지는 보여 준다.
     */
    private suspend fun load() {
        val upcoming = viewModelScope.async { tripRepository.myTrips() }
        val current = viewModelScope.async { tripRepository.currentTrip() }
        val today = viewModelScope.async { ledgerRepository.today() }
        val receivables = viewModelScope.async { ledgerRepository.receivables() }
        val orders = viewModelScope.async { orderRepository.openOrders(limit = HOME_ORDER_COUNT) }
        upcoming.await().orShow()?.let { trips -> snapshot.update { it.copy(upcoming = upcomingOf(trips)) } }
        today.await().orShow()?.let { summary -> snapshot.update { it.copy(today = summary) } }
        receivables.await().orShow()?.let { owed -> snapshot.update { it.copy(receivables = owed) } }
        orders.await().orShow()?.let { list -> snapshot.update { it.copy(orders = list) } }
        val trip = current.await().orShow() ?: run {
            snapshot.update { it.copy(current = null, wait = null) }
            return
        }
        snapshot.update { it.copy(current = trip, wait = if (trip.status.isAtStop) waitAt(trip) else null) }
    }

    /** 서버가 바꾼 운행 상태를 다시 받는다 (도착 → 대기 중, 출발 → 운송 중) */
    private suspend fun reloadCurrent(wait: WaitRecord?) {
        val trip = tripRepository.currentTrip().orShow()
        snapshot.update { it.copy(current = trip, wait = wait) }
    }

    private suspend fun waitAt(trip: Trip): WaitRecord? {
        val stop = trip.currentStop ?: return null
        return waitRepository.waits(trip.id).orShow()?.firstOrNull { it.stopType == stop }
    }

    private fun currentAtStop(): Pair<Trip, StopType>? {
        val trip = snapshot.value.current?.takeIf { it.status.isAtStop } ?: return null
        return trip.currentStop?.let { trip to it }
    }

    private fun guideTo(stop: TripStop) {
        val started = navigator.startGuidance(Destination(stop.name, stop.address, stop.latitude, stop.longitude))
        if (!started) show(NAVI_FAILED_MESSAGE)
    }

    private fun arriveWhenNaviArrives() {
        viewModelScope.launch {
            navigator.progress.filter { it?.arrived == true }.collect {
                if (snapshot.value.current?.status?.isDriving == true) arrive()
            }
        }
    }

    /** 내비가 주행 정보를 띄엄띄엄 보내서, 안내 중에만 10초마다 남은 거리·시간을 다시 묻는다 */
    private fun keepNaviProgressFresh() {
        viewModelScope.launch {
            navigator.progress.map { it != null }.distinctUntilChanged().collectLatest { guiding ->
                while (guiding) {
                    delay(NAVI_REFRESH_MILLIS)
                    navigator.refresh()
                }
            }
        }
    }

    /** 서버 요청 하나를 버튼 동작으로 실행한다. 이미 처리 중이면 무시한다 */
    private fun runAction(action: suspend () -> Unit) {
        if (_status.value.isBusy) return
        _status.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                _status.update { it.copy(isBusy = false) }
            }
        }
    }

    /** 실패면 문장을 띄우고 null */
    private fun <T> ApiResult<T>.orShow(): T? = when (this) {
        is ApiResult.Success -> data
        is ApiResult.Failure -> {
            show(error.message)
            null
        }
    }

    private fun show(message: String) = _status.update { it.copy(message = message) }

    companion object {
        const val TRIP_COMPLETED_MESSAGE = "운행을 마쳤어요. 장부에 기록했어요"
        const val NAVI_FAILED_MESSAGE = "차량 내비에 연결하지 못했습니다"
        private const val NAVI_REFRESH_MILLIS = 10_000L

        /** 홈에는 오더를 이만큼만. 나머지는 오더 탭에서 */
        private const val HOME_ORDER_COUNT = 5

        /** 끝나지 않은 운행을 상차 시각이 이른 순으로. 시각이 없는 운행은 뒤로 */
        fun upcomingOf(trips: List<Trip>): List<Trip> = trips
            .filter { it.status.isActive }
            .sortedBy { it.pickup.plannedAt ?: Instant.MAX }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    tripRepository = container.tripRepository,
                    waitRepository = container.waitRepository,
                    ledgerRepository = container.ledgerRepository,
                    orderRepository = container.orderRepository,
                    navigator = container.navigator,
                )
            }
        }
    }
}
