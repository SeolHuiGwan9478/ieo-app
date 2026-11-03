package com.ieo.app.di

import android.content.Context
import com.ieo.app.BuildConfig
import com.ieo.app.core.network.NetworkClients
import com.ieo.app.core.session.DeviceInfo
import com.ieo.app.core.session.EncryptedTokenStore
import com.ieo.app.core.session.KeystoreTokenCipher
import com.ieo.app.core.session.SessionManager
import com.ieo.app.data.auth.AuthApi
import com.ieo.app.data.auth.DefaultAuthRepository
import com.ieo.app.data.auth.ServerTokenRefresher
import com.ieo.app.data.trip.DefaultTripRepository
import com.ieo.app.data.trip.TripApi
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.trip.TripRepository

/**
 * 앱 전체에서 하나씩만 있는 객체들. 화면(ViewModel)은 여기서 인터페이스를 받아 쓴다.
 * Hilt 같은 의존성 주입 도구 대신 손으로 연결한다. 객체가 늘면 Hilt로 옮긴다.
 */
interface AppContainer {
    val session: SessionManager
    val authRepository: AuthRepository
    val tripRepository: TripRepository
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
}
