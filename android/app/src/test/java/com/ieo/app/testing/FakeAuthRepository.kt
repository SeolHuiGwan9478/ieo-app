package com.ieo.app.testing

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.auth.CarLoginSession
import com.ieo.app.domain.auth.CarLoginStatus
import com.ieo.app.domain.auth.ExistingAccount
import com.ieo.app.domain.auth.PhoneVerification
import com.ieo.app.domain.auth.UserRole
import java.time.Duration
import java.time.Instant

/** 서버 없이 ViewModel을 시험하려고 결과를 미리 정해 두는 저장소 */
class FakeAuthRepository : AuthRepository {

    var requestCodeResult: ApiResult<String?> = ApiResult.Success("381942")
    var verifyResult: ApiResult<PhoneVerification> =
        ApiResult.Success(PhoneVerification("phone-token", ExistingAccount("홍길동", UserRole.DRIVER)))
    var loginResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var startCarLoginResult: ApiResult<CarLoginSession> = ApiResult.Success(carSession())
    /** 차례대로 돌려준다. 다 쓰면 마지막 값을 계속 준다 */
    val pollResults = ArrayDeque<ApiResult<CarLoginStatus>>()

    var loggedInWith: String? = null
    var startCarLoginCount = 0
    var pollCount = 0

    override suspend fun requestCode(phone: String) = requestCodeResult

    override suspend fun verifyCode(phone: String, code: String) = verifyResult

    override suspend fun loginWithPhone(phoneToken: String): ApiResult<Unit> {
        loggedInWith = phoneToken
        return loginResult
    }

    override suspend fun startCarLogin(): ApiResult<CarLoginSession> {
        startCarLoginCount++
        return startCarLoginResult
    }

    override suspend fun pollCarLogin(session: CarLoginSession): ApiResult<CarLoginStatus> {
        pollCount++
        return if (pollResults.size > 1) pollResults.removeFirst() else pollResults.firstOrNull() ?: ApiResult.Success(CarLoginStatus.PENDING)
    }

    override fun logout() = Unit

    companion object {
        val NOW: Instant = Instant.parse("2026-11-03T06:00:00Z")

        fun carSession() = CarLoginSession(
            id = "session-1",
            code = "KX7P-M4QZ",
            qrContent = "ieo://car-login?code=KX7P-M4QZ",
            pollSecret = "secret",
            expiresAt = NOW.plusSeconds(300),
            pollInterval = Duration.ofSeconds(2),
        )
    }
}
