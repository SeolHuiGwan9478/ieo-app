package com.ieo.app.data.trip

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TripApi {

    @GET("api/trips")
    suspend fun myTrips(@Query("size") size: Int? = null): Response<List<TripResponse>>

    /** 진행 중인 운행이 없으면 204 */
    @GET("api/trips/current")
    suspend fun currentTrip(): Response<TripResponse>

    @POST("api/trips/{tripId}/start")
    suspend fun start(@Path("tripId") tripId: Long): Response<TripResponse>

    @POST("api/trips")
    suspend fun register(@Body body: CreateTripRequest): Response<TripResponse>
}
