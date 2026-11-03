package com.ieo.app.domain.order

import com.ieo.app.core.network.ApiResult

interface OrderRepository {

    /**
     * 내 차량으로 맡을 수 있는 모집 중 오더 (상차가 빠른 순).
     * TODO: 차량 위치를 받으면 위치와 반경을 넘겨 "근처 오더"로 좁힌다
     */
    suspend fun openOrders(): ApiResult<List<Order>>
}
