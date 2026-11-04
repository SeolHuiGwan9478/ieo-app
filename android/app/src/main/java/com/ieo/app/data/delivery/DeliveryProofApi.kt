package com.ieo.app.data.delivery

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DeliveryProofApi {

    /** 처음이면 201, 이미 있으면 200 */
    @POST("api/trips/{tripId}/delivery-proof")
    suspend fun issue(@Path("tripId") tripId: Long): Response<DeliveryProofResponse>

    @GET("api/trips/{tripId}/delivery-proof")
    suspend fun get(@Path("tripId") tripId: Long): Response<DeliveryProofResponse>
}
