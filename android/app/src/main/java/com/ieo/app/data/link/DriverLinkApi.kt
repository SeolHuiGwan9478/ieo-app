package com.ieo.app.data.link

import retrofit2.Response
import retrofit2.http.GET

interface DriverLinkApi {

    @GET("api/driver-links/invitations")
    suspend fun invitations(): Response<List<DriverLinkResponse>>

    @GET("api/driver-links")
    suspend fun links(): Response<List<DriverLinkResponse>>
}
