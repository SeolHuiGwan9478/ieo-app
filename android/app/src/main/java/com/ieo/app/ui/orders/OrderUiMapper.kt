package com.ieo.app.ui.orders

import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderProfit
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
    /** 지금 위치에서 상차지까지 거리(m). 위치 없이 찾았으면 null */
    val distanceMeters: Long? = null,
    /** 일반이 아닌 화물 종류. 일반이거나 모르면 null */
    val cargo: CargoCategory? = null,
    /** 주선사 신뢰 지표. 서버가 주지 않으면 null */
    val brokerMetrics: BrokerMetrics? = null,
    /** "실수익 약 28만 원" 어림값. 위치를 몰라 계산하지 못하면 null */
    val netProfit: String? = null,
    val pickupName: String = "",
    /** "오늘 08:00~10:00" */
    val pickupTime: String = "",
    val dropoffName: String = "",
    /** "오늘 14:00까지" */
    val dropoffTime: String = "",
    /** 자동 새로고침으로 새로 들어왔다 */
    val isNew: Boolean = false,
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

    fun matches(order: Order, conditions: OrderConditions): Boolean {
        val day = order.pickupFrom.atZone(DayTimeFormatter.KOREA).toLocalDate()
        val dayOk = when (conditions.day) {
            OrderDayFilter.ALL -> true
            OrderDayFilter.TODAY -> day == labels.today()
            OrderDayFilter.TOMORROW -> day == labels.today().plusDays(1)
        }
        return dayOk && order.cargoCategory !in conditions.excluded
    }

    fun row(order: Order, isNew: Boolean = false) = OrderRow(
        id = order.id,
        time = labels.time(order.pickupFrom),
        day = labels.day(order.pickupFrom),
        route = route(order),
        vehicle = order.vehicleLabel,
        paymentType = order.paymentTypeLabel,
        // 서류 승인 전에는 화면 위 안내가 한 번 말해 주므로 줄마다 되풀이하지 않는다
        brokerName = if (order.limited) "" else order.brokerName,
        fare = formatWon(order.fare),
        requested = order.isRequestedByMe,
        distanceMeters = order.distanceMeters,
        cargo = order.specialCargo,
        brokerMetrics = order.brokerMetrics,
        netProfit = order.netProfit?.let { "실수익 약 ${formatWon(roundedToManwon(it))}" },
        pickupName = order.pickup.name,
        pickupTime = "${labels.dayTime(order.pickupFrom)}~${labels.time(order.pickupUntil)}",
        dropoffName = order.dropoff.name,
        dropoffTime = "${labels.dayTime(order.dropoffBy)}까지",
        isNew = isNew,
    )

    fun detail(order: Order, profit: OrderProfit? = null) = OrderDetail(
        id = order.id,
        route = route(order),
        lines = listOfNotNull(
            "상차" to "${labels.dayTime(order.pickupFrom)} ~ ${labels.time(order.pickupUntil)} · ${addressOf(order, order.pickup.address)}",
            "하차" to "${labels.dayTime(order.dropoffBy)}까지 · ${addressOf(order, order.dropoff.address)}",
            "차량" to order.vehicleLabel,
            "화물" to cargo(order),
            "운임" to "${formatWon(order.fare)} · ${order.paymentTypeLabel} · ${order.expectedPaymentDays}일 안에 지급",
            profitLine(order, profit),
            profit?.tollFare?.let { "예상 통행료" to formatWon(it) },
            profit?.let { "기름값" to formatWon(it.fuelCost) },
            profit?.let { "상차지까지" to "${(it.emptyMeters + 500) / 1000}km" },
            order.routeAverageFare?.let { "노선 평균 운임" to "${formatWon(roundedToManwon(it))} (Ieo에서 끝난 운행 기준)" },
            "주선사" to brokerOf(order),
            order.brokerMetrics?.let { "주선사 기록" to metricsSummary(it) },
        ),
        requested = order.isRequestedByMe,
    )

    /** 서류 심사 전이면 주선사를 가린다 */
    private fun brokerOf(order: Order): String = if (order.limited) "서류 승인 후 보여요" else order.brokerName

    /** 서류 심사 전이면 시·구까지만 온다 */
    private fun addressOf(order: Order, address: String): String = if (order.limited) "$address (정확한 주소는 승인 후)" else address

    private fun profitLine(order: Order, profit: OrderProfit?): Pair<String, String>? = when {
        profit != null && profit.tollFare != null -> "실수익" to "${formatWon(profit.netProfit)} (기름값·통행료 뺀 금액)"
        profit != null -> "실수익" to "${formatWon(profit.netProfit)} (기름값만 뺀 금액)"
        else -> order.netProfit?.let { "실수익" to "약 ${formatWon(roundedToManwon(it))} (기름값만 뺀 어림값)" }
    }

    private fun roundedToManwon(won: Long): Long = (won + 5_000) / 10_000 * 10_000

    fun pending(order: Order, toElapsed: (Instant) -> Long) = PendingRequest(
        route = route(order),
        expiresAt = toElapsed(order.myRequest!!.expiresAt),
    )

    private fun route(order: Order) = "${order.pickup.name} → ${order.dropoff.name}"

    /** "평균 지급 12일 · 지급 지연 8% · 대기비 지급 90% · 이 회사 운행 평균 대기 35분". 대기는 상하차지 탓일 때가 많아 중립적으로 적는다 */
    private fun metricsSummary(metrics: BrokerMetrics): String {
        if (!metrics.published) return "거래 기록 적음"
        return listOfNotNull(
            metrics.avgPaymentDays?.let { "평균 지급 ${it}일" },
            metrics.latePaymentPercent?.let { "지급 지연 $it%" },
            metrics.waitFeePaidPercent?.let { "대기비 지급 $it%" },
            metrics.avgWaitMinutes?.let { "이 회사 운행 평균 대기 ${it}분" },
        ).joinToString(" · ").ifEmpty { "아직 기록이 없어요" }
    }

    /** "냉동·냉장 · 냉동 닭 · 4.2톤 · 지게차". 비어 있는 값과 "일반"은 뺀다 */
    private fun cargo(order: Order): String = listOfNotNull(
        order.specialCargo?.label,
        order.cargoType,
        order.weightKg.takeIf { it > 0 }?.let { "%.1f톤".format(it / 1000.0) },
        order.loadingMethodLabel,
    ).joinToString(" · ").ifEmpty { "정보 없음" }
}
