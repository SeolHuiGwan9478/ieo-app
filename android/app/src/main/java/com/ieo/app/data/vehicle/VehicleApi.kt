package com.ieo.app.data.vehicle

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface VehicleApi {

    @GET("api/vehicles")
    suspend fun vehicles(): Response<List<VehicleResponse>>

    @PATCH("api/vehicles/{vehicleId}")
    suspend fun update(@Path("vehicleId") vehicleId: Long, @Body body: UpdateVehicleRequest): Response<VehicleResponse>

    @POST("api/vehicles/pleos/resolve")
    suspend fun resolvePleos(@Body body: PleosVinRequest): Response<PleosResolveResponse>

    @POST("api/vehicles/{vehicleId}/pleos")
    suspend fun linkPleos(@Path("vehicleId") vehicleId: Long, @Body body: PleosVinRequest): Response<VehicleResponse>
}
