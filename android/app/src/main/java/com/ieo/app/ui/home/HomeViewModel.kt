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
import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.recommendation.NextOrderAdvisor
import com.ieo.app.domain.recommendation.NextOrderOffers
import com.ieo.app.domain.recommendation.NextOrderVoiceDialog
import com.ieo.app.domain.recommendation.VoiceAnswer
import com.ieo.app.domain.recommendation.RecommendationAnswer
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.trip.LocationTracker
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import com.ieo.app.domain.vehicle.VehicleSensors
import com.ieo.app.domain.voice.AnswerClassifier
import com.ieo.app.domain.voice.Announcer
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
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import com.ieo.app.ui.component.BannerKind
import com.ieo.app.ui.component.IeoBanner
import com.ieo.app.ui.component.NOTIFICATION_VISIBLE_MS
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

/** 화면 위에 잠깐 띄우는 것들 */
data class HomeStatus(
    /** 서버 요청 중. 버튼을 두 번 누르지 않게 한다 */
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 ([HomeViewModel.messageShown]) */
    val message: String? = null,
    /** 서버 알림 배너. 맨 앞 것부터 하나씩 보여 준다 ([HomeViewModel.bannerDone]) */
    val banners: List<IeoBanner> = emptyList(),
    /** 수락 창에서 묻는 말을 마친 후보. 앞선 안내(도착 등)에 밀려도 15초를 온전히 주려고 이때부터 시간을 잰다 */
    val offerAsked: Long? = null,
    /** 차주 대답을 듣는 중 (마이크가 열려 있다) */
    val listening: Boolean = false,
    /** 대기 증빙 링크 창. 닫으면 null ([HomeViewModel.proofShown]) */
    val proof: WaitProofLink? = null,
    /** 인수증 QR 창. 닫으면 null ([HomeViewModel.closeDeliveryProof]) */
    val deliveryProof: DeliveryProof? = null,
    /** 도착 확인 창 (WAIT-007). 대답하거나 시간이 지나면 null */
    val arrivalQuestion: ArrivalQuestion? = null,
)

/** 홈 화면이 ViewModel에 보내는 동작 */
interface HomeActions {
    /** 내 배차에서 "안내 시작": 출발 처리 후 상차지로 길 안내 */
    fun startTrip(tripId: String)

    /** "대기 시작": 확인 없이 지금부터 대기를 시작한다 (WAIT-008). 도착 확인 창이 떠 있으면 그 확인과 같다 */
    fun arrive()

    /** 도착 확인 창의 "도착 맞아요": 감지한 때부터 대기를 시작한다 (WAIT-007) */
    fun confirmArrival()

    /** 도착 확인 창의 "아직이에요": 창을 닫고, 같은 정차 중에는 다시 묻지 않는다 */
    fun declineArrival()

    /** "상차 시작" · "하차 시작" */
    fun startWork()

    /** 떠남. 상차지면 하차지로 길 안내, 하차지면 운행 완료 */
    fun depart()

    /** 내비 안내가 꺼졌을 때 다시 켜기 */
    fun resumeGuidance()

    /** 지금 상하차지의 대기 증빙 링크를 만들어 보여 준다 */
    fun sendWaitProof()
    fun sendProofToBroker()

    /** 하차지에서 "인수증 받기": QR을 보여 주고 수하인 서명을 기다린다 (POD-003) */
    fun requestDeliveryProof()

    /** 하차지 추천 카드에서 확인까지 마친 "수락": 그 오더에 배차 요청을 보낸다 (REC-022, 023) */
    fun acceptNextOrder(orderId: Long)

    /** 수락 창에서 "넘기기": 거절로 적는다. 3번이면 이 운행에서는 더 안내하지 않는다 (REC-024) */
    fun skipNextOrder(orderId: Long)

    /** 수락 창의 시간이 끝났다: 거절로 치지 않고 다음 후보로 넘어간다 */
    fun nextOrderTimedOut(orderId: Long)

    /** 추천 카드를 눌러 그 후보의 수락 창을 연다 */
    fun openNextOrder(orderId: Long)

    fun refresh()
}

/**
 * 홈 화면: 운행 진행(출발 → 상차지 도착 → 상차 → 출발 → 하차지 도착 → 하차 → 완료)을 서버에 알리고,
 * 그 결과로 화면 상태를 바꾼다. 화면을 고르는 규칙은 [HomeStateFactory]가 갖고 있다.
 *
 * 상하차지 도착: 반경 안에서 3분 세워 두거나(WAIT-001) 차량 내비가 "목적지 도착"을 알리면 음성과 창으로 묻고,
 * 차주가 맞다고 하면 감지한 때부터 대기를 시작한다 (WAIT-007). "대기 시작" 버튼은 묻지 않고 바로 시작한다 (WAIT-008).
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
    private val recommendationRepository: RecommendationRepository,
    /** 서버 알림 (09 알림). 앱 전체의 [com.ieo.app.domain.notification.NotificationListener]가 받는다 */
    private val notifications: Flow<AppNotification> = emptyFlow(),
    private val recognizer: SpeechRecognizer = NoSpeechRecognizer,
    /** 단어 목록으로 못 알아들은 예/아니오 대답을 서버에서 판별한다 (UX-025) */
    private val answerClassifier: AnswerClassifier = NoAnswerClassifier,
    private val clock: Clock = Clock.systemUTC(),
    private val stateFactory: HomeStateFactory = HomeStateFactory(),
) : ViewModel(), HomeActions {

    private val snapshot = MutableStateFlow(HomeSnapshot())

    /** 상하차지 반경 안에서 멈춘 동안 남은 시간, 상하차지 3km 안인지, 세워 두었는지를 화면에 보여 준다 */
    private val arrivalView = MutableStateFlow(ArrivalView())
    private val arrivalDetector = ArrivalDetector()

    /** 묻고 있는 도착. 대답하면 null */
    private var pendingArrival: PendingArrival? = null
    private var arrivalJob: Job? = null

    /** 이미 물은 정차에서는 다시 묻지 않는다. 차가 움직이거나 반경을 벗어나면 풀린다 */
    private var holdUntilMoved = false

    /** 내비 도착 알림으로 물은 정차 지점. 운행·지점마다 한 번 */
    private val naviArrivalAsked = mutableSetOf<Pair<Long, StopType>>()

    /** 상하차지를 떠나는 동안 남은 시간을 화면에 보여 준다 */
    private val departureCheck = MutableStateFlow<DepartureCheck>(DepartureCheck.Staying)
    private val departureDetector = DepartureDetector()

    /** 운송 중 다음 배차 추천: 50%에서 찾고 67%에서 음성으로 안내한다 (REC-001) */
    private val nextOrderAdvisor = NextOrderAdvisor(recommendationRepository) { trip, next ->
        announcedOffers += next.order.id
        talkWhileDriving(trip, next)
    }

    val state: StateFlow<HomeState> =
        combine(snapshot, navigator.progress, arrivalView, departureCheck, nextOrderAdvisor.current) { snapshot, navi, arrival, departure, next ->
            stateFactory.create(snapshot, navi, arrival.check, departure, next, arrival.nearStop, arrival.parked)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateFactory.create(HomeSnapshot(), null))

    private val _status = MutableStateFlow(HomeStatus())
    val status: StateFlow<HomeStatus> = _status.asStateFlow()

    /** 수락 창에 띄울 차례인 후보 */
    private val offers = NextOrderOffers()
    private val announcedOffers = mutableSetOf<Long>()

    /** 다음 배차를 두고 차주와 말을 주고받는 일. 한 번에 하나 */
    private var voiceJob: Job? = null

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
    private val voiceDialog = NextOrderVoiceDialog(
        recognizer = listeningRecognizer,
        speak = { announcer.speak(HomeAnnouncements.nextOrderLine(it)) },
    )
    private val arrivalDialog = YesNoDialog(
        recognizer = listeningRecognizer,
        classifier = answerClassifier,
        speak = { announcer.speak(HomeAnnouncements.arrivalLine(it)) },
    )

    /** 지금 띄울 수락 창. 정차 중이고 QR 창이 닫혀 있을 때만 있고, 차가 움직이면 바로 null이 된다. 그 후보는 다음에 멈추면 다시 나온다 */
    val nextOffer: StateFlow<NextOrderCard?> =
        combine(
            offers.current,
            snapshot.map { it.current?.status }.distinctUntilChanged(),
            sensors.motion,
            // 수하인이 QR로 서명하는 동안, 도착을 묻는 동안에는 창도 음성도 끼어들지 않는다
            _status.map { it.proof != null || it.deliveryProof != null || it.arrivalQuestion != null }.distinctUntilChanged(),
        ) { next, status, motion, sheetOpen ->
            next?.takeUnless { sheetOpen }?.takeIf { canOffer(status, motion) }?.let(stateFactory::nextOrderCard)
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            load()
            resumeGuidanceAfterRestart()
        }
        askWhenNaviArrives()
        askWhenStoppedAtStop()
        departWhenLeavingStop()
        announceChargeableStart()
        keepNaviProgressFresh()
        keepNearbyOrdersFresh()
        reportLocationWhileOnTrip()
        watchEtaShared()
        recommendNextOrder()
        recommendAtDropoff()
        keepOffersFresh()
        tellNewOffers()
        tellNotifications()
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
        if (pendingArrival != null) return confirmArrival()
        arriveAt(at = null)
    }

    override fun confirmArrival() {
        arrivalJob?.cancel()
        answerArrival(yes = true)
    }

    override fun declineArrival() {
        arrivalJob?.cancel()
        answerArrival(yes = false)
    }

    /**
     * 도착을 묻는다 (WAIT-007): 창을 띄우고 말한 뒤 15초 동안 대답을 듣는다.
     * 단어 목록으로 못 알아들은 말은 서버에서 판별한다 (UX-025). 대답이 없으면 창을 닫는다
     */
    private fun askArrival(trip: Trip, stop: StopType, at: Instant) {
        if (pendingArrival != null) return
        pendingArrival = PendingArrival(trip.id, stop, at)
        val name = trip.stop(stop).name
        _status.update { it.copy(arrivalQuestion = ArrivalQuestion(name, stateFactory.time(at))) }
        // 다음 배차를 말하던 중이면 멈추고 도착부터 묻는다
        voiceJob?.cancel()
        arrivalJob = viewModelScope.launch {
            val question = HomeAnnouncements.arrivalQuestion(name, at)
            announcer.speak(question)
            _status.update { status -> status.copy(arrivalQuestion = status.arrivalQuestion?.copy(asked = true)) }
            when (arrivalDialog.listen(question.text, ARRIVAL_CONFIRM_SECONDS * 1_000L)) {
                YesNo.YES -> answerArrival(yes = true)
                YesNo.NO -> {
                    announcer.say(HomeAnnouncements.arrivalDeclined())
                    answerArrival(yes = false)
                }
                else -> answerArrival(yes = false)
            }
        }
    }

    /** 창을 닫고, 맞다고 했으면 감지한 때부터 대기를 시작한다 */
    private fun answerArrival(yes: Boolean) {
        val pending = pendingArrival ?: return
        pendingArrival = null
        announcer.cancel(HomeAnnouncements.ARRIVAL_KEY)
        _status.update { it.copy(arrivalQuestion = null) }
        if (yes) arriveAt(pending.at)
    }

    /** [at]: 도착 확인이면 감지한 때. "대기 시작" 버튼이면 null(서버가 받은 때) */
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

    override fun sendProofToBroker() = runAction {
        val (trip, stop) = currentAtStop() ?: return@runAction
        val link = waitRepository.sendProof(trip.id, stop).orShow() ?: return@runAction
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

    override fun acceptNextOrder(orderId: Long) = runAction {
        val trip = offerTrip() ?: return@runAction
        // 성공이어도 돌려받는 배차 요청은 null일 수 있다. 실패만 막는다
        val result = recommendationRepository.answer(trip.id, orderId, RecommendationAnswer.ACCEPTED)
        if (result is ApiResult.Failure) {
            // 그 사이 마감된 오더일 수 있다. 다음 후보로 넘어간다
            show(result.error.message)
            offers.finish(orderId)
            reloadOffers()
            return@runAction
        }
        // 이 순서여야 한다: 카드를 먼저 지우면 그 사이 다음 후보가 잠깐 잡혀 안내까지 나간다
        offers.accepted(orderId)
        snapshot.update { it.copy(nextOrders = it.nextOrders.filterNot { next -> next.order.id == orderId }) }
        show(NEXT_ORDER_REQUESTED_MESSAGE)
    }

    override fun skipNextOrder(orderId: Long) = runAction {
        val trip = offerTrip() ?: return@runAction
        // 성공이어도 돌려받는 값은 null이다. 실패만 막는다
        val result = recommendationRepository.answer(trip.id, orderId, RecommendationAnswer.REJECTED)
        if (result is ApiResult.Failure) {
            show(result.error.message)
            return@runAction
        }
        offers.finish(orderId)
        reloadOffers()
    }

    override fun nextOrderTimedOut(orderId: Long) {
        offers.finish(orderId)
        if (offers.current.value == null) viewModelScope.launch { reloadOffers() }
    }

    override fun openNextOrder(orderId: Long) = offers.open(orderId)

    private fun offerTrip() = snapshot.value.current?.takeIf { it.status in OFFER_STATUSES }

    /** 후보를 다시 받는다. 거절이 빠지고, 운송 중이면 아직 안내하지 않은 다음 후보가 온다. 3번 거절이면 비어서 온다 (REC-024) */
    private suspend fun reloadOffers() {
        val trip = offerTrip() ?: return
        val items = (recommendationRepository.recommend(trip.id) as? ApiResult.Success)?.data?.items ?: return
        snapshot.update { it.copy(nextOrders = items) }
        // 바로 이어서 다음 후보를 말할 수 있게 지켜보는 쪽을 기다리지 않고 올린다
        publishOffers(trip, items, nextOrderAdvisor.current.value)
    }

    /** 하차지에서는 받아 둔 후보를, 운송 중에는 안내한 1등과 이어 받은 후보를 수락 창 차례에 올린다 */
    private fun keepOffersFresh() {
        viewModelScope.launch {
            combine(snapshot.map { it.current to it.nextOrders }.distinctUntilChanged(), nextOrderAdvisor.current) { (trip, items), first ->
                Triple(trip, items, first)
            }.collect { (trip, items, first) -> publishOffers(trip, items, first) }
        }
    }

    private fun publishOffers(trip: Trip?, items: List<NextOrder>, first: NextOrder?) = when (trip?.status) {
        TripStatus.AT_DROPOFF -> offers.update(trip.id, items)
        TripStatus.IN_TRANSIT -> offers.update(trip.id, (listOfNotNull(first) + items).distinctBy { it.order.id })
        else -> offers.update(null, emptyList())
    }

    /**
     * 수락 창이 처음 뜨면 짧게 묻고 서버에 적은 뒤 대답을 듣는다. 운송 중 67%에 이미 말한 후보는 다시 말하지 않고 듣기만 한다.
     * 창이 닫히면(수락, 넘기기, 차가 움직임) 말하던 것을 멈추고 마이크를 닫는다
     */
    private fun tellNewOffers() {
        viewModelScope.launch {
            nextOffer.map { it?.orderId }.distinctUntilChanged().collect { orderId ->
                voiceJob?.cancel()
                if (orderId == null) {
                    announcer.cancel(HomeAnnouncements.NEXT_ORDER_KEY)
                    return@collect
                }
                val trip = offerTrip() ?: return@collect
                val next = offers.current.value?.takeIf { it.order.id == orderId } ?: return@collect
                val firstTime = announcedOffers.add(orderId)
                if (firstTime) recommendationRepository.answer(trip.id, orderId, answer = null)
                voiceJob = launch {
                    val question = HomeAnnouncements.nextOrderOffer(next)
                    if (firstTime) announcer.speak(question)
                    _status.update { it.copy(offerAsked = orderId) }
                    when (ask(next, question.text, confirmed = true)) {
                        VoiceAnswer.ACCEPT -> acceptNextOrder(orderId)
                        VoiceAnswer.NEXT -> skipNextOrder(orderId)
                        VoiceAnswer.NONE -> Unit
                    }
                }
            }
        }
    }

    /**
     * 운송 중 안내를 말하고 대답을 듣는다 (REC-020). 손을 쓰지 않아도 되는 유일한 경로다.
     * 차를 세우면 수락 창 쪽이 이어받는다([tellNewOffers]). "다음"이면 다음 후보를 이어서 말한다
     */
    private fun talkWhileDriving(trip: Trip, first: NextOrder) {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            var next: NextOrder? = first
            while (next != null) {
                val text = HomeAnnouncements.nextOrder(trip.dropoff.name, next, stateFactory.pickupTime(next))
                announcer.speak(text)
                when (ask(next, text.text, confirmed = false)) {
                    VoiceAnswer.ACCEPT -> {
                        acceptNextOrder(next.order.id)
                        return@launch
                    }
                    VoiceAnswer.NONE -> return@launch
                    VoiceAnswer.NEXT -> {
                        recommendationRepository.answer(trip.id, next.order.id, RecommendationAnswer.NEXT)
                        offers.finish(next.order.id)
                        reloadOffers()
                        next = offers.current.value
                        next?.let { announcedOffers += it.order.id }
                    }
                }
            }
        }
    }

    private suspend fun ask(next: NextOrder, repeatText: String, confirmed: Boolean): VoiceAnswer = voiceDialog.ask(
        repeatText = repeatText,
        detailText = HomeAnnouncements.nextOrderDetail(next, stateFactory.nextOrderCard(next).pickupDay, stateFactory.pickupTime(next)).text,
        confirmText = HomeAnnouncements.nextOrderConfirm(next).text,
        confirmed = confirmed,
        // 수락 창은 시간이 다 될 때까지 듣는다. 창이 닫히면 voiceJob이 취소되어 듣기도 멈춘다
        listenMillis = if (confirmed) OFFER_SECONDS * 1_000L else NextOrderVoiceDialog.LISTEN_MILLIS,
    )

    fun messageShown() = _status.update { it.copy(message = null) }

    private fun bannerOf(notification: AppNotification) = IeoBanner(
        id = notification.id,
        text = notification.message,
        kind = if (notification.type == NotificationType.DISPATCH_CONFIRMED) BannerKind.GOOD else BannerKind.INFO,
        visibleMs = NOTIFICATION_VISIBLE_MS,
    )

    /** 배너를 다 보여 줬거나 닫았다. 다음 배너가 있으면 이어서 나온다 */
    fun bannerDone(id: Long) = _status.update { status -> status.copy(banners = status.banners.filterNot { it.id == id }) }

    /** 배차 확정·마감·만료 알림을 말하고 배너로 띄운다. 확정이면 내 배차가 늘었으니 홈을 새로 받는다 */
    private fun tellNotifications() {
        viewModelScope.launch {
            notifications.collect { notification ->
                announcer.say(HomeAnnouncements.notification(notification))
                _status.update { it.copy(banners = it.banners + bannerOf(notification)) }
                val confirmed = notification.type == NotificationType.DISPATCH_CONFIRMED
                if (confirmed) load()
                // 수락한 요청이 마감·만료됐으면 다음 후보로 이어 간다 (REC-023)
                if (offers.answered(notification.orderId, confirmed)) reloadOffers()
            }
        }
    }

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
                if (naviArrivalAsked.add(trip.id to stop)) askArrival(trip, stop, clock.instant())
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
                    if (pendingArrival != null) declineArrival()
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
                val check = if (holdUntilMoved || pendingArrival != null) {
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
                    askArrival(trip, stop, check.at)
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

    /**
     * 하차지에 도착하면 다음 배차 추천 5건을 받아 카드로 보여 준다 (REC-021). 운행·도착마다 한 번.
     * 하차지를 떠나거나 운행이 끝나면 비운다. 못 받으면 카드 없이 대기 화면만 보여 준다
     */
    private fun recommendAtDropoff() {
        viewModelScope.launch {
            snapshot.map { trip -> trip.current?.takeIf { it.status == TripStatus.AT_DROPOFF }?.id }
                .distinctUntilChanged()
                .collectLatest { tripId ->
                    val items = tripId?.let { (recommendationRepository.recommend(it) as? ApiResult.Success)?.data?.items }
                    snapshot.update { it.copy(nextOrders = items.orEmpty()) }
                }
        }
    }

    /** 서버가 주선사에게 도착 시각을 알렸는지 운송 중에 다시 확인한다. 알린 뒤에는 묻지 않는다 */
    private fun watchEtaShared() {
        viewModelScope.launch {
            snapshot.map { trip -> trip.current?.takeIf { it.status == TripStatus.IN_TRANSIT && !it.etaShared }?.id }
                .distinctUntilChanged()
                .collectLatest { tripId ->
                    while (tripId != null) {
                        delay(ETA_SHARED_CHECK_MILLIS)
                        val latest = (tripRepository.currentTrip() as? ApiResult.Success)?.data
                        if (latest?.id == tripId) snapshot.update { it.copy(current = latest) }
                    }
                }
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
        const val NEXT_ORDER_REQUESTED_MESSAGE = "배차 요청을 보냈어요. 주선사가 확정하면 내 배차에 들어와요"
        const val DELIVERY_SIGNED_MESSAGE = "인수증 서명을 받았어요. 운행을 마쳤어요"
        private const val SIGNATURE_POLL_MILLIS = 3_000L
        private const val NAVI_REFRESH_MILLIS = 10_000L
        private const val ETA_SHARED_CHECK_MILLIS = 60_000L
        private const val SENSOR_CHECK_MILLIS = 1_000L

        /** 상하차지 정보 카드를 읽어 주고 "대기 시작" 버튼을 보여 주는 거리 (SITE-006, WAIT-008) */
        private const val APPROACH_ANNOUNCE_METERS = 3_000.0

        /** 도착 확인 창이 기다리는 시간 (WAIT-007) */
        const val ARRIVAL_CONFIRM_SECONDS = 15

        /** 근처 오더를 찾은 곳에서 이만큼 움직이면 다시 찾는다 */
        private const val NEARBY_REFRESH_METERS = 5_000.0

        /** 수락 창을 띄울 수 있는 운행 상태. 추천을 받는 상태와 같다 */
        private val OFFER_STATUSES = setOf(TripStatus.IN_TRANSIT, TripStatus.AT_DROPOFF)

        /**
         * 수락 창을 띄워도 되는 정차인가. 운전 중에 터치를 요구하지 않는다 (REC-026, 10 음성·안전 UX).
         * 하차지든 운송 중이든 세워 두었을 때(기어 P 또는 주차 브레이크)만. 신호 대기·발 브레이크는 언제 출발할지 몰라 정차로 보지 않는다.
         * 차량 신호를 모르면 띄우지 않는다
         */
        fun canOffer(status: TripStatus?, motion: VehicleMotion?): Boolean =
            status in OFFER_STATUSES && motion?.isParked == true

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
                    notifications = container.notifications.received,
                    recognizer = container.speechRecognizer,
                    answerClassifier = container.answerClassifier,
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

/** 묻고 있는 도착. [at]은 감지한 때 */
private data class PendingArrival(val tripId: Long, val stop: StopType, val at: Instant)
