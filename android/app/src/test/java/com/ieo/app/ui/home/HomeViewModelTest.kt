package com.ieo.app.ui.home

import androidx.lifecycle.viewModelScope
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.voice.AnnouncementPriority
import com.ieo.app.domain.voice.AnswerClassifier
import com.ieo.app.domain.voice.SpeechRecognizer
import com.ieo.app.domain.voice.YesNo
import com.ieo.app.domain.wait.WaitMark
import com.ieo.app.domain.wait.WaitSource
import com.ieo.app.testing.FakeAnnouncer
import com.ieo.app.ui.component.BannerKind
import com.ieo.app.ui.component.IeoBanner
import com.ieo.app.ui.component.NOTIFICATION_VISIBLE_MS
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val notifications = MutableSharedFlow<AppNotification>()
    /** 차주가 할 말. null은 침묵. 대본이 떨어지면 아무 말도 안 한다 */
    private val heard = ArrayDeque<String?>()
    private val recognizer = object : SpeechRecognizer {
        override suspend fun listen(timeoutMillis: Long) = heard.removeFirstOrNull()
    }
    /** 서버 대답 판별. 물어본 대답을 모은다 */
    private val classified = mutableListOf<String>()
    private var serverMeaning: YesNo = YesNo.UNKNOWN
    private val classifier = object : AnswerClassifier {
        override suspend fun classify(question: String, answer: String): YesNo {
            classified += answer
            return serverMeaning
        }
    }
    private val dropoff = GeoPoint(TripFixtures.DROPOFF.latitude, TripFixtures.DROPOFF.longitude)
    private val nearPickup = GeoPoint(TripFixtures.PICKUP.latitude + 0.001, TripFixtures.PICKUP.longitude)

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
            notifications = notifications,
            recognizer = recognizer,
            answerClassifier = classifier,
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
    fun `달리던 운행은 차가 움직일 때 길 안내를 이어 간다`() = homeTest(prepare = { server.trip = server.trip.copy(status = TripStatus.TO_PICKUP) }) {
        assertTrue(navigator.guidedTo.isEmpty())

        sensors.drive(GeoPoint(36.0, 128.0))
        runCurrent()

        assertEquals(listOf("평택 포승"), navigator.guidedTo)
    }

    @Test
    fun `정차 중에는 앱을 켜도 내비를 앞으로 부르지 않는다`() = homeTest(prepare = { server.trip = server.trip.copy(status = TripStatus.TO_PICKUP) }) {
        sensors.parkAt(GeoPoint(36.0, 128.0))
        runCurrent()

        assertTrue(navigator.guidedTo.isEmpty())
    }

    @Test
    fun `내비에서 안내를 끝내면 켜진 동안에는 다시 켜지 않는다`() = homeTest(prepare = { server.trip = server.trip.copy(status = TripStatus.TO_PICKUP) }) {
        sensors.drive(GeoPoint(36.0, 128.0))
        runCurrent()
        navigator.stopGuidance()

        sensors.parkAt(GeoPoint(36.0, 128.0))
        runCurrent()
        sensors.drive(GeoPoint(36.0, 128.0))
        runCurrent()

        assertEquals(listOf("평택 포승"), navigator.guidedTo)
    }

    @Test
    fun `출발 전이면 길 안내를 시작하지 않는다`() = homeTest {
        assertTrue(navigator.guidedTo.isEmpty())
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
    fun `내비가 도착을 알리면 묻고 응이라고 하면 그때부터 대기를 시작한다`() = homeTest(prepare = { heard += "응" }) { vm ->
        vm.startTrip("1")
        runCurrent()

        navigator.arrive()
        runCurrent()

        assertTrue(announcer.texts.first().startsWith("평택 포승에 도착하셨어요? "))
        assertEquals(listOf("start", "arrive PICKUP"), server.calls)
        assertEquals(listOf<Instant?>(NOW), server.arrivedAt)
        assertNull(vm.status.value.stopQuestion)
        assertNull(navigator.progress.value)
        val state = vm.state.value as HomeState.Waiting
        assertTrue(state.isPickup)
    }

    @Test
    fun `상차지 반경 안에서 3분 세워 두면 묻고 확인하면 멈추기 시작한 시각으로 대기를 시작한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        // 상차지(평택 포승)에서 약 110m
        sensors.parkAt(nearPickup)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(180L, (vm.state.value as HomeState.Driving).arrivingInSeconds)

        clock.instant = NOW.plusSeconds(180)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(server.calls.isEmpty())
        val question = vm.status.value.stopQuestion!!
        assertEquals("평택 포승", question.stopName)
        assertTrue(question.asked)

        vm.confirmStopQuestion()
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
        assertEquals("대구 성서", navigator.guidedTo.last())
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
    fun `대기 중에 증빙을 보내면 그 운행의 링크 창을 띄운다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        vm.arrive()
        runCurrent()

        vm.sendWaitProof()
        runCurrent()

        assertEquals("http://localhost/api/public/wait-proofs/token-1", vm.status.value.proof?.url)
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
    fun `배차 확정 알림이 오면 말하고 화면에 띄우고 홈을 새로 받는다`() = homeTest { vm ->
        assertEquals(listOf<Long?>(null), tracker.tracked)
        server.trip = server.trip.copy(status = TripStatus.TO_PICKUP)

        val confirmed = AppNotification(7, NotificationType.DISPATCH_CONFIRMED, "한빛로지스에서 배차를 확정했어요", 1, 11, NOW)
        notifications.emit(confirmed)
        runCurrent()

        assertEquals(confirmed.message, announcer.texts.last())
        assertEquals(AnnouncementPriority.TRIP, announcer.said.last().priority)
        assertEquals(listOf(IeoBanner(7, confirmed.message, BannerKind.GOOD, NOTIFICATION_VISIBLE_MS)), vm.status.value.banners)
        assertNull(vm.status.value.message)
        assertEquals(listOf<Long?>(null, 1), tracker.tracked)

        notifications.emit(confirmed.copy(id = 8, type = NotificationType.DISPATCH_EXPIRED, message = "확정되지 않았어요"))
        runCurrent()
        assertEquals(AnnouncementPriority.RECOMMENDATION, announcer.said.last().priority)

        // 먼저 온 배너부터 하나씩 보여 준다
        assertEquals(listOf(7L, 8L), vm.status.value.banners.map { it.id })
        assertEquals(BannerKind.INFO, vm.status.value.banners.last().kind)
        vm.bannerDone(7)
        assertEquals(listOf(8L), vm.status.value.banners.map { it.id })
    }

    @Test
    fun `운행 완료를 누르고 완료하면 운행을 마치고 내 배차 목록으로 돌아간다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()

        vm.depart()
        assertTrue(vm.status.value.finishing)
        vm.finishTrip(makeProof = false)
        runCurrent()

        assertTrue(vm.state.value is HomeState.Idle)
        assertEquals(HomeViewModel.TRIP_COMPLETED_MESSAGE, vm.status.value.message)
    }

    @Test
    fun `상차지 반경을 벗어나면 묻고 확인하면 벗어난 시각으로 출발하고 하차지로 길 안내한다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
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
        // 떠남을 감지하면 묻는다. 확인해야 떠난 것으로 남긴다
        assertEquals("평택 포승에서 상차 끝내고 출발할까요?", vm.status.value.stopQuestion?.question)
        vm.confirmStopQuestion()
        runCurrent()

        assertEquals(listOf("arrive PICKUP", "depart PICKUP"), server.calls)
        assertEquals(listOf<Instant?>(NOW.plusSeconds(10)), server.departedAt)
        assertEquals("대구 성서", navigator.guidedTo.last())
        assertTrue(vm.state.value is HomeState.Driving)
    }

    @Test
    fun `하차지에서 시속 20km 이상으로 1분 달리면 묻고 확인하면 운행을 마친다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()
        sensors.drive(GeoPoint(TripFixtures.DROPOFF.latitude, TripFixtures.DROPOFF.longitude), speedKph = 30.0)
        advanceTimeBy(1_000)
        runCurrent()

        clock.instant = NOW.plusSeconds(60)
        advanceTimeBy(1_000)
        runCurrent()
        vm.confirmStopQuestion()
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
    fun `도착을 물었는데 대답이 없으면 창을 닫고 같은 정차 중에는 다시 묻지 않는다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        sensors.parkAt(nearPickup)
        advanceTimeBy(1_001)
        clock.instant = NOW.plusSeconds(180)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(vm.status.value.stopQuestion != null)

        // 음성 인식이 아무것도 못 듣고 일찍 끝나도 창은 15초 동안 남아 눌러서 대답할 수 있다
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(vm.status.value.stopQuestion != null)

        advanceTimeBy(HomeViewModel.ARRIVAL_CONFIRM_SECONDS * 1_000L + 1)
        runCurrent()
        assertNull(vm.status.value.stopQuestion)
        assertTrue(server.calls.isEmpty())

        // 그대로 10분 더 서 있어도 다시 묻지 않는다
        clock.instant = NOW.plusSeconds(780)
        advanceTimeBy(600_000)
        runCurrent()
        assertNull(vm.status.value.stopQuestion)
        assertEquals(1, announcer.texts.count { it.endsWith("대기로 기록할까요?") })

        // 움직였다가 다시 3분 세우면 다시 묻는다
        sensors.drive(nearPickup, speedKph = 5.0)
        advanceTimeBy(1_000)
        clock.instant = NOW.plusSeconds(800)
        sensors.parkAt(nearPickup)
        advanceTimeBy(1_000)
        clock.instant = NOW.plusSeconds(900)
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(vm.status.value.stopQuestion)
        clock.instant = NOW.plusSeconds(980)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(vm.status.value.stopQuestion != null)
    }

    @Test
    fun `아직이라고 하면 대기를 시작하지 않고 직접 시작하는 법을 알려 준다`() = homeTest(TripStatus.TO_PICKUP, prepare = { heard += "아직 줄 서 있어" }) { vm ->
        navigator.progress.value = NaviProgress("평택 포승", arrived = true)
        runCurrent()

        assertNull(vm.status.value.stopQuestion)
        assertTrue(server.calls.isEmpty())
        assertEquals("알겠어요. 도착하면 대기 시작을 눌러 주세요.", announcer.texts.last())
        // 단어 목록으로 알아들었으니 서버에 묻지 않는다
        assertTrue(classified.isEmpty())
    }

    @Test
    fun `단어 목록으로 못 알아들은 대답은 서버에 묻는다`() = homeTest(TripStatus.TO_PICKUP, prepare = {
        heard += "어어 들어왔지"
        serverMeaning = YesNo.YES
    }) { vm ->
        navigator.progress.value = NaviProgress("평택 포승", arrived = true)
        runCurrent()

        assertEquals(listOf("어어 들어왔지"), classified)
        assertEquals(listOf("arrive PICKUP"), server.calls)
    }

    @Test
    fun `대기 시작 버튼은 상하차지 3km 안에서 보이고 세워 두었을 때만 눌린다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        // 약 5.5km
        sensors.drive(GeoPoint(TripFixtures.PICKUP.latitude + 0.05, TripFixtures.PICKUP.longitude))
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(!(vm.state.value as HomeState.Driving).nearStop)

        // 약 2.2km, 달리는 중
        val near = GeoPoint(TripFixtures.PICKUP.latitude + 0.02, TripFixtures.PICKUP.longitude)
        sensors.drive(near)
        advanceTimeBy(1_000)
        runCurrent()
        var driving = vm.state.value as HomeState.Driving
        assertTrue(driving.nearStop && driving.showsStartWait && !driving.parked)

        sensors.parkAt(near)
        advanceTimeBy(1_000)
        runCurrent()
        driving = vm.state.value as HomeState.Driving
        assertTrue(driving.parked)

        // 반경 밖이어도 누르면 묻지 않고 바로 시작한다
        vm.arrive()
        runCurrent()
        assertEquals(listOf("arrive PICKUP"), server.calls)
    }

    @Test
    fun `운행 완료에서 대기 증빙을 고르면 운행이 끝난 뒤 그 운행의 링크를 보여 준다`() = homeTest(TripStatus.IN_TRANSIT) { vm ->
        vm.arrive()
        runCurrent()
        vm.depart()
        vm.finishTrip(makeProof = true)
        runCurrent()

        assertEquals(listOf("arrive DROPOFF", "depart DROPOFF", "proof 1"), server.calls)
        assertEquals("http://localhost/api/public/wait-proofs/token-1", vm.status.value.proof?.url)
        assertTrue(vm.state.value is HomeState.Idle)
    }

    @Test
    fun `떠남 확인으로 하차지를 끝내면 대기 증빙을 만들지 묻고 응이면 링크를 보여 준다`() = homeTest(TripStatus.IN_TRANSIT, prepare = { heard += "응"; heard += "응" }) { vm ->
        vm.arrive()
        sensors.parkAt(dropoff)
        advanceTimeBy(1_001)
        runCurrent()
        clock.instant = NOW.plusSeconds(10)
        sensors.drive(GeoPoint(TripFixtures.DROPOFF.latitude + 0.01, TripFixtures.DROPOFF.longitude), speedKph = 15.0)
        advanceTimeBy(1_000)
        clock.instant = NOW.plusSeconds(40)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("arrive DROPOFF", "depart DROPOFF", "proof 1"), server.calls)
        assertTrue(announcer.texts.contains("대기 증빙을 만들까요? 만들면 주선사에게 보낼 링크가 생겨요."))
        assertEquals("http://localhost/api/public/wait-proofs/token-1", vm.status.value.proof?.url)
        assertNull(vm.status.value.stopQuestion)
    }

    @Test
    fun `떠남 확인 뒤 증빙을 괜찮다고 하면 만들지 않는다`() = homeTest(TripStatus.IN_TRANSIT, prepare = { heard += "응"; heard += "아니" }) { vm ->
        vm.arrive()
        sensors.parkAt(dropoff)
        advanceTimeBy(1_001)
        runCurrent()
        clock.instant = NOW.plusSeconds(10)
        sensors.drive(GeoPoint(TripFixtures.DROPOFF.latitude + 0.01, TripFixtures.DROPOFF.longitude), speedKph = 15.0)
        advanceTimeBy(1_000)
        clock.instant = NOW.plusSeconds(40)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("arrive DROPOFF", "depart DROPOFF"), server.calls)
        assertEquals("알겠어요. 장부에서 언제든 만들 수 있어요.", announcer.texts.last())
        assertNull(vm.status.value.proof)
    }

    @Test
    fun `떠남을 아직이라고 하면 반경 안으로 돌아오기 전에는 다시 묻지 않는다`() = homeTest(TripStatus.IN_TRANSIT, prepare = { heard += "아직" }) { vm ->
        vm.arrive()
        sensors.parkAt(dropoff)
        advanceTimeBy(1_001)
        runCurrent()
        clock.instant = NOW.plusSeconds(10)
        sensors.drive(GeoPoint(TripFixtures.DROPOFF.latitude + 0.01, TripFixtures.DROPOFF.longitude), speedKph = 15.0)
        advanceTimeBy(1_000)
        clock.instant = NOW.plusSeconds(40)
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(vm.status.value.stopQuestion)
        assertEquals("알겠어요. 끝나면 운행 완료를 눌러 주세요.", announcer.texts.last())

        clock.instant = NOW.plusSeconds(400)
        advanceTimeBy(60_000)
        runCurrent()
        assertNull(vm.status.value.stopQuestion)
        assertEquals(listOf("arrive DROPOFF"), server.calls)
    }

    @Test
    fun `확인하거나 누른 순간의 위치와 방법을 같이 보낸다`() = homeTest(TripStatus.TO_PICKUP, prepare = { heard += "응" }) { vm ->
        sensors.parkAt(nearPickup)
        advanceTimeBy(1_001)
        clock.instant = NOW.plusSeconds(180)
        advanceTimeBy(1_000)
        runCurrent()
        vm.startWork()
        runCurrent()

        assertEquals(listOf("arrive PICKUP", "work PICKUP"), server.calls)
        assertEquals(WaitMark(nearPickup, WaitSource.CONFIRM_VOICE), server.marks[0])
        assertEquals(WaitMark(nearPickup, WaitSource.BUTTON), server.marks[1])
    }

    @Test
    fun `통신이 끊겨 대기를 못 보내면 쌓아 두었다가 누른 시각으로 다시 보낸다`() = homeTest(TripStatus.TO_PICKUP) { vm ->
        server.offline = true
        // 실패가 오기까지 13초가 걸려도 누른 때로 쌓아야 한다 (에뮬레이터에서 밀렸던 것)
        server.whileFailing = { clock.instant = NOW.plusSeconds(13) }
        vm.arrive()
        runCurrent()
        assertTrue(server.calls.isEmpty())
        assertEquals(HomeViewModel.WAIT_QUEUED_MESSAGE, vm.status.value.message)
        assertTrue(vm.state.value is HomeState.Driving)

        server.offline = false
        clock.instant = NOW.plusSeconds(120)
        advanceTimeBy(15_001)
        runCurrent()

        assertEquals(listOf("arrive PICKUP"), server.calls)
        // 누른 때(NOW)로 남는다
        assertEquals(listOf<Instant?>(NOW), server.arrivedAt)
        assertTrue(vm.state.value is HomeState.Waiting)
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
        vm.finishTrip(makeProof = false)
        runCurrent()
        assertEquals("운행을 마쳤어요. 장부에 45만 원을 기록했어요.", announcer.texts.last())
    }

    @Test
    fun `운행 전 화면에 오늘 요약과 내 배차를 서버에서 받아 보여 준다`() = homeTest { vm ->
        val state = vm.state.value as HomeState.Idle

        assertEquals(1, state.today.tripCount)
        assertEquals(1, state.myTrips.size)
        // 세워 두지 않았으면 추천은 없다
        assertTrue(state.picks.isEmpty())
    }

    private fun picks(vararg ids: Long) = ApiResult.Success(
        ids.map { NextOrder(TripFixtures.order(id = it), 17_000, 7_771, 372_229 - it, false) },
    )

    @Test
    fun `차를 세우면 세운 자리 근처 추천을 받아 보여 주고 움직이면 지운다`() = homeTest(prepare = { server.nextOrders = picks(11, 12, 13) }) { vm ->
        val here = GeoPoint(36.0, 128.0)
        sensors.drive(here)
        runCurrent()
        assertTrue(server.recommendRequests.isEmpty())

        sensors.parkAt(here)
        runCurrent()
        assertEquals(listOf(here), server.recommendRequests)
        assertEquals(listOf(11L, 12L, 13L), (vm.state.value as HomeState.Idle).picks.map { it.orderId })

        // 세워 둔 동안에는 다시 묻지 않는다
        sensors.parkAt(here)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, server.recommendRequests.size)

        sensors.drive(here)
        runCurrent()
        assertTrue((vm.state.value as HomeState.Idle).picks.isEmpty())
    }

    @Test
    fun `운행 중이어도 세우면 같은 방식으로 추천을 보여 준다`() = homeTest(TripStatus.IN_TRANSIT, prepare = { server.nextOrders = picks(11) }) { vm ->
        sensors.parkAt(GeoPoint(36.0, 128.0))
        runCurrent()

        assertEquals(listOf(11L), (vm.state.value as HomeState.Driving).picks.map { it.orderId })
    }

    @Test
    fun `세우면 가운데 창에 1등부터 띄우고 넘기거나 시간이 지나면 다음 추천, 마지막 뒤에는 닫는다`() = homeTest(prepare = { server.nextOrders = picks(11, 12, 13) }) { vm ->
        sensors.parkAt(GeoPoint(36.0, 128.0))
        runCurrent()
        assertEquals(11L, vm.status.value.pick?.orderId)

        vm.nextPick()
        assertEquals(12L, vm.status.value.pick?.orderId)
        vm.nextPick()
        assertEquals(13L, vm.status.value.pick?.orderId)
        vm.nextPick()
        assertNull(vm.status.value.pick)
        // 카드는 남아 있고, 눌러서 연 것은 넘기면 닫힌다
        assertEquals(3, (vm.state.value as HomeState.Idle).picks.size)
        vm.openPick(12)
        vm.nextPick()
        assertNull(vm.status.value.pick)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `추천을 눌러 배차를 요청하면 추천에서 눌렀다고 보내고 목록에서 뺀다`() = homeTest(prepare = { server.nextOrders = picks(11, 12) }) { vm ->
        sensors.parkAt(GeoPoint(36.0, 128.0))
        runCurrent()

        assertEquals(11L, vm.status.value.pick?.orderId)
        vm.requestPick()
        runCurrent()

        assertEquals(listOf(11L to true), server.requests)
        assertNull(vm.status.value.pick)
        assertEquals(listOf(12L), (vm.state.value as HomeState.Idle).picks.map { it.orderId })
        assertEquals(HomeViewModel.NEXT_ORDER_REQUESTED_MESSAGE, vm.status.value.message)
    }

    @Test
    fun `앱을 다시 켜면 진행 중인 운행의 대기 화면을 이어서 보여 준다`() = homeTest(
        status = TripStatus.TO_PICKUP,
        prepare = { server.arrive(1, StopType.PICKUP) },
    ) { vm ->
        assertTrue(vm.state.value is HomeState.Waiting)
    }
}
