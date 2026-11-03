package com.ieo.app.ui.orders

import com.ieo.app.domain.order.Order
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.common.formatWon
import java.time.Instant

/** 목록 한 줄 */
data class OrderRow(
    val id: Long,
    val time: String,
    val day: String,
    val route: String,
    val vehicle: String,
    val paymentType: String,
    val brokerName: String,
    val fare: String,
    /** 내가 요청해 둔 오더 */
    val requested: Boolean,
)

/** 아래에서 올라오는 상세 */
data class OrderDetail(
    val id: Long,
    val route: String,
    /** 이름 · 값 */
    val lines: List<Pair<String, String>>,
    /** 확정을 기다리는 내 요청이 있으면 "배차 요청" 대신 "요청 철회" */
    val requested: Boolean,
)

/** 확정을 기다리는 내 요청 (화면 위 띠) */
data class PendingRequest(
    val route: String,
    /** 만료 시각 (SystemClock.elapsedRealtime 기준) */
    val expiresAt: Long,
)

/** 오더([Order])를 오더 탭에 그릴 모양으로 바꾼다. 날짜 표기는 [labels]가 정한다 */
class OrderUiMapper(private val labels: DayTimeFormatter) {

    fun matches(order: Order, filter: OrderDayFilter): Boolean {
        val day = order.pickupFrom.atZone(DayTimeFormatter.KOREA).toLocalDate()
        return when (filter) {
            OrderDayFilter.ALL -> true
            OrderDayFilter.TODAY -> day == labels.today()
            OrderDayFilter.TOMORROW -> day == labels.today().plusDays(1)
        }
    }

    fun row(order: Order) = OrderRow(
        id = order.id,
        time = labels.time(order.pickupFrom),
        day = labels.day(order.pickupFrom),
        route = route(order),
        vehicle = order.vehicleLabel,
        paymentType = order.paymentTypeLabel,
        brokerName = order.brokerName,
        fare = formatWon(order.fare),
        requested = order.isRequestedByMe,
    )

    fun detail(order: Order) = OrderDetail(
        id = order.id,
        route = route(order),
        lines = listOfNotNull(
            "상차" to "${labels.dayTime(order.pickupFrom)} ~ ${labels.time(order.pickupUntil)} · ${order.pickup.address}",
            "하차" to "${labels.dayTime(order.dropoffBy)}까지 · ${order.dropoff.address}",
            "차량" to order.vehicleLabel,
            "화물" to cargo(order),
            "운임" to "${formatWon(order.fare)} · ${order.paymentTypeLabel} · ${order.expectedPaymentDays}일 안에 지급",
            "주선사" to order.brokerName,
        ),
        requested = order.isRequestedByMe,
    )

    fun pending(order: Order, toElapsed: (Instant) -> Long) = PendingRequest(
        route = route(order),
        expiresAt = toElapsed(order.myRequest!!.expiresAt),
    )

    private fun route(order: Order) = "${order.pickup.name} → ${order.dropoff.name}"

    /** "파렛트 · 4.2톤 · 지게차". 비어 있는 값은 뺀다 */
    private fun cargo(order: Order): String = listOfNotNull(
        order.cargoType,
        order.weightKg.takeIf { it > 0 }?.let { "%.1f톤".format(it / 1000.0) },
        order.loadingMethodLabel,
    ).joinToString(" · ").ifEmpty { "정보 없음" }
}
