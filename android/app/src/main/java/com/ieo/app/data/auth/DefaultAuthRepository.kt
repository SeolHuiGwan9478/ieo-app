package com.ieo.app.data.auth

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.map
import com.ieo.app.core.network.mapResponse
import com.ieo.app.core.network.onSuccess
import com.ieo.app.core.session.DeviceInfo
import com.ieo.app.core.session.SessionManager
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.auth.CarLoginSession
import com.ieo.app.domain.auth.CarLoginStatus
import com.ieo.app.domain.auth.PhoneVerification

class DefaultAuthRepository(
    private val api: AuthApi,
    private val caller: ApiCaller,
    private val session: SessionManager,
    private val device: DeviceInfo,
) : AuthRepository {

    override suspend fun requestCode(phone: String): ApiResult<String?> =
        caller.call { api.requestCode(SendCodeRequest(phone)) }.mapResponse { it.demoCode }

    override suspend fun verifyCode(phone: String, code: String): ApiResult<PhoneVerification> =
        caller.call { api.verifyCode(VerifyCodeRequest(phone, code, device.deviceId)) }.mapResponse { it.toDomain() }

    override suspend fun loginWithPhone(phoneToken: String): ApiResult<Unit> =
        caller.call { api.loginWithPhone(PhoneLoginRequest(phoneToken, device.deviceId, device.deviceName, device.appVersion)) }
            .onSuccess { session.signIn(it.toDomain()) }
            .map { }

    override suspend fun startCarLogin(): ApiResult<CarLoginSession> =
        caller.call {
            api.createCarLoginSession(CreateCarLoginSessionRequest(device.deviceId, device.deviceName, device.appVersion))
        }.mapResponse { it.toDomain() }

    override suspend fun pollCarLogin(session: CarLoginSession): ApiResult<CarLoginStatus> =
        when (val result = caller.call { api.pollCarLogin(session.id, session.pollSecret) }) {
            is ApiResult.Success -> ApiResult.Success(signInIfApproved(result.data))
            // pollSecret이 틀렸거나 세션이 지워졌으면 404. 새 코드를 받게 만료로 본다
            is ApiResult.Failure -> if ((result.error as? ApiError.Server)?.status == 404) {
                ApiResult.Success(CarLoginStatus.EXPIRED)
            } else {
                result
            }
        }

    override fun logout() = session.signOut()

    private fun signInIfApproved(response: CarLoginPollResponse): CarLoginStatus {
        val status = response.statusOrExpired()
        val tokens = response.tokens
        if (status == CarLoginStatus.APPROVED && tokens != null) {
            this.session.signIn(tokens.toDomain())
        }
        return status
    }
}
