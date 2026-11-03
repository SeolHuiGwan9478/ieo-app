package com.ieo.app.data.order

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.order.ReportType

class DefaultOrderRepository(
    private val api: OrderApi,
    private val caller: ApiCaller,
) : OrderRepository {

    override suspend fun openOrders(limit: Int): ApiResult<List<Order>> =
        caller.call { api.openOrders(size = limit) }.mapResponse { orders -> orders.map(OrderResponse::toDomain) }

    override suspend fun request(orderId: Long): ApiResult<DispatchRequest> =
        caller.call { api.request(orderId) }.mapResponse { it.toDomain() }

    override suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest> =
        caller.call { api.withdraw(requestId) }.mapResponse { it.toDomain() }

    override suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit> =
        caller.callOptional { api.report(orderId, CreateOrderReportRequest(type.name)) }.mapResponse { }
}
