package com.ieo.app.domain.order

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.vehicle.GeoPoint

/** 오더 탭 거르기 (DSP-010). 거리는 [near]가 있을 때만 쓴다 */
data class OrderSearch(val radiusKm: Int = 50, val minFare: Long? = null, val paymentType: String? = null)

interface OrderRepository {

    /**
     * 내 차량으로 맡을 수 있는 모집 중 오더 (상차가 빠른 순), 최대 [limit]건.
     * [near]를 주면 그곳에서 상차지까지 50km 안만 주고, 오더마다 거리를 붙인다 (근처 오더)
     */
    suspend fun openOrders(limit: Int, near: GeoPoint? = null, search: OrderSearch = OrderSearch()): ApiResult<List<Order>>

    /** 실제 경로로 계산한 실수익 내역 (오더 상세) */
    suspend fun profit(orderId: Long, from: GeoPoint): ApiResult<OrderProfit>

    /** 필수 서류가 모두 승인됐는지. 아니면 배차를 요청할 수 없다 (서류는 휴대폰 앱에서 올린다) */
    suspend fun documentsVerified(): ApiResult<Boolean> = ApiResult.Success(true)

    /** 배차 요청 (기본 차량으로). 동시에 3건까지 */
    /** [isRecommendation]: 차량 홈의 배차 추천에서 눌러 보냈다 (03 배차 추천). 서버 요청에 그대로 남는다 */
    suspend fun request(orderId: Long, isRecommendation: Boolean = false): ApiResult<DispatchRequest>

    /** 확정 전 요청 철회 */
    suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest>

    suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit>
}
