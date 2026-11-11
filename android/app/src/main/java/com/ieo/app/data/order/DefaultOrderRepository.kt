package com.ieo.app.data.order

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderProfit
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.order.OrderSearch
import com.ieo.app.domain.order.ReportType
import com.ieo.app.domain.vehicle.GeoPoint

class DefaultOrderRepository(
    private val api: OrderApi,
    private val caller: ApiCaller,
) : OrderRepository {

    override suspend fun documentsVerified(): ApiResult<Boolean> =
        caller.call { api.documents() }.mapResponse { it.verified }

    override suspend fun openOrders(limit: Int, near: GeoPoint?, search: OrderSearch): ApiResult<List<Order>> =
        caller.call {
            api.openOrders(
                lat = near?.latitude, lng = near?.longitude, size = limit,
                radiusM = near?.let { search.radiusKm * 1_000 }, minFare = search.minFare, paymentType = search.paymentType,
            )
        }.mapResponse { orders -> orders.map(OrderResponse::toDomain) }

    override suspend fun profit(orderId: Long, from: GeoPoint): ApiResult<OrderProfit> =
        caller.call { api.profit(orderId, from.latitude, from.longitude) }.mapResponse { it.toDomain() }

    override suspend fun request(orderId: Long, isRecommendation: Boolean): ApiResult<DispatchRequest> =
        caller.call { api.request(orderId, CreateDispatchRequestRequest(isRecommendation = isRecommendation.takeIf { it })) }
            .mapResponse { it.toDomain() }

    override suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest> =
        caller.call { api.withdraw(requestId) }.mapResponse { it.toDomain() }

    override suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit> =
        caller.callOptional { api.report(orderId, CreateOrderReportRequest(type.name)) }.mapResponse { }
}
