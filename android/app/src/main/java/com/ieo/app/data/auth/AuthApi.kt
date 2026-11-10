package com.ieo.app.data.auth

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** 로그인 전 API (/api/auth/...). 토큰 없이 부른다 */
interface AuthApi {

    @POST("api/auth/phone/code")
    suspend fun requestCode(@Body body: SendCodeRequest): Response<SendCodeResponse>

    @POST("api/auth/phone/verify")
    suspend fun verifyCode(@Body body: VerifyCodeRequest): Response<PhoneVerifyResponse>

    @POST("api/auth/phone/login")
    suspend fun loginWithPhone(@Body body: PhoneLoginRequest): Response<TokenResponse>

    @POST("api/auth/car-login/sessions")
    suspend fun createCarLoginSession(@Body body: CreateCarLoginSessionRequest): Response<CarLoginSessionResponse>

    @POST("api/auth/car-login/sessions/{sessionId}/poll")
    suspend fun pollCarLogin(
        @Path("sessionId") sessionId: String,
        @Header(POLL_SECRET_HEADER) pollSecret: String,
    ): Response<CarLoginPollResponse>

    /** 토큰 갱신. OkHttp Authenticator 안에서 부르므로 동기 호출(Call)이다 */
    @POST("api/auth/refresh")
    fun refresh(@Body body: RefreshTokenRequest): Call<TokenResponse>

    companion object {
        const val POLL_SECRET_HEADER = "X-Poll-Secret"
    }
}
