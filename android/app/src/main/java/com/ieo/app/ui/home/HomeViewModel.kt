package com.ieo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.ledger.TripDistanceRecorder
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.restarea.RestAdvisor
import com.ieo.app.domain.restarea.RestAreaRepository
import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.restarea.RouteRestStopsAt
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.trip.LocationTracker
import com.ieo.app.domain.trip.RestCheck
import com.ieo.app.domain.trip.RestLevel
import com.ieo.app.domain.trip.RestTimer
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleSensors
import com.ieo.app.domain.voice.Announcer
import com.ieo.app.domain.voice.AnswerClassifier
import com.ieo.app.domain.voice.NoAnswerClassifier
import com.ieo.app.domain.voice.NoSpeechRecognizer
import com.ieo.app.domain.voice.SpeechRecognizer
import com.ieo.app.domain.voice.YesNo
import com.ieo.app.domain.voice.YesNoDialog
import com.ieo.app.domain.voice.speak
import com.ieo.app.domain.wait.ArrivalCheck
import com.ieo.app.domain.wait.ArrivalDetector
import com.ieo.app.domain.wait.DepartureCheck
import com.ieo.app.domain.wait.DepartureDetector
import com.ieo.app.domain.wait.GeoFence
import com.ieo.app.domain.wait.MemoryPendingWaitStore
import com.ieo.app.domain.wait.PendingWait
import com.ieo.app.domain.wait.PendingWaitStore
import com.ieo.app.domain.wait.WaitMark
import com.ieo.app.domain.wait.WaitOutbox
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import com.ieo.app.domain.wait.WaitSource
import com.ieo.app.ui.common.josa
import com.ieo.app.ui.component.BannerKind
import com.ieo.app.ui.component.IeoBanner
import com.ieo.app.ui.component.NOTIFICATION_VISIBLE_MS
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 화면 위에 잠깐 띄우는 것들 */
data class HomeStatus(
    /** 서버 요청 중. 버튼을 두 번 누르지 않게 한다 */
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 ([HomeViewModel.messageShown]) */
    val message: String? = null,
    /** 서버 알림 배너. 맨 앞 것부터 하나씩 보여 준다 ([HomeViewModel.bannerDone]) */
    val banners: List<IeoBanner> = emptyList(),
    /** 차주 대답을 듣는 중 (마이크가 열려 있다) */
    val listening: Boolean = false,
    /** 대기 증빙 링크 창. 닫으면 null ([HomeViewModel.proofShown]) */
    val proof: WaitProofLink? = null,
    /** 도착·떠남 확인 창 (WAIT-007, 005). 대답하거나 시간이 지나면 null */
    val stopQuestion: StopQuestion? = null,
    /** "운행 완료"를 누르고 대기 증빙을 만들지 고르는 창 (WAIT-020) */
    val finishing: Boolean = false,
    /** 가운데 창에 띄운 추천 오더 (03 배차 추천). 15초가 지나거나 넘기면 다음 추천, 닫으면 null */
    val pick: NextOrderCard? = null,
)

/** 홈 화면이 ViewModel에 보내는 동작 */
interface HomeActions {
    /** 내 배차에서 "안내 시작": 출발 처리 후 상차지로 길 안내 */
    fun startTrip(tripId: String)

    /** "대기 시작": 확인 없이 지금부터 대기를 시작한다 (WAIT-008). 도착 확인 창이 떠 있으면 그 확인과 같다 */
    fun arrive()

    /** 도착·떠남 확인 창의 "맞아요": 감지한 때로 대기를 시작하거나 끝낸다 (WAIT-007, 005) */
    fun confirmStopQuestion()

    /** 확인 창의 "아직이에요": 창을 닫고, 같은 정차 중에는 다시 묻지 않는다 */
    fun declineStopQuestion()

    /** "운행 완료" 창에서 완료. [makeProof]면 이 운행의 대기 증빙을 만들어 보여 준다 */
    fun finishTrip(makeProof: Boolean)

    /** "운행 완료" 창을 닫는다 */
    fun cancelFinish()

    /** "상차 시작" · "하차 시작" */
    fun startWork()

    /** "상차 끝, 출발"은 바로 떠나고, "운행 완료"는 대기 증빙을 고르는 창을 연다 */
    fun depart()

    /** 내비 안내가 꺼졌을 때 다시 켜기 */
    fun resumeGuidance()

    /** 지금 상하차지의 대기 증빙 링크를 만들어 보여 준다 */
    fun sendWaitProof()
    fun sendProofToBroker()

    /** 추천 카드를 눌러 그 오더를 가운데 창에 연다 (03 배차 추천) */
    fun openPick(orderId: Long)

    fun closePick()

    /** 창의 "넘기기"나 15초가 지남: 세웠을 때 자동으로 띄운 것이면 다음 추천을, 카드로 연 것이면 닫는다. 기록하지 않는다 */
    fun nextPick()

    /** 연 추천 오더에 배차를 요청한다. 요청에 "추천에서 눌렀음"(isRecommendation)이 남는다 */
    fun requestPick()

    /** 세워 두었을 때 경로 위 휴게소를 내비 경유지로 넣는다 (TRIP-032) */
    fun addRestStopWaypoint(stop: RestStop)

    fun refresh()
}

/**
 * 홈 화면: 운행 진행(출발 → 상차지 도착 → 상차 → 출발 → 하차지 도착 → 하차 → 완료)을 서버에 알리고,
 * 그 결과로 화면 상태를 바꾼다. 화면을 고르는 규칙은 [HomeStateFactory]가 갖고 있다.
 *
 * 상하차지 도착: 반경 안에서 3분 세워 두거나(WAIT-001) 차량 내비가 "목적지 도착"을 알리면 음성과 창으로 묻고,
 * 차주가 맞다고 하면 감지한 때부터 대기를 시작한다 (WAIT-007). "대기 시작" 버튼은 묻지 않고 바로 시작한다 (WAIT-008).
 * 떠남은 반경을 벗어나거나 시속 20km 이상으로 1분 달린 때(WAIT-005), 또는 "출발"·"운행 완료" 버튼.
 * 상하차지 3km 전, 도착, 대기비 시작, 떠남은 음성으로도 알린다 (문장은 [HomeAnnouncements]).
 * 배차 추천: 상하차지에서(운행이 없으면 아무 데서나) 차를 세우면 근처 추천 1건을 받아 보여 준다 (03 배차 추천).
 */
class HomeViewModel(
    private val tripRepository: TripRepository,
    private val waitRepository: WaitRepository,
    private val ledgerRepository: LedgerRepository,
    private val orderRepository: OrderRepository,
    private val siteRepository: SiteRepository,
    private val navigator: Navigator,
    private val sensors: VehicleSensors,
    private val announcer: Announcer,
    private val locationTracker: LocationTracker,
    private val recommendationRepository: RecommendationRepository,
    /** 경로 위 휴게소·졸음쉼터 (TRIP-032) */
    private val restAreaRepository: RestAreaRepository = RestAreaRepository.None,
    /** 서버 알림 (09 알림). 앱 전체의 [com.ieo.app.domain.notification.NotificationListener]가 받는다 */
    private val notifications: Flow<AppNotification> = emptyFlow(),
    private val recognizer: SpeechRecognizer = NoSpeechRecognizer,
    /** 단어 목록으로 못 알아들은 예/아니오 대답을 서버에서 판별한다 (UX-025) */
    private val answerClassifier: AnswerClassifier = NoAnswerClassifier,
    /** 통신이 끊겨 보내지 못한 대기 요청. 앱이 꺼져도 남는다 */
    pendingWaits: PendingWaitStore = MemoryPendingWaitStore(),
    /** 운행 거리를 재서 장부에 넣는다 (LEDG-001). 없으면 재지 않는다 */
    private val tripDistance: TripDistanceRecorder? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val stateFactory: HomeStateFactory = HomeStateFactory(),
) : ViewModel(), HomeActions {

    private val snapshot = MutableStateFlow(HomeSnapshot())

    /** 상하차지 반경 안에서 멈춘 동안 남은 시간, 상하차지 3km 안인지, 세워 두었는지를 화면에 보여 준다 */
    private val arrivalView = MutableStateFlow(ArrivalView())
    private val arrivalDetector = ArrivalDetector()

    /** 통신이 끊긴 동안의 대기 시작·작업 시작·떠남을 쌓아 두었다가 다시 보낸다 */
    private val outbox = WaitOutbox(waitRepository, pendingWaits)

    /** 묻고 있는 도착·떠남. 대답하면 null */
    private var pendingQuestion: PendingQuestion? = null
    private var questionJob: Job? = null

    /** 떠남을 물었는데 아니라고 했다. 반경 안으로 다시 들어오기 전에는 묻지 않는다 */
    private var departureOnHold = false

    /** 이미 물은 정차에서는 다시 묻지 않는다. 차가 움직이거나 반경을 벗어나면 풀린다 */
    private var holdUntilMoved = false

    /** 내비 도착 알림으로 물은 정차 지점. 운행·지점마다 한 번 */
    private val naviArrivalAsked = mutableSetOf<Pair<Long, StopType>>()

    /** 상하차지를 떠나는 동안 남은 시간을 화면에 보여 준다 */
    private val departureCheck = MutableStateFlow<DepartureCheck>(DepartureCheck.Staying)
    private val departureDetector = DepartureDetector()

    /** 연속 운전 시간 (TRIP-030). 달리고 있지 않으면 null */
    private val restCheck = MutableStateFlow<RestCheck?>(null)
    private val restTimer = RestTimer()

    /** 받아 둔 경로 위 쉴 곳 (TRIP-032). 운행 중이 아니면 null */
    private val routeStops = MutableStateFlow<RouteRestStopsAt?>(null)

    val state: StateFlow<HomeState> =
        combine(snapshot, navigator.progress, arrivalView, departureCheck, combine(restCheck, routeStops, ::Pair)) { snapshot, navi, arrival, departure, (rest, stops) ->
            stateFactory.create(snapshot, navi, arrival.check, departure, arrival.nearStop, arrival.parked, rest, stops)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateFactory.create(HomeSnapshot(), null))

    private val _status = MutableStateFlow(HomeStatus())
    val status: StateFlow<HomeStatus> = _status.asStateFlow()

    /** 듣는 동안 화면에 "듣는 중"을 띄운다 */
    private val listeningRecognizer = object : SpeechRecognizer {
        override suspend fun listen(timeoutMillis: Long): String? {
            _status.update { it.copy(listening = true) }
            try {
                return recognizer.listen(timeoutMillis)
            } finally {
                _status.update { it.copy(listening = false) }
            }
        }
    }
    private val stopDialog = YesNoDialog(
        recognizer = listeningRecognizer,
        classifier = answerClassifier,
        speak = { announcer.speak(HomeAnnouncements.arrivalLine(it)) },
    )

    /** 세워 두었다 (기어 P 또는 주차 브레이크). 확인 창의 버튼은 이때만 눌린다. 떠남 확인은 대기 화면에서도 뜨므로 차 신호를 바로 쓴다 */
    val parked: StateFlow<Boolean> = sensors.motion.map { it?.isParked == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            load()
            resumeGuidanceAfterRestart()
        }
        retryPendingWaits()
        askWhenNaviArrives()
        askWhenStoppedAtStop()
        departWhenLeavingStop()
        announceChargeableStart()
        keepNaviProgressFresh()
        reportLocationWhileOnTrip()
        recommendWhenParked()
        tellNotifications()
        tellWhenRestIsDue()
        rememberDropoffRoute()
    }

    /** 하차지로 처음 안내받은 경로 거리를 적어 둔다. 운행을 마칠 때 장부의 주행거리로 쓴다 (LEDG-001) */
    private fun rememberDropoffRoute() {
        val recorder = tripDistance ?: return
        viewModelScope.launch {
            navigator.progress.collect { progress ->
                val trip = snapshot.value.current ?: return@collect
                val total = progress?.totalMeters ?: return@collect
                if (trip.currentStop == StopType.DROPOFF) recorder.routeToDropoff(trip.id, total)
            }
        }
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

    override fun arrive() {
        if (pendingQuestion?.kind == StopQuestionKind.ARRIVE) return confirmStopQuestion()
        arriveAt(at = null, source = WaitSource.BUTTON)
    }

    override fun confirmStopQuestion() {
        questionJob?.cancel()
        answerStopQuestion(yes = true, source = WaitSource.CONFIRM_TOUCH)
    }

    override fun declineStopQuestion() {
        questionJob?.cancel()
        answerStopQuestion(yes = false, source = WaitSource.CONFIRM_TOUCH)
    }

    /**
     * 도착(WAIT-007)이나 떠남(WAIT-005)을 묻는다: 창을 띄우고 말한 뒤 15초 동안 대답을 듣는다.
     * 단어 목록으로 못 알아들은 말은 서버에서 판별한다 (UX-025). 대답이 없으면 창을 닫는다
     */
    private fun askStopQuestion(kind: StopQuestionKind, trip: Trip, stop: StopType, at: Instant) {
        if (pendingQuestion != null) return
        pendingQuestion = PendingQuestion(kind, trip.id, stop, at)
        val name = trip.stop(stop).name
        val isPickup = stop == StopType.PICKUP
        // 도착·떠남 확인이 추천 창보다 먼저다
        closePick()
        _status.update { it.copy(stopQuestion = StopQuestion(kind, name, isPickup, stateFactory.time(at))) }
        questionJob = viewModelScope.launch {
            val question = when (kind) {
                StopQuestionKind.ARRIVE -> HomeAnnouncements.arrivalQuestion(name, at)
                StopQuestionKind.DEPART -> HomeAnnouncements.departureQuestion(name, isPickup)
                StopQuestionKind.PROOF -> HomeAnnouncements.proofQuestion()
            }
            announcer.speak(question)
            _status.update { status -> status.copy(stopQuestion = status.stopQuestion?.copy(asked = true)) }
            // 창은 15초를 채운다. 음성 인식이 일찍 끝나도(못 들음) 그사이 눌러서 대답할 수 있게
            val window = launch { delay(ARRIVAL_CONFIRM_SECONDS * 1_000L) }
            val answer = stopDialog.listen(question.text, ARRIVAL_CONFIRM_SECONDS * 1_000L)
            if (answer == YesNo.YES || answer == YesNo.NO) window.cancel()
            when (answer) {
                YesNo.YES -> answerStopQuestion(yes = true, source = WaitSource.CONFIRM_VOICE)
                YesNo.NO -> {
                    announcer.say(
                        when (kind) {
                            StopQuestionKind.ARRIVE -> HomeAnnouncements.arrivalDeclined()
                            StopQuestionKind.DEPART -> HomeAnnouncements.departureDeclined(isPickup)
                            StopQuestionKind.PROOF -> HomeAnnouncements.proofDeclined()
                        },
                    )
                    answerStopQuestion(yes = false, source = WaitSource.CONFIRM_VOICE)
                }
                else -> {
                    window.join()
                    answerStopQuestion(yes = false, source = WaitSource.CONFIRM_VOICE)
                }
            }
        }
    }

    /** 창을 닫고, 맞다고 했으면 감지한 때로 대기를 시작하거나 끝낸다 */
    private fun answerStopQuestion(yes: Boolean, source: WaitSource) {
        val pending = pendingQuestion ?: return
        pendingQuestion = null
        announcer.cancel(HomeAnnouncements.ARRIVAL_KEY)
        _status.update { it.copy(stopQuestion = null) }
        when (pending.kind) {
            StopQuestionKind.ARRIVE -> if (yes) arriveAt(pending.at, source)
            StopQuestionKind.DEPART -> if (yes) departAndAskProof(pending, source) else departureOnHold = true
            StopQuestionKind.PROOF -> if (yes) makeProofAfterTrip(pending.tripId)
        }
    }

    /**
     * 서버에 닿지 못했으면 차에 쌓아 두고 true. 시각은 누른 때(또는 감지한 때)로 정해 두어, 나중에 보내도 그때로 남는다.
     * [at]은 요청을 보내기 전에 잡아 둔 시각이다: 통신 실패를 기다리는 동안(10초 넘게) 밀리지 않게 (WAIT-026).
     * 쌓아 둔 것은 [retryPendingWaits]가 다시 보낸다
     */
    private fun queueIfOffline(result: ApiResult<*>, action: PendingWait.Action, trip: Trip, stop: StopType, at: Instant, source: WaitSource): Boolean {
        if ((result as? ApiResult.Failure)?.error !is ApiError.Network) return false
        outbox.add(PendingWait(action, trip.id, stop, at, sensors.location.value, source))
        show(WAIT_QUEUED_MESSAGE)
        return true
    }

    /** 쌓아 둔 대기 요청을 앱을 켤 때와 몇 초마다 다시 보낸다. 보냈으면 화면을 새로 받는다 */
    private fun retryPendingWaits() {
        viewModelScope.launch {
            while (true) {
                if (!outbox.isEmpty) {
                    val result = outbox.flush()
                    result.refused.firstOrNull()?.let(::show)
                    if (result.sent > 0) {
                        show(WAIT_SENT_MESSAGE)
                        load()
                    }
                }
                delay(OUTBOX_RETRY_MILLIS)
            }
        }
    }

    /** 기사님이 확인하거나 누른 순간의 위치와 방법 (WAIT-025) */
    private fun markOf(source: WaitSource) = WaitMark(sensors.location.value, source)

    /** [at]: 도착 확인이면 감지한 때. "대기 시작" 버튼이면 null(서버가 받은 때) */
    private fun arriveAt(at: Instant?, source: WaitSource) = runAction {
        val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return@runAction
        val stop = trip.currentStop ?: return@runAction
        val pressedAt = clock.instant()
        val result = waitRepository.arrive(trip.id, stop, at, markOf(source))
        if (queueIfOffline(result, PendingWait.Action.ARRIVE, trip, stop, at ?: pressedAt, source)) {
            // 보낼 때까지 같은 정차에서 다시 묻지 않는다
            holdUntilMoved = true
            return@runAction
        }
        val record = result.orShow() ?: return@runAction
        announcer.say(HomeAnnouncements.arrived(trip.stop(stop).name, record.policy))
        navigator.stopGuidance()
        reloadCurrent(record)
    }

    override fun startWork() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        val pressedAt = clock.instant()
        val result = waitRepository.startWork(trip.id, stop, mark = markOf(WaitSource.BUTTON))
        if (queueIfOffline(result, PendingWait.Action.START_WORK, trip, stop, pressedAt, WaitSource.BUTTON)) return@runAction
        val record = result.orShow() ?: return@runAction
        snapshot.update { it.copy(wait = record) }
    }

    override fun depart() {
        val (_, stop) = currentAtStop() ?: return
        if (pendingQuestion?.kind == StopQuestionKind.DEPART) return confirmStopQuestion()
        when (stop) {
            StopType.PICKUP -> departAt(at = null, source = WaitSource.BUTTON)
            StopType.DROPOFF -> _status.update { it.copy(finishing = true) }
        }
    }

    override fun cancelFinish() = _status.update { it.copy(finishing = false) }

    override fun finishTrip(makeProof: Boolean) {
        _status.update { it.copy(finishing = false) }
        runAction {
            val tripId = currentAtStop()?.first?.id ?: return@runAction
            if (!departNow(at = null, source = WaitSource.BUTTON)) return@runAction
            if (makeProof) {
                // 운행은 이미 끝났다. 증빙을 못 만들었으면 장부에서 다시 만들 수 있다
                waitRepository.createProof(tripId).orShow()?.let { link ->
                    proofTripId = tripId
                    _status.update { it.copy(proof = link) }
                }
            }
        }
    }

    /** [at]: 떠남 확인이면 반경을 벗어나거나 달리기 시작한 때. 버튼이면 null(서버가 받은 때) */
    private fun departAt(at: Instant?, source: WaitSource) = runAction { departNow(at, source) }

    /**
     * 떠남 확인으로 떠난다. 하차지였으면 운행이 끝났으니 대기 증빙을 만들지 묻는다 (WAIT-020).
     * [운행 완료] 버튼으로 끝낼 때는 창의 체크박스로 고르므로 묻지 않는다. 대부분 이 길로 끝나서 증빙 만들 때를 놓치지 않게 한다
     */
    private fun departAndAskProof(pending: PendingQuestion, source: WaitSource) = runAction {
        val trip = snapshot.value.current?.takeIf { it.id == pending.tripId } ?: return@runAction
        if (!departNow(pending.at, source) || pending.stop != StopType.DROPOFF) return@runAction
        askStopQuestion(StopQuestionKind.PROOF, trip, StopType.DROPOFF, clock.instant())
    }

    private fun makeProofAfterTrip(tripId: Long) = runAction {
        // 운행은 이미 끝났다. 증빙을 못 만들었으면 장부에서 다시 만들 수 있다
        val link = waitRepository.createProof(tripId).orShow() ?: return@runAction
        proofTripId = tripId
        _status.update { it.copy(proof = link) }
        announcer.say(HomeAnnouncements.proofMade())
    }

    /** 떠난다. 상차지면 하차지로 안내하고, 하차지면 운행이 끝난다. 성공하면 true */
    private suspend fun departNow(at: Instant?, source: WaitSource): Boolean {
        val (trip, stop) = currentAtStop() ?: return false
        // 서버는 진입 ≤ 작업 시작 ≤ 떠남 순서만 받는다. 떠나기 시작한 뒤에 "상차 시작"을 눌렀으면 그때로 맞춘다
        val record = snapshot.value.wait?.takeIf { it.stopType == stop }
        val departedAt = at?.let { maxOf(it, record?.workStartedAt ?: record?.arrivedAt ?: it) }
        val pressedAt = clock.instant()
        val result = waitRepository.depart(trip.id, stop, departedAt, markOf(source))
        if (queueIfOffline(result, PendingWait.Action.DEPART, trip, stop, departedAt ?: pressedAt, source)) {
            departureOnHold = true
            return false
        }
        result.orShow() ?: return false
        when (stop) {
            StopType.PICKUP -> {
                announcer.say(HomeAnnouncements.leftPickup(trip.pickup.name, trip.dropoff.name))
                reloadCurrent(wait = null)
                guideTo(trip.dropoff)
            }
            StopType.DROPOFF -> {
                // 인수증 결제면 종이 인수증을 휴대폰으로 보내라고 함께 알린다 (04 인수증)
                announcer.say(HomeAnnouncements.tripCompleted(trip.fare, trip.paysOnReceipt))
                // 장부 줄이 생긴 뒤라 거리를 바로 넣을 수 있다. 경로를 모르면 기사님이 장부에서 넣는다
                tripDistance?.let { recorder -> viewModelScope.launch { recorder.completed(trip.id) } }
                snapshot.update { it.copy(current = null, wait = null, site = null) }
                show(if (trip.paysOnReceipt) RECEIPT_REMINDER_MESSAGE else TRIP_COMPLETED_MESSAGE)
                load()
            }
        }
        return true
    }

    override fun resumeGuidance() {
        val trip = snapshot.value.current ?: return
        trip.currentStop?.let { guideTo(trip.stop(it)) }
    }

    /** 보여 주고 있는 증빙 링크의 운행 */
    private var proofTripId: Long? = null

    override fun sendWaitProof() = runAction {
        val (trip, _) = currentAtStop() ?: return@runAction
        val link = waitRepository.createProof(trip.id).orShow() ?: return@runAction
        proofTripId = trip.id
        _status.update { it.copy(proof = link) }
    }

    override fun sendProofToBroker() = runAction {
        // 운행이 끝난 뒤(운행 완료 때 만든 증빙)에도 보낼 수 있게 링크의 운행으로 보낸다
        val tripId = proofTripId ?: currentAtStop()?.first?.id ?: return@runAction
        val link = waitRepository.sendProof(tripId).orShow() ?: return@runAction
        _status.update { it.copy(proof = link) }
    }

    /** 세웠을 때 1등부터 차례로 띄우는 중이다. 카드를 눌러 연 것이면 false */
    private var pickChain = false

    override fun openPick(orderId: Long) {
        pickChain = false
        showPick(orderId)
    }

    private fun showPick(orderId: Long) {
        val next = snapshot.value.nextOrders.firstOrNull { it.order.id == orderId } ?: return
        _status.update { it.copy(pick = stateFactory.nextOrderCard(next)) }
    }

    override fun closePick() {
        pickChain = false
        _status.update { it.copy(pick = null) }
    }

    override fun nextPick() {
        val current = _status.value.pick ?: return
        val list = snapshot.value.nextOrders
        val following = list.getOrNull(list.indexOfFirst { it.order.id == current.orderId } + 1)
        if (pickChain && following != null) showPick(following.order.id) else closePick()
    }

    override fun requestPick() = runAction {
        val pick = _status.value.pick ?: return@runAction
        orderRepository.request(pick.orderId, isRecommendation = true).orShow() ?: return@runAction
        closePick()
        snapshot.update { it.copy(nextOrders = it.nextOrders.filterNot { next -> next.order.id == pick.orderId }) }
        show(NEXT_ORDER_REQUESTED_MESSAGE)
    }

    /**
     * 배차 추천 (03, 2026-11-13): 차를 세우면(기어 P 또는 주차 브레이크) 추천 1건을 받는다.
     * 상차지·하차지에 도착해 있으면 서버가 그곳 근처를, 운행이 없으면 세운 자리 근처를 찾는다.
     * 상차지로 가는 중·운송 중(휴게소 등)에는 묻지 않는다. 세워 둔 채 도착을 확인해도 그때 한 번 묻는다.
     * 같은 곳에서 다시 받은 것(fresh=false)은 홈 카드에만 두고 가운데 창은 띄우지 않는다. 움직이면 지운다
     */
    private fun recommendWhenParked() {
        viewModelScope.launch {
            combine(parked, snapshot.map { it.current?.status?.isDriving == true }) { isParked, driving -> isParked to driving }
                .distinctUntilChanged()
                .collectLatest { (isParked, driving) ->
                    if (!isParked || driving) {
                        snapshot.update { it.copy(nextOrders = emptyList()) }
                        closePick()
                        return@collectLatest
                    }
                    val here = sensors.location.filterNotNull().first()
                    val result = (recommendationRepository.recommend(here) as? ApiResult.Success)?.data ?: return@collectLatest
                    snapshot.update { it.copy(nextOrders = result.items) }
                    val first = result.items.firstOrNull() ?: return@collectLatest
                    if (result.fresh && _status.value.stopQuestion == null && !_status.value.finishing) {
                        pickChain = true
                        showPick(first.order.id)
                    }
                }
        }
    }

    fun messageShown() = _status.update { it.copy(message = null) }

    private fun bannerOf(notification: AppNotification) = IeoBanner(
        id = notification.id,
        text = notification.message,
        kind = if (notification.type == NotificationType.DISPATCH_CONFIRMED) BannerKind.GOOD else BannerKind.INFO,
        visibleMs = NOTIFICATION_VISIBLE_MS,
    )

    /** 배너를 다 보여 줬거나 닫았다. 다음 배너가 있으면 이어서 나온다 */
    fun bannerDone(id: Long) = _status.update { status -> status.copy(banners = status.banners.filterNot { it.id == id }) }

    /** 서버 알림을 말하고 배너로 띄운다. 확정·취소면 내 배차가 바뀌었으니 홈을 새로 받는다 */
    private fun tellNotifications() {
        viewModelScope.launch {
            notifications.collect { notification ->
                announcer.say(HomeAnnouncements.notification(notification))
                _status.update { it.copy(banners = it.banners + bannerOf(notification)) }
                if (notification.type in HomeAnnouncements.TRIP_CHANGES) load()
            }
        }
    }

    fun proofShown() = _status.update { it.copy(proof = null) }

    /**
     * 홈에 필요한 것을 한꺼번에 받는다: 내 배차, 진행 중인 운행, 오늘 요약, 미수금.
     * 하나가 실패해도 나머지는 보여 준다.
     */
    private suspend fun load() {
        val upcoming = viewModelScope.async { tripRepository.myTrips() }
        val current = viewModelScope.async { tripRepository.currentTrip() }
        val today = viewModelScope.async { ledgerRepository.today() }
        val receivables = viewModelScope.async { ledgerRepository.receivables() }
        upcoming.await().orShow()?.let { trips -> snapshot.update { it.copy(upcoming = upcomingOf(trips)) } }
        today.await().orShow()?.let { summary -> snapshot.update { it.copy(today = summary) } }
        receivables.await().orShow()?.let { owed -> snapshot.update { it.copy(receivables = owed) } }
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

    /**
     * 시동을 다시 걸면 앱이 새로 뜨면서 안내 상태를 잃는다. 달리던 운행이 있으면 차가 움직이기 시작할 때 한 번만 이어서 안내한다.
     * 길 안내를 요청하면 내비가 이에오를 가리므로, 정차 중에 앱을 연 기사님을 방해하지 않게 기다린다.
     * 내비가 받지 못하면 화면의 "길 안내 다시"로 이어진다
     */
    private suspend fun resumeGuidanceAfterRestart() {
        if (snapshot.value.current?.status?.isDriving != true) return
        sensors.motion.filterNotNull().first { it.gear == Gear.DRIVE || !it.isStopped }
        if (navigator.progress.value != null) return
        val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return
        val stop = trip.stop(trip.currentStop ?: return)
        navigator.startGuidance(Destination(stop.name, stop.address, stop.latitude, stop.longitude))
    }

    private fun guideTo(stop: TripStop) {
        val started = navigator.startGuidance(Destination(stop.name, stop.address, stop.latitude, stop.longitude))
        if (!started) show(NAVI_FAILED_MESSAGE)
    }

    /** 차량 내비가 "목적지 도착"을 알리면 묻는다. 달리는 중일 수 있어 음성이 먼저고, 터치는 세워 두었을 때만 받는다 */
    private fun askWhenNaviArrives() {
        viewModelScope.launch {
            navigator.progress.filter { it?.arrived == true }.collect {
                val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return@collect
                val stop = trip.currentStop ?: return@collect
                if (naviArrivalAsked.add(trip.id to stop)) askStopQuestion(StopQuestionKind.ARRIVE, trip, stop, clock.instant())
            }
        }
    }

    /**
     * 도착 감지 (WAIT-001): 1초마다 지금 가는 상하차지 반경 안에 세워 두었는지(기어 P 또는 주차 브레이크) 본다.
     * 3분이 차면 멈추기 시작한 시각으로 도착을 묻는다. 가는 곳이 바뀌면 처음부터 센다.
     * 물은 뒤에는 차가 움직이거나 반경을 벗어날 때까지 다시 세지 않는다.
     * 화면의 "대기 시작" 버튼에 쓰려고 상하차지 3km 안인지, 세워 두었는지도 함께 적는다.
     */
    private fun askWhenStoppedAtStop() {
        viewModelScope.launch {
            var fenceOf: Pair<Long, StopType>? = null
            while (true) {
                delay(SENSOR_CHECK_MILLIS)
                val motion = sensors.motion.value
                val here = sensors.location.value
                val parked = motion?.isParked == true
                val trip = snapshot.value.current?.takeIf { it.status.isDriving }
                val stop = trip?.currentStop
                if (trip == null || stop == null) {
                    fenceOf = null
                    // 묻는 사이 운행이 바뀌었으면(취소, 다른 화면에서 도착 등) 창을 닫는다
                    if (pendingQuestion?.kind == StopQuestionKind.ARRIVE) declineStopQuestion()
                    arrivalView.value = ArrivalView(parked = parked)
                    continue
                }
                if (fenceOf != trip.id to stop) {
                    fenceOf = trip.id to stop
                    arrivalDetector.reset()
                    holdUntilMoved = false
                }
                announceApproach(trip, stop, here)
                val target = trip.stop(stop)
                val fence = fenceOf(target)
                if (holdUntilMoved && ((motion != null && !motion.isParked) || (here != null && !fence.contains(here)))) {
                    holdUntilMoved = false
                }
                val check = if (holdUntilMoved || pendingQuestion != null) {
                    arrivalDetector.reset()
                    ArrivalCheck.NotYet
                } else {
                    arrivalDetector.check(clock.instant(), fence, here, motion)
                }
                val nearStop = here != null && GeoPoint(target.latitude, target.longitude).distanceTo(here) <= APPROACH_ANNOUNCE_METERS
                arrivalView.value = ArrivalView(check, nearStop, parked)
                if (check is ArrivalCheck.Arrived) {
                    arrivalDetector.reset()
                    holdUntilMoved = true
                    askStopQuestion(StopQuestionKind.ARRIVE, trip, stop, check.at)
                }
            }
        }
    }

    /**
     * 떠남 감지 (WAIT-005): 1초마다 지금 있는 상하차지를 떠났는지 본다.
     * 떠났으면 반경을 벗어나거나 달리기 시작한 시각으로 묻는다: "하차 끝내고 운행을 마칠까요?"
     * 아니라고 하거나 대답이 없으면 반경 안으로 다시 들어오기 전에는 묻지 않는다 (그동안은 버튼으로 끝낸다).
     */
    private fun departWhenLeavingStop() {
        viewModelScope.launch {
            var stayingAt: Pair<Long, StopType>? = null
            while (true) {
                delay(SENSOR_CHECK_MILLIS)
                val atStop = currentAtStop()?.takeIf { (_, stop) -> snapshot.value.wait?.stopType == stop }
                if (atStop == null) {
                    stayingAt = null
                    departureOnHold = false
                    if (pendingQuestion?.kind == StopQuestionKind.DEPART) declineStopQuestion()
                    departureCheck.value = DepartureCheck.Staying
                    continue
                }
                val (trip, stop) = atStop
                val fence = fenceOf(trip.stop(stop))
                if (stayingAt != trip.id to stop) {
                    stayingAt = trip.id to stop
                    departureDetector.reset()
                    departureOnHold = false
                }
                val here = sensors.location.value
                if (departureOnHold && here != null && fence.contains(here)) departureOnHold = false
                if (departureOnHold || pendingQuestion != null) {
                    departureDetector.reset()
                    departureCheck.value = DepartureCheck.Staying
                    continue
                }
                val check = departureDetector.check(clock.instant(), fence, here, sensors.motion.value)
                departureCheck.value = check
                if (check is DepartureCheck.Departed && !_status.value.isBusy) {
                    departureDetector.reset()
                    askStopQuestion(StopQuestionKind.DEPART, trip, stop, check.at)
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

    /**
     * 연속 운전이 1시간 45분, 2시간이 되면 한 번씩 쉬라고 말한다 (TRIP-031). 운행이 없어도 잰다.
     * 15분 넘게 세워 쉬면 처음부터 다시 잰다
     */
    private fun tellWhenRestIsDue() {
        viewModelScope.launch {
            var told: RestLevel? = null
            while (true) {
                val check = restTimer.check(clock.instant(), sensors.motion.value)
                restCheck.value = check
                val level = check?.level
                if (level == null) {
                    told = null
                } else if (told == null || level > told) {
                    told = level
                    tellRest(level)
                }
                delay(REST_CHECK_MILLIS)
            }
        }
    }

    /**
     * 휴게 안내 (TRIP-031, 032): 지금 위치에서 경로 위 쉴 곳을 다시 받아 함께 말한다.
     * 8초 안에 못 받으면 받아 둔 것으로, 그것도 없으면 장소 없이 말한다. 말하는 때는 늦추지 않는다
     */
    private fun tellRest(level: RestLevel) {
        viewModelScope.launch {
            val fresh = withTimeoutOrNull(REST_STOPS_WAIT_MILLIS) { loadRouteStops() }
            val stops = (fresh ?: routeStops.value)?.current(navigator.progress.value?.remainMeters).orEmpty()
            announcer.say(HomeAnnouncements.rest(level, RestAdvisor.advise(level, stops)))
        }
    }

    /**
     * 운행 중인 차의 경로 위 쉴 곳을 받는다 (TRIP-032). 휴게 안내(1시간 45분, 2시간) 때만 부른다 (2026-11-13, TMAP 아끼기).
     * 쉴 곳 정보는 서버 DB 것이고, TMAP 경로 선은 내 길 위에 있는지 가리는 데만 쓴다.
     * 운행 중이 아니거나 위치를 모르거나 경로를 못 받으면 null
     */
    private suspend fun loadRouteStops(): RouteRestStopsAt? {
        val trip = snapshot.value.current?.takeIf { it.status.isDriving } ?: return null
        val here = sensors.location.value ?: return null
        val remain = navigator.progress.value?.remainMeters
        val result = (restAreaRepository.onRoute(trip.id, here) as? ApiResult.Success)?.data ?: return null
        if (!result.routed) return null
        return RouteRestStopsAt(trip.id, result.stops, result.attribution, clock.instant(), remain).also { routeStops.value = it }
    }

    /** 세워 두었을 때 휴게소를 내비 경유지로 넣는다 (TRIP-032). 달리는 동안에는 누르지 못한다 */
    override fun addRestStopWaypoint(stop: RestStop) {
        if (sensors.motion.value?.isParked != true) return
        val added = navigator.addWaypoint(
            Destination(name = stop.name, address = stop.direction?.let { "$it 방향" }.orEmpty(), latitude = stop.latitude, longitude = stop.longitude),
        )
        show(if (added) "${josa(stop.name, "을", "를")} 경유지로 넣었어요" else "경유지를 넣지 못했어요")
    }

    private fun fenceOf(stop: TripStop) = GeoFence(GeoPoint(stop.latitude, stop.longitude), stop.geofenceRadiusMeters)

    /** 내비가 주행 정보를 띄엄띄엄 보내서, 안내 중에만 10초마다 남은 거리·시간을 다시 묻는다 */
    /** 출발한 운행이 있는 동안 위치를 서버로 보낸다 (TRIP-013). 끝나거나 취소되면 멈춘다 */
    private fun reportLocationWhileOnTrip() {
        viewModelScope.launch {
            snapshot.map { it.current?.id }.distinctUntilChanged().collect(locationTracker::track)
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
        const val WAIT_QUEUED_MESSAGE = "통신이 끊겼어요. 연결되면 누른 시각으로 기록할게요"
        const val WAIT_SENT_MESSAGE = "통신이 끊긴 동안의 기록을 보냈어요"

        /** 쌓아 둔 대기 요청을 다시 보내 보는 간격 */
        private const val OUTBOX_RETRY_MILLIS = 15_000L
        const val NAVI_FAILED_MESSAGE = "차량 내비에 연결하지 못했습니다"
        const val NEXT_ORDER_REQUESTED_MESSAGE = "배차 요청을 보냈어요. 주선사가 확정하면 내 배차에 들어와요"
        const val RECEIPT_REMINDER_MESSAGE = "운행을 마쳤어요. 인수증 사진은 휴대폰 증빙에서 보내 주세요"
        private const val NAVI_REFRESH_MILLIS = 10_000L
        private const val SENSOR_CHECK_MILLIS = 1_000L
        private const val REST_CHECK_MILLIS = 10_000L
        private const val REST_STOPS_WAIT_MILLIS = 8_000L

        /** 상하차지 정보 카드를 읽어 주고 "대기 시작" 버튼을 보여 주는 거리 (SITE-006, WAIT-008) */
        private const val APPROACH_ANNOUNCE_METERS = 3_000.0

        /** 도착 확인 창이 기다리는 시간 (WAIT-007) */
        const val ARRIVAL_CONFIRM_SECONDS = 15

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
                    navigator = container.navigator,
                    sensors = container.vehicleSensors,
                    announcer = container.announcer,
                    locationTracker = container.locationTracker,
                    recommendationRepository = container.recommendationRepository,
                    restAreaRepository = container.restAreaRepository,
                    notifications = container.notifications.received,
                    recognizer = container.speechRecognizer,
                    answerClassifier = container.answerClassifier,
                    pendingWaits = container.pendingWaits,
                    tripDistance = container.tripDistance,
                )
            }
        }
    }
}

/** 도착 감지 상태와 "대기 시작" 버튼에 쓰는 값 */
private data class ArrivalView(
    val check: ArrivalCheck = ArrivalCheck.NotYet,
    /** 가는 상하차지 3km 안 */
    val nearStop: Boolean = false,
    /** 세워 두었다 (기어 P 또는 주차 브레이크) */
    val parked: Boolean = false,
)

/** 묻고 있는 도착·떠남. [at]은 감지한 때 */
private data class PendingQuestion(val kind: StopQuestionKind, val tripId: Long, val stop: StopType, val at: Instant)
