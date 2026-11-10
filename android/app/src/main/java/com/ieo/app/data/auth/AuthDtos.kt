package com.ieo.app.data.auth

import com.ieo.app.core.session.AuthTokens
import com.ieo.app.domain.auth.CarLoginSession
import com.ieo.app.domain.auth.CarLoginStatus
import com.ieo.app.domain.auth.ExistingAccount
import com.ieo.app.domain.auth.PhoneVerification
import com.ieo.app.domain.auth.UserRole
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/user/dto와 같은 모양. 화면에는 toDomain()으로 바꿔서 넘긴다

@Serializable
data class SendCodeRequest(val phone: String)

/** 시연 서버는 문자를 보내지 않고 인증번호를 그대로 준다. 운영에서는 null */
@Serializable
data class SendCodeResponse(val demoCode: String? = null)

@Serializable
data class VerifyCodeRequest(val phone: String, val code: String, val deviceId: String)

@Serializable
data class PhoneVerifyResponse(
    val phoneToken: String,
    val accountExists: Boolean,
    val existingAccount: ExistingAccountResponse? = null,
) {
    fun toDomain() = PhoneVerification(
        phoneToken = phoneToken,
        existingAccount = existingAccount?.takeIf { accountExists }?.toDomain(),
    )
}

@Serializable
data class ExistingAccountResponse(val name: String, val role: String) {
    fun toDomain() = ExistingAccount(name = name, role = UserRole.valueOf(role))
}

@Serializable
data class PhoneLoginRequest(val phoneToken: String, val deviceId: String, val carDeviceName: String, val appVersion: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String) {
    fun toDomain() = AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
}

@Serializable
data class CreateCarLoginSessionRequest(val deviceId: String, val deviceName: String?, val appVersion: String?)

@Serializable
data class CarLoginSessionResponse(
    val sessionId: String,
    val code: String,
    val pollSecret: String,
    val qrUrl: String,
    val expiresAt: String,
    val pollIntervalSeconds: Int,
) {
    fun toDomain() = CarLoginSession(
        id = sessionId,
        code = code,
        qrContent = qrUrl,
        pollSecret = pollSecret,
        expiresAt = Instant.parse(expiresAt),
        pollInterval = Duration.ofSeconds(pollIntervalSeconds.toLong()),
    )
}

@Serializable
data class CarLoginPollResponse(val status: String, val tokens: TokenResponse? = null) {
    fun statusOrExpired(): CarLoginStatus =
        CarLoginStatus.entries.firstOrNull { it.name == status } ?: CarLoginStatus.EXPIRED
}
