package com.ieo.app.di

import android.content.Context
import com.ieo.app.BuildConfig
import com.ieo.app.core.network.NetworkClients
import com.ieo.app.core.session.DeviceInfo
import com.ieo.app.core.session.EncryptedTokenStore
import com.ieo.app.core.session.KeystoreTokenCipher
import com.ieo.app.core.session.SessionManager
import com.ieo.app.data.account.AccountApi
import com.ieo.app.data.account.DefaultAccountRepository
import com.ieo.app.data.auth.AuthApi
import com.ieo.app.data.auth.DefaultAuthRepository
import com.ieo.app.data.auth.ServerTokenRefresher
import com.ieo.app.data.delivery.DefaultDeliveryProofRepository
import com.ieo.app.data.delivery.DeliveryProofApi
import com.ieo.app.data.expense.DefaultExpenseRepository
import com.ieo.app.data.expense.ExpenseApi
import com.ieo.app.data.ledger.DefaultLedgerRepository
import com.ieo.app.data.link.DefaultDriverLinkRepository
import com.ieo.app.data.link.DriverLinkApi
import com.ieo.app.data.ledger.LedgerApi
import com.ieo.app.data.navi.NaviGuide
import com.ieo.app.data.notification.PrefsLastNotificationStore
import com.ieo.app.data.notification.SseNotificationRepository
import com.ieo.app.data.order.DefaultOrderRepository
import com.ieo.app.data.order.OrderApi
import com.ieo.app.data.recommendation.DefaultRecommendationRepository
import com.ieo.app.data.recommendation.RecommendationApi
import com.ieo.app.data.site.DefaultSiteRepository
import com.ieo.app.data.site.SiteApi
import com.ieo.app.data.trip.DefaultTripRepository
import com.ieo.app.data.trip.ForegroundLocationTracker
import com.ieo.app.data.trip.TripApi
import com.ieo.app.data.vehicle.CarSensors
import com.ieo.app.data.vehicle.DefaultVehicleRepository
import com.ieo.app.data.vehicle.PleosCarIdentity
import com.ieo.app.data.vehicle.VehicleApi
import com.ieo.app.data.voice.PleosSpeechRecognizer
import com.ieo.app.data.voice.TtsAnnouncer
import com.ieo.app.data.wait.DefaultWaitRepository
import com.ieo.app.data.wait.WaitApi
import com.ieo.app.domain.account.AccountRepository
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.delivery.DeliveryProofRepository
import com.ieo.app.domain.expense.ExpenseRepository
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.link.DriverLinkRepository
import com.ieo.app.domain.navi.NaviPlaceSource
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.notification.NotificationListener
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.trip.LocationTracker
import com.ieo.app.domain.trip.TripLocationReporter
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.vehicle.CarIdentity
import com.ieo.app.domain.vehicle.VehicleRepository
import com.ieo.app.domain.vehicle.VehicleSensors
import com.ieo.app.domain.voice.Announcer
import com.ieo.app.domain.voice.SpeechRecognizer
import com.ieo.app.domain.wait.WaitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 하나씩만 있는 객체들. 화면(ViewModel)은 여기서 인터페이스를 받아 쓴다.
 * Hilt 같은 의존성 주입 도구 대신 손으로 연결한다. 객체가 늘면 Hilt로 옮긴다.
 */
interface AppContainer {
    val session: SessionManager
    val authRepository: AuthRepository
    val tripRepository: TripRepository
    val waitRepository: WaitRepository
    val ledgerRepository: LedgerRepository
    val orderRepository: OrderRepository
    val recommendationRepository: RecommendationRepository
    val expenseRepository: ExpenseRepository
    val siteRepository: SiteRepository
    val deliveryProofRepository: DeliveryProofRepository
    val accountRepository: AccountRepository
    val vehicleRepository: VehicleRepository
    val driverLinkRepository: DriverLinkRepository
    val navigator: Navigator
    val naviPlaces: NaviPlaceSource
    val vehicleSensors: VehicleSensors
    val carIdentity: CarIdentity
    val announcer: Announcer
    val speechRecognizer: SpeechRecognizer
    val locationTracker: LocationTracker
    val notifications: NotificationListener
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val session = SessionManager(EncryptedTokenStore(appContext, KeystoreTokenCipher()))

    private val device = DeviceInfo(appContext, appVersion = BuildConfig.VERSION_NAME)

    private val network = NetworkClients(
        baseUrl = BuildConfig.API_BASE_URL,
        session = session,
        enableLogging = BuildConfig.DEBUG,
        refresherFactory = { publicRetrofit -> ServerTokenRefresher(publicRetrofit.create(AuthApi::class.java)) },
    )

    override val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(network.public.create(AuthApi::class.java), network.caller, session, device)
    }

    override val tripRepository: TripRepository by lazy {
        DefaultTripRepository(network.authenticated.create(TripApi::class.java), network.caller)
    }

    override val waitRepository: WaitRepository by lazy {
        DefaultWaitRepository(
            api = network.authenticated.create(WaitApi::class.java),
            caller = network.caller,
            publicBaseUrl = BuildConfig.API_BASE_URL,
        )
    }

    override val ledgerRepository: LedgerRepository by lazy {
        DefaultLedgerRepository(network.authenticated.create(LedgerApi::class.java), network.caller)
    }

    override val orderRepository: OrderRepository by lazy {
        DefaultOrderRepository(network.authenticated.create(OrderApi::class.java), network.caller)
    }

    override val recommendationRepository: RecommendationRepository by lazy {
        DefaultRecommendationRepository(network.authenticated.create(RecommendationApi::class.java), network.caller)
    }

    override val expenseRepository: ExpenseRepository by lazy {
        DefaultExpenseRepository(network.authenticated.create(ExpenseApi::class.java), network.caller)
    }

    override val siteRepository: SiteRepository by lazy {
        DefaultSiteRepository(network.authenticated.create(SiteApi::class.java), network.caller)
    }

    override val deliveryProofRepository: DeliveryProofRepository by lazy {
        DefaultDeliveryProofRepository(
            api = network.authenticated.create(DeliveryProofApi::class.java),
            caller = network.caller,
            publicBaseUrl = BuildConfig.API_BASE_URL,
        )
    }

    override val accountRepository: AccountRepository by lazy {
        DefaultAccountRepository(network.authenticated.create(AccountApi::class.java), network.caller)
    }

    override val vehicleRepository: VehicleRepository by lazy {
        DefaultVehicleRepository(network.authenticated.create(VehicleApi::class.java), network.caller)
    }

    override val driverLinkRepository: DriverLinkRepository by lazy {
        DefaultDriverLinkRepository(network.authenticated.create(DriverLinkApi::class.java), network.caller)
    }

    // 내비 연결은 하나. 길 안내와 저장 장소를 같은 객체가 맡는다
    private val naviGuide by lazy { NaviGuide(appContext) }

    override val navigator: Navigator get() = naviGuide

    override val naviPlaces: NaviPlaceSource get() = naviGuide

    override val vehicleSensors: VehicleSensors by lazy { CarSensors(appContext) }

    override val carIdentity: CarIdentity by lazy { PleosCarIdentity(appContext) }

    override val announcer: Announcer by lazy { TtsAnnouncer(appContext) }

    override val speechRecognizer: SpeechRecognizer by lazy { PleosSpeechRecognizer(appContext) }

    /** 앱이 살아 있는 동안 도는 일. 화면이 바뀌거나 내비가 앞에 떠도 이어진다 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val locationTracker: LocationTracker by lazy {
        ForegroundLocationTracker(appContext, TripLocationReporter(appScope, vehicleSensors, navigator, tripRepository))
    }

    /** 로그인한 동안 서버 알림(SSE)을 받는다. 로그아웃하면 끊는다 */
    override val notifications: NotificationListener = NotificationListener(
        scope = appScope,
        repository = SseNotificationRepository(network.authenticatedClient, BuildConfig.API_BASE_URL, network.json),
        store = PrefsLastNotificationStore(appContext),
    ).also { listener ->
        appScope.launch {
            session.isLoggedIn.collect { loggedIn -> if (loggedIn) listener.start() else listener.stop() }
        }
    }
}
