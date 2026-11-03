package com.ieo.app.data.order

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderRepository

class DefaultOrderRepository(
    private val api: OrderApi,
    private val caller: ApiCaller,
) : OrderRepository {

    override suspend fun openOrders(): ApiResult<List<Order>> =
        caller.call { api.openOrders(size = HOME_ORDER_COUNT) }.mapResponse { orders -> orders.map(OrderResponse::toDomain) }

    private companion object {
        /** 홈 화면에 보여 줄 만큼만 */
        const val HOME_ORDER_COUNT = 5
    }
}
