package com.ieo.app.data.account

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface AccountApi {

    @GET("api/users/me")
    suspend fun me(): Response<UserResponse>

    @PATCH("api/users/me")
    suspend fun update(@Body body: UpdateUserRequest): Response<UserResponse>

    @GET("api/users/me/consents")
    suspend fun consents(): Response<List<ConsentResponse>>

    @POST("api/users/me/car-sessions/current/sign-out")
    suspend fun endCarSession(): Response<Unit>

    @GET("api/drivers/me/record")
    suspend fun record(): Response<DriverRecordResponse>

    @GET("api/drivers/me/documents")
    suspend fun documents(): Response<DocumentProgressResponse>
}

@Serializable
data class DriverRecordResponse(val verified: Boolean, val completedTripCount: Long = 0, val lateCancelCount: Long = 0)

/** 내 서류 응답 중 진행 정도만 */
@Serializable
data class DocumentProgressResponse(val verified: Boolean, val approvedRequired: Int = 0, val requiredTotal: Int = 4)

@Serializable
data class ConsentResponse(val type: String, val agreed: Boolean)
