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
import com.ieo.app.data.ledger.LedgerApi
import com.ieo.app.data.navi.NaviGuide
import com.ieo.app.data.order.DefaultOrderRepository
import com.ieo.app.data.order.OrderApi
import com.ieo.app.data.site.DefaultSiteRepository
import com.ieo.app.data.site.SiteApi
import com.ieo.app.data.trip.DefaultTripRepository
import com.ieo.app.data.trip.TripApi
import com.ieo.app.data.vehicle.DefaultVehicleRepository
import com.ieo.app.data.vehicle.VehicleApi
import com.ieo.app.data.wait.DefaultWaitRepository
import com.ieo.app.data.wait.WaitApi
import com.ieo.app.domain.account.AccountRepository
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.delivery.DeliveryProofRepository
import com.ieo.app.domain.expense.ExpenseRepository
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.vehicle.VehicleRepository
import com.ieo.app.domain.wait.WaitRepository

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
    val expenseRepository: ExpenseRepository
    val siteRepository: SiteRepository
    val deliveryProofRepository: DeliveryProofRepository
    val accountRepository: AccountRepository
    val vehicleRepository: VehicleRepository
    val navigator: Navigator
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

    override val navigator: Navigator by lazy { NaviGuide(appContext) }
}
