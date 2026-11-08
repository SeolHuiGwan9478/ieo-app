package com.ieo.app.ui.home

import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.wait.WaitPolicy

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
        /** 차량 위치로 근처 오더를 찾았다. 위치를 몰라 전체에서 찾았으면 false ("모집 중인 오더") */
        val nearby: Boolean,
        /** 내가 잡아 둔 배차. 눌러서 길 안내를 시작한다 */
        val myTrips: List<AssignedTrip>,
        val orders: List<OrderCard>,
    ) : HomeState

    data class Waiting(
        val trip: TripSummary,
        val stopName: String,
        val isPickup: Boolean,
        /** 도착 시각 (SystemClock.elapsedRealtime 기준) */
        val waitStartedAt: Long,
        val policy: WaitPolicy?,
        val site: SiteInfo?,
        /** 대기비를 세기 시작하는 시각 (elapsedRealtime). 예정 시각보다 일찍 왔으면 도착보다 늦다 */
        val chargeableFrom: Long = waitStartedAt,
        /** 작업("상차 시작")을 누른 시각 (elapsedRealtime). 작업 전이면 null */
        val workStartedAt: Long? = null,
        /** 상하차지를 떠나는 중이다. 이만큼 더 이어지면 자동으로 떠남 처리 (WAIT-005) */
        val departingInSeconds: Long? = null,
        /** 하차지에 서 있을 때 보여 주는 다음 배차 추천. 실수익 높은 순 최대 5건 (REC-021) */
        val nextOrders: List<NextOrderCard> = emptyList(),
    ) : HomeState {
        val isWorking: Boolean get() = workStartedAt != null
    }

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
        /** 내비가 길 안내 중인지. 안내가 꺼졌거나 실패했으면 "길 안내 다시"·"도착했어요" 버튼을 보여 준다 */
        val naviActive: Boolean = true,
        /** 상하차지 반경 안에서 멈춰 있다. 이만큼 더 서 있으면 자동으로 도착 처리 (WAIT-001) */
        val arrivingInSeconds: Long? = null,
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
    /** 공차 연료비를 뺀 실수익. 아직 계산하지 못하면 null */
    val netProfit: Long?,
    /** 주선사 신뢰 지표. 서버가 주지 않으면 null */
    val brokerMetrics: BrokerMetrics?,
    /** 지금 위치에서 상차지까지 거리(m). 위치 없이 찾았으면 null */
    val distanceMeters: Long? = null,
    /** 일반이 아닌 화물 종류. 일반이거나 모르면 null */
    val cargo: CargoCategory? = null,
)

data class TripSummary(
    val route: String,
    val vehicle: String,
    val fare: Long,
    val paymentType: String,
    val brokerName: String,
)

data class Recommendation(val route: String, val pickupTime: String, val netProfit: Long)

/** 하차지 정차 중 다음 배차 추천 카드 하나 (REC-021). 누르면 수락 확인 창이 뜬다 */
data class NextOrderCard(
    val orderId: Long,
    val pickup: String,
    val dropoff: String,
    val pickupDay: String,
    val pickupTime: String,
    val vehicle: String,
    val netProfit: Long,
    val fare: Long,
    val brokerName: String,
    /** 하차지 → 상차지 공차 거리(m) */
    val emptyMeters: Long,
    val longWait: Boolean,
    /** 일반이 아닌 화물 종류. 일반이거나 모르면 null */
    val cargo: CargoCategory? = null,
    /** 주선사 신뢰 지표 (BRK-031). 서버가 주지 않으면 null */
    val brokerMetrics: BrokerMetrics? = null,
) {
    val route: String get() = "$pickup → $dropoff"
}

/** 상하차지 정보 카드. 아무도 남기지 않은 항목은 null */
data class SiteInfo(
    val heightLimitMeters: Double?,
    val forklift: Boolean?,
    val averageWaitMinutes: Int?,
    val heightWarning: HeightWarning = HeightWarning.UNKNOWN,
    val parking: Boolean? = null,
    val averageWorkMinutes: Int? = null,
    val notes: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = heightLimitMeters == null && forklift == null && parking == null &&
            averageWaitMinutes == null && averageWorkMinutes == null && notes.isEmpty()
}

fun AssignedTrip.toSummary() = TripSummary(
    route = "${pickup.name} → ${dropoff.name}",
    vehicle = vehicle,
    fare = fare,
    paymentType = paymentType,
    brokerName = brokerName,
)
