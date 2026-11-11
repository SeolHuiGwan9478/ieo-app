package com.ieo.app.data.wait

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** stopType은 PICKUP 또는 DROPOFF */
interface WaitApi {

    @GET("api/trips/{tripId}/waits")
    suspend fun waits(@Path("tripId") tripId: Long): Response<List<WaitRecordResponse>>

    /** 처음이면 201, 이미 도착해 있었으면 200 */
    @POST("api/trips/{tripId}/stops/{stopType}/wait/arrive")
    suspend fun arrive(
        @Path("tripId") tripId: Long,
        @Path("stopType") stopType: String,
        @Body body: WaitTimeRequest = WaitTimeRequest(),
    ): Response<WaitRecordResponse>

    @POST("api/trips/{tripId}/stops/{stopType}/wait/work-start")
    suspend fun startWork(
        @Path("tripId") tripId: Long,
        @Path("stopType") stopType: String,
        @Body body: WaitTimeRequest = WaitTimeRequest(),
    ): Response<WaitRecordResponse>

    @POST("api/trips/{tripId}/stops/{stopType}/wait/depart")
    suspend fun depart(
        @Path("tripId") tripId: Long,
        @Path("stopType") stopType: String,
        @Body body: WaitTimeRequest = WaitTimeRequest(),
    ): Response<WaitRecordResponse>

    /** 운행의 대기 증빙 링크 (상차지·하차지 함께). 이미 만든 링크가 있으면 같은 링크 */
    @POST("api/trips/{tripId}/wait-proof")
    suspend fun createProof(@Path("tripId") tripId: Long): Response<WaitProofLinkResponse>

    /** 이미 보냈으면 다시 보내지 않는다 */
    @POST("api/trips/{tripId}/wait-proof/send")
    suspend fun sendProof(@Path("tripId") tripId: Long): Response<WaitProofLinkResponse>
}
