package com.ieo.app.ui.home

/**
 * 홈 화면 상태. 운행 상태에 따라 화면의 주인공이 바뀐다. (요구사항: docs/requirements/01_운행.md, 02_대기.md)
 * - [Idle] 운행 전: 내 배차와 근처 오더
 * - [Waiting] 상하차지 정차 중: 대기 타이머와 대기비
 * - [Driving] 주행 중: 도착 예정 시각과 진행 상황. 터치할 일을 두지 않는다
 */
sealed interface HomeState {

    data class Idle(
        val today: TodaySummary,
        val unpaid: UnpaidAlert?,
        val baseLocation: String,
        /** 내가 잡아 둔 배차. 눌러서 길 안내를 시작한다 */
        val myTrips: List<AssignedTrip>,
        val orders: List<OrderCard>,
    ) : HomeState

    data class Waiting(
        val trip: TripSummary,
        val stopName: String,
        val isPickup: Boolean,
        /** 대기 시작 시각 (SystemClock.elapsedRealtime 기준) */
        val waitStartedAt: Long,
        val policy: WaitPolicy?,
        val site: SiteInfo?,
    ) : HomeState

    data class Driving(
        val trip: TripSummary,
        /** 지금 가는 곳 (상차지 또는 하차지) */
        val destinationName: String,
        val isPickup: Boolean,
        /** 도착 예정 시각. 내비에서 아직 못 받았으면 null */
        val etaText: String?,
        /** 0.0 ~ 1.0. 모르면 null */
        val progress: Float?,
        val remainingKm: Int?,
        val remainingMinutes: Int?,
        val etaShared: Boolean,
        /** 음성으로 안내 중인 다음 배차 추천. 없으면 null */
        val recommendation: Recommendation?,
    ) : HomeState
}

data class TodaySummary(val tripCount: Int, val netProfit: Long)

data class UnpaidAlert(val brokerName: String, val amount: Long, val overdueDays: Int)

/** 상차지·하차지 한 곳. 길 안내에 좌표를 쓴다 */
data class Place(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

data class AssignedTrip(
    val id: String,
    val pickup: Place,
    val dropoff: Place,
    /** "오늘", "내일" */
    val pickupDay: String,
    /** "15:00" */
    val pickupTime: String,
    val vehicle: String,
    val fare: Long,
    val paymentType: String,
    val brokerName: String,
)

data class OrderCard(
    val id: String,
    val pickup: String,
    val dropoff: String,
    val pickupDay: String,
    val pickupTime: String,
    val vehicle: String,
    val fare: Long,
    val netProfit: Long,
    /** 주선사 평균 지급 소요일. 데이터가 부족하면 null */
    val brokerPaymentDays: Int?,
)

data class TripSummary(
    val route: String,
    val fare: Long,
    val paymentType: String,
    val brokerName: String,
)

data class Recommendation(val route: String, val pickupTime: String, val netProfit: Long)

/** 대기비 정책: 무료 시간이 지나면 단위 시간마다 금액이 붙는다 (올림) */
data class WaitPolicy(val freeMinutes: Int, val unitMinutes: Int, val unitAmount: Long, val maxAmount: Long?) {

    fun chargeableAmount(waitMinutes: Long): Long {
        val over = waitMinutes - freeMinutes
        if (over <= 0) return 0
        val units = (over + unitMinutes - 1) / unitMinutes
        val amount = units * unitAmount
        return maxAmount?.let { minOf(amount, it) } ?: amount
    }
}

data class SiteInfo(val heightLimitMeters: Double?, val forklift: Boolean?, val averageWaitMinutes: Int?)

fun AssignedTrip.toSummary() = TripSummary(
    route = "${pickup.name} → ${dropoff.name}",
    fare = fare,
    paymentType = paymentType,
    brokerName = brokerName,
)
