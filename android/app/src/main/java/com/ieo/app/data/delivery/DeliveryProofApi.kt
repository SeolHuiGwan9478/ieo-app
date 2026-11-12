package com.ieo.app.data.delivery

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface DeliveryProofApi {

    @GET("api/trips/{tripId}/delivery-proof")
    suspend fun get(@Path("tripId") tripId: Long): Response<DeliveryProofResponse>

    @GET("api/trips/{tripId}/delivery-proof/photos/{photoId}")
    suspend fun photo(@Path("tripId") tripId: Long, @Path("photoId") photoId: Long): Response<ResponseBody>
}
