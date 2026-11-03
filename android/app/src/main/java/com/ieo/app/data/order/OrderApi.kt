package com.ieo.app.data.order

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OrderApi {

    /** 위치를 보내면 반경(기본 50km) 안 상차지만 준다 */
    @GET("api/orders")
    suspend fun openOrders(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("size") size: Int? = null,
    ): Response<List<OrderResponse>>
}
