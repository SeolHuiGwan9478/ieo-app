package com.ieo.app.domain.order

import com.ieo.app.core.network.ApiResult

interface OrderRepository {

    /**
     * 내 차량으로 맡을 수 있는 모집 중 오더 (상차가 빠른 순), 최대 [limit]건.
     * TODO: 차량 위치를 받으면 위치와 반경을 넘겨 "근처 오더"로 좁힌다
     */
    suspend fun openOrders(limit: Int): ApiResult<List<Order>>

    /** 배차 요청 (기본 차량으로). 동시에 3건까지 */
    suspend fun request(orderId: Long): ApiResult<DispatchRequest>

    /** 확정 전 요청 철회 */
    suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest>

    suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit>
}
