package com.ieo.app.ui.home

import com.ieo.app.domain.order.Order
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock

/** 오더 마켓의 오더([Order])를 홈 화면의 "근처 오더" 카드([OrderCard])로 바꾼다 */
class OrderCardMapper(clock: Clock = Clock.system(DayTimeFormatter.KOREA)) {

    private val labels = DayTimeFormatter(clock)

    fun map(order: Order) = OrderCard(
        id = order.id.toString(),
        pickup = order.pickup.name,
        dropoff = order.dropoff.name,
        pickupDay = labels.day(order.pickupFrom),
        pickupTime = labels.time(order.pickupFrom),
        vehicle = order.vehicleLabel,
        fare = order.fare,
        // 어림값이라 만 원 단위로만 보여 준다
        netProfit = order.netProfit?.let { (it + 5_000) / 10_000 * 10_000 },
        brokerMetrics = order.brokerMetrics,
        distanceMeters = order.distanceMeters,
        cargo = order.specialCargo,
    )
}
