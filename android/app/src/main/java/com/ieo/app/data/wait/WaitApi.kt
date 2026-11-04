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

    /** 이미 만든 링크가 있으면 같은 링크 */
    @POST("api/trips/{tripId}/stops/{stopType}/wait/proof")
    suspend fun createProof(
        @Path("tripId") tripId: Long,
        @Path("stopType") stopType: String,
    ): Response<WaitProofLinkResponse>
}
