package com.ieo.app.data.site

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SiteApi {

    @GET("api/sites/{siteId}")
    suspend fun card(@Path("siteId") siteId: Long): Response<SiteCardResponse>
}
