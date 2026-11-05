package com.ieo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofRepository
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.recommendation.NextOrderAdvisor
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.trip.LocationTracker
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleSensors
import com.ieo.app.domain.voice.Announcer
import com.ieo.app.domain.wait.ArrivalCheck
import com.ieo.app.domain.wait.ArrivalDetector
import com.ieo.app.domain.wait.DepartureCheck
import com.ieo.app.domain.wait.DepartureDetector
import com.ieo.app.domain.wait.GeoFence
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.filterNotNull
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
    /** 대기 증빙 링크 창. 닫으면 null ([HomeViewModel.proofShown]) */
    val proof: WaitProofLink? = null,
    /** 인수증 QR 창. 닫으면 null ([HomeViewModel.closeDeliveryProof]) */
    val deliveryProof: DeliveryProof? = null,
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

    /** 지금 상하차지의 대기 증빙 링크를 만들어 보여 준다 */
    fun sendWaitProof()

    /** 하차지에서 "인수증 받기": QR을 보여 주고 수하인 서명을 기다린다 (POD-003) */
    fun requestDeliveryProof()

    fun refresh()
}

/**
 * 홈 화면: 운행 진행(출발 → 상차지 도착 → 상차 → 출발 → 하차지 도착 → 하차 → 완료)을 서버에 알리고,
 * 그 결과로 화면 상태를 바꾼다. 화면을 고르는 규칙은 [HomeStateFactory]가 갖고 있다.
 *
 * 상하차지 도착은 셋 중 먼저 오는 것으로 처리한다: 상하차지 반경 안 3분 정차(WAIT-001),
 * 차량 내비의 "목적지 도착", "도착했어요" 버튼.
 * 떠남은 반경을 벗어나거나 시속 20km 이상으로 1분 달린 때(WAIT-005), 또는 "출발"·"운행 완료" 버튼.
 * 상하차지 3km 전, 도착, 대기비 시작, 떠남, 인수증 서명은 음성으로도 알린다 (문장은 [HomeAnnouncements]).
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    private val waitRepository: WaitRepository,
    private val ledgerRepository: LedgerRepository,
    private val orderRepository: OrderRepository,
    private val siteRepository: SiteRepository,
    private val deliveryProofRepository: DeliveryProofRepository,
    private val navigator: Navigator,
    private val sensors: VehicleSensors,
    private val announcer: Announcer,
    private val locationTracker: LocationTracker,
    recommendationRepository: RecommendationRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val stateFactory: HomeStateFactory = HomeStateFactory(),
) : ViewModel(), HomeActions {

    private val snapshot = MutableStateFlow(HomeSnapshot())

    /** 상하차지 반경 안에서 멈춘 동안 남은 시간을 화면에 보여 준다 */
    private val arrivalCheck = MutableStateFlow<ArrivalCheck>(ArrivalCheck.NotYet)
    private val arrivalDetector = ArrivalDetector()

    /** 상하차지를 떠나는 동안 남은 시간을 화면에 보여 준다 */
    private val departureCheck = MutableStateFlow<DepartureCheck>(DepartureCheck.Staying)
    private val departureDetector = DepartureDetector()

    /** 운송 중 다음 배차 추천: 50%에서 찾고 67%에서 음성으로 안내한다 (REC-001) */
    private val nextOrderAdvisor = NextOrderAdvisor(recommendationRepository) { trip, next ->
        announcer.say(HomeAnnouncements.nextOrder(trip.dropoff.name, next, stateFactory.pickupTime(next)))
    }

    val state: StateFlow<HomeState> =
        combine(snapshot, navigator.progress, arrivalCheck, departureCheck, nextOrderAdvisor.current, stateFactory::create)
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateFactory.create(HomeSnapshot(), null))

    private val _status = MutableStateFlow(HomeStatus())
    val status: StateFlow<HomeStatus> = _status.asStateFlow()

    init {
        refresh()
        arriveWhenNaviArrives()
        arriveWhenStoppedAtStop()
        departWhenLeavingStop()
        announceChargeableStart()
        keepNaviProgressFresh()
        keepNearbyOrdersFresh()
        reportLocationWhileOnTrip()
        recommendNextOrder()
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

    override fun arrive() = arriveAt(at = null)

    /** [at]: 자동 도착이면 멈추기 시작한 때. 버튼·내비 도착이면 null(서버가 받은 때) */
    private fun arriveAt(at: Instant?) = runAction {
        val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return@runAction
        val stop = trip.currentStop ?: return@runAction
        val record = waitRepository.arrive(trip.id, stop, at).orShow() ?: return@runAction
        announcer.say(HomeAnnouncements.arrived(trip.stop(stop).name, record.policy))
        navigator.stopGuidance()
        reloadCurrent(record)
    }

    override fun startWork() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        val record = waitRepository.startWork(trip.id, stop).orShow() ?: return@runAction
        snapshot.update { it.copy(wait = record) }
    }

    override fun depart() = departAt(at = null)

    /** [at]: 자동 떠남이면 반경을 벗어나거나 달리기 시작한 때. 버튼이면 null(서버가 받은 때) */
    private fun departAt(at: Instant?) = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        // 서버는 진입 ≤ 작업 시작 ≤ 떠남 순서만 받는다. 떠나기 시작한 뒤에 "상차 시작"을 눌렀으면 그때로 맞춘다
        val record = snapshot.value.wait?.takeIf { it.stopType == stop }
        val departedAt = at?.let { maxOf(it, record?.workStartedAt ?: record?.arrivedAt ?: it) }
        waitRepository.depart(trip.id, stop, departedAt).orShow() ?: return@runAction
        when (stop) {
            StopType.PICKUP -> {
                announcer.say(HomeAnnouncements.leftPickup(trip.pickup.name, trip.dropoff.name))
                reloadCurrent(wait = null)
                guideTo(trip.dropoff)
            }
            StopType.DROPOFF -> {
                announcer.say(HomeAnnouncements.tripCompleted(trip.fare))
                closeDeliveryProof()
                snapshot.update { it.copy(current = null, wait = null, site = null) }
                show(TRIP_COMPLETED_MESSAGE)
                load()
            }
        }
    }

    override fun resumeGuidance() {
        val trip = snapshot.value.current ?: return
        trip.currentStop?.let { guideTo(trip.stop(it)) }
    }

    override fun sendWaitProof() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        val link = waitRepository.createProof(trip.id, stop).orShow() ?: return@runAction
        _status.update { it.copy(proof = link) }
    }

    private var signatureWatch: Job? = null

    override fun requestDeliveryProof() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        if (stop != StopType.DROPOFF) return@runAction
        val proof = deliveryProofRepository.issue(trip.id).orShow() ?: return@runAction
        _status.update { it.copy(deliveryProof = proof) }
        if (proof.isConfirmed) onDeliveryConfirmed(proof) else watchSignature(trip.id)
    }

    /** 창을 닫으면 서명 확인도 멈춘다. 다시 누르면 같은 링크로 이어서 기다린다 */
    fun closeDeliveryProof() {
        signatureWatch?.cancel()
        _status.update { it.copy(deliveryProof = null) }
    }

    /** 수하인 서명을 몇 초마다 확인한다. 통신이 잠깐 끊겨도 계속 기다린다 */
    private fun watchSignature(tripId: Long) {
        signatureWatch?.cancel()
        signatureWatch = viewModelScope.launch {
            while (true) {
                delay(SIGNATURE_POLL_MILLIS)
                val proof = (deliveryProofRepository.get(tripId) as? ApiResult.Success)?.data ?: continue
                _status.update { it.copy(deliveryProof = proof) }
                if (proof.isConfirmed) {
                    onDeliveryConfirmed(proof)
                    return@launch
                }
            }
        }
    }

    /** 서명하면 서버가 운행을 끝낸다 (POD-008). 창은 그대로 두고 뒤의 홈을 새로 받는다 */
    private suspend fun onDeliveryConfirmed(proof: DeliveryProof) {
        announcer.say(HomeAnnouncements.deliverySigned(proof.signerName))
        show(DELIVERY_SIGNED_MESSAGE)
        load()
    }

    fun messageShown() = _status.update { it.copy(message = null) }

    fun proofShown() = _status.update { it.copy(proof = null) }

    /**
     * 홈에 필요한 것을 한꺼번에 받는다: 내 배차, 진행 중인 운행, 오늘 요약, 미수금, 모집 중 오더.
     * 하나가 실패해도 나머지는 보여 준다.
     */
    private suspend fun load() {
        val upcoming = viewModelScope.async { tripRepository.myTrips() }
        val current = viewModelScope.async { tripRepository.currentTrip() }
        val today = viewModelScope.async { ledgerRepository.today() }
        val receivables = viewModelScope.async { ledgerRepository.receivables() }
        val near = sensors.location.value
        val orders = viewModelScope.async { orderRepository.openOrders(limit = HOME_ORDER_COUNT, near = near) }
        upcoming.await().orShow()?.let { trips -> snapshot.update { it.copy(upcoming = upcomingOf(trips)) } }
        today.await().orShow()?.let { summary -> snapshot.update { it.copy(today = summary) } }
        receivables.await().orShow()?.let { owed -> snapshot.update { it.copy(receivables = owed) } }
        orders.await().orShow()?.let { list -> snapshot.update { it.copy(orders = list, ordersNear = near) } }
        val trip = current.await().orShow() ?: run {
            snapshot.update { it.copy(current = null, wait = null, site = null) }
            return
        }
        snapshot.update { it.copy(current = trip, wait = if (trip.status.isAtStop) waitAt(trip) else null) }
        loadSite(trip)
    }

    /** 서버가 바꾼 운행 상태를 다시 받는다 (도착 → 대기 중, 출발 → 운송 중) */
    private suspend fun reloadCurrent(wait: WaitRecord?) {
        val trip = tripRepository.currentTrip().orShow()
        snapshot.update { it.copy(current = trip, wait = wait) }
        loadSite(trip)
    }

    /** 상하차지에 있으면 그곳의 정보 카드를 받는다. 못 받아도 대기 화면은 그대로 보여 준다 */
    private suspend fun loadSite(trip: Trip?) {
        val stop = trip?.takeIf { it.status.isAtStop }?.currentStop?.let(trip::stop)
        if (stop == null) {
            snapshot.update { it.copy(site = null) }
            return
        }
        if (snapshot.value.site?.id == stop.siteId) return
        val card = (siteRepository.card(stop.siteId) as? ApiResult.Success)?.data
        snapshot.update { it.copy(site = card) }
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

    /**
     * 자동 도착 (WAIT-001): 1초마다 지금 가는 상하차지 반경 안에서 멈춰 있는지 본다.
     * 3분이 차면 멈추기 시작한 시각으로 도착을 알린다. 가는 곳이 바뀌면 처음부터 센다.
     */
    private fun arriveWhenStoppedAtStop() {
        viewModelScope.launch {
            var fenceOf: Pair<Long, StopType>? = null
            while (true) {
                delay(SENSOR_CHECK_MILLIS)
                val trip = snapshot.value.current?.takeIf { it.status.isDriving }
                val stop = trip?.currentStop
                if (trip == null || stop == null) {
                    fenceOf = null
                    arrivalCheck.value = ArrivalCheck.NotYet
                    continue
                }
                if (fenceOf != trip.id to stop) {
                    fenceOf = trip.id to stop
                    arrivalDetector.reset()
                }
                announceApproach(trip, stop, sensors.location.value)
                val check = arrivalDetector.check(clock.instant(), fenceOf(trip.stop(stop)), sensors.location.value, sensors.motion.value)
                arrivalCheck.value = check
                if (check is ArrivalCheck.Arrived && !_status.value.isBusy) {
                    arrivalDetector.reset()
                    arriveAt(check.at)
                }
            }
        }
    }

    /**
     * 자동 떠남 (WAIT-005): 1초마다 지금 있는 상하차지를 떠났는지 본다.
     * 떠났으면 반경을 벗어나거나 달리기 시작한 시각으로 알린다. 상차지면 하차지로 안내하고, 하차지면 운행이 끝난다.
     */
    private fun departWhenLeavingStop() {
        viewModelScope.launch {
            var stayingAt: Pair<Long, StopType>? = null
            while (true) {
                delay(SENSOR_CHECK_MILLIS)
                val atStop = currentAtStop()?.takeIf { (_, stop) -> snapshot.value.wait?.stopType == stop }
                if (atStop == null) {
                    stayingAt = null
                    departureCheck.value = DepartureCheck.Staying
                    continue
                }
                val (trip, stop) = atStop
                if (stayingAt != trip.id to stop) {
                    stayingAt = trip.id to stop
                    departureDetector.reset()
                }
                val check = departureDetector.check(clock.instant(), fenceOf(trip.stop(stop)), sensors.location.value, sensors.motion.value)
                departureCheck.value = check
                if (check is DepartureCheck.Departed && !_status.value.isBusy) {
                    departureDetector.reset()
                    departAt(check.at)
                }
            }
        }
    }

    /** 정보 카드를 읽어 준 정차 지점. 운행·지점마다 한 번만 */
    private val approachAnnounced = mutableSetOf<Pair<Long, StopType>>()

    /** 가는 상하차지 3km 안에 처음 들어오면 정보 카드를 읽어 준다 (SITE-006). 높이 제한이 걸리면 경고로 */
    private fun announceApproach(trip: Trip, stop: StopType, here: GeoPoint?) {
        val target = trip.stop(stop)
        if (here == null || trip.id to stop in approachAnnounced) return
        if (GeoPoint(target.latitude, target.longitude).distanceTo(here) > APPROACH_ANNOUNCE_METERS) return
        approachAnnounced += trip.id to stop
        viewModelScope.launch {
            val card = (siteRepository.card(target.siteId) as? ApiResult.Success)?.data ?: return@launch
            HomeAnnouncements.approaching(card)?.let(announcer::say)
        }
    }

    /**
     * 무료 대기 시간이 끝나는 순간 "지금부터 대기비가 붙어요" (WAIT-012).
     * 작업을 시작하면 대기 기록이 바뀌어 취소된다. 앱을 켰을 때 이미 지났으면 말하지 않는다.
     */
    private fun announceChargeableStart() {
        viewModelScope.launch {
            snapshot.map { it.wait }.distinctUntilChanged().collectLatest { wait ->
                val policy = wait?.policy ?: return@collectLatest
                if (wait.workStartedAt != null || wait.departedAt != null) return@collectLatest
                val startsAt = wait.chargeableFrom.plus(Duration.ofMinutes(policy.freeMinutes.toLong()))
                val millis = Duration.between(clock.instant(), startsAt).toMillis()
                if (millis <= 0) return@collectLatest
                delay(millis)
                announcer.say(HomeAnnouncements.chargeableStarted(policy))
            }
        }
    }

    private fun fenceOf(stop: TripStop) = GeoFence(GeoPoint(stop.latitude, stop.longitude), stop.geofenceRadiusMeters)

    /**
     * 운행 전 화면의 근처 오더를 차 위치에 맞춘다: 처음 위치가 들어올 때, 그리고 찾은 곳에서 5km 넘게 움직였을 때 다시 찾는다.
     * 운행 중에는 오더를 보여 주지 않으니 찾지 않는다 (운행이 끝나면 [load]가 지금 위치로 찾는다).
     * 실패하면 조용히 넘어가고, 다시 5km 움직이면 또 찾는다. 통신이 끊긴 동안 몇 초마다 묻지 않게 하려는 것이다.
     */
    private fun keepNearbyOrdersFresh() {
        viewModelScope.launch {
            var lastTried: GeoPoint? = null
            sensors.location.filterNotNull().collect { here ->
                if (snapshot.value.current != null) return@collect
                val from = snapshot.value.ordersNear ?: lastTried
                if (from != null && from.distanceTo(here) < NEARBY_REFRESH_METERS) return@collect
                lastTried = here
                val list = (orderRepository.openOrders(limit = HOME_ORDER_COUNT, near = here) as? ApiResult.Success)?.data
                    ?: return@collect
                snapshot.update { it.copy(orders = list, ordersNear = here) }
            }
        }
    }

    /** 내비가 주행 정보를 띄엄띄엄 보내서, 안내 중에만 10초마다 남은 거리·시간을 다시 묻는다 */
    /** 출발한 운행이 있는 동안 위치를 서버로 보낸다 (TRIP-013). 끝나거나 취소되면 멈춘다 */
    private fun reportLocationWhileOnTrip() {
        viewModelScope.launch {
            snapshot.map { it.current?.id }.distinctUntilChanged().collect(locationTracker::track)
        }
    }

    /** 운송 중 진행률을 추천에 알려 준다. 찾기·안내는 [NextOrderAdvisor]가 정한다 */
    private fun recommendNextOrder() {
        viewModelScope.launch {
            combine(snapshot.map { it.current }.distinctUntilChanged(), navigator.progress.map { it?.fraction }) { trip, fraction ->
                trip to fraction
            }.distinctUntilChanged().collect { (trip, fraction) -> nextOrderAdvisor.onProgress(trip, fraction) }
        }
    }

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
        const val DELIVERY_SIGNED_MESSAGE = "인수증 서명을 받았어요. 운행을 마쳤어요"
        private const val SIGNATURE_POLL_MILLIS = 3_000L
        private const val NAVI_REFRESH_MILLIS = 10_000L
        private const val SENSOR_CHECK_MILLIS = 1_000L

        /** 상하차지 정보 카드를 읽어 주는 거리 (SITE-006) */
        private const val APPROACH_ANNOUNCE_METERS = 3_000.0

        /** 근처 오더를 찾은 곳에서 이만큼 움직이면 다시 찾는다 */
        private const val NEARBY_REFRESH_METERS = 5_000.0

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
                    siteRepository = container.siteRepository,
                    deliveryProofRepository = container.deliveryProofRepository,
                    navigator = container.navigator,
                    sensors = container.vehicleSensors,
                    announcer = container.announcer,
                    locationTracker = container.locationTracker,
                    recommendationRepository = container.recommendationRepository,
                )
            }
        }
    }
}
