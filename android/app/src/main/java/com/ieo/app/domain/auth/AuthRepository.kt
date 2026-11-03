package com.ieo.app.domain.auth

import com.ieo.app.core.network.ApiResult

/**
 * 로그인. 화면은 이 인터페이스만 알고, 서버와 어떻게 통신하는지는 모른다.
 * 로그인에 성공하면 구현체가 세션에 토큰을 저장한다. 화면은 토큰을 직접 만지지 않는다.
 * 설계: docs/design/01_로그인.md
 */
interface AuthRepository {

    /** 인증번호 문자 보내기 */
    suspend fun requestCode(phone: String): ApiResult<Unit>

    /** 인증번호 확인. 가입된 계정이 있는지도 함께 알려 준다 */
    suspend fun verifyCode(phone: String, code: String): ApiResult<PhoneVerification>

    /** [verifyCode]에서 받은 phoneToken으로 로그인 */
    suspend fun loginWithPhone(phoneToken: String): ApiResult<Unit>

    /** 차량 QR 로그인 시작: 화면에 띄울 코드를 받는다 */
    suspend fun startCarLogin(): ApiResult<CarLoginSession>

    /** 휴대폰에서 승인했는지 확인. 승인됐으면 이때 로그인된다 */
    suspend fun pollCarLogin(session: CarLoginSession): ApiResult<CarLoginStatus>

    fun logout()
}
