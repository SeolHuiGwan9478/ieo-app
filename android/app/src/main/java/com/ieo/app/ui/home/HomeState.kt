package com.ieo.app.ui.home

import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.trip.RestCheck
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
        /** 내가 잡아 둔 배차. 눌러서 길 안내를 시작한다 */
        val myTrips: List<AssignedTrip>,
        /** 근처 추천 1건 (03 배차 추천). 세워 두지 않았으면 비어 있다 */
        val picks: List<NextOrderCard> = emptyList(),
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
        /** 근처 추천 1건 (03 배차 추천). 세워 두지 않았으면 비어 있다 */
        val picks: List<NextOrderCard> = emptyList(),
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
        /** 내비가 길 안내 중인지. 안내가 꺼졌거나 실패했으면 "길 안내 다시"·"대기 시작" 버튼을 보여 준다 */
        val naviActive: Boolean = true,
        /** 상하차지 반경 안에 세워 두었다. 이만큼 더 서 있으면 도착을 확인한다 (WAIT-001, 007) */
        val arrivingInSeconds: Long? = null,
        /** 가는 상하차지 3km 안이다. "대기 시작" 버튼을 보여 준다 (WAIT-008) */
        val nearStop: Boolean = false,
        /** 세워 두었다(기어 P 또는 주차 브레이크). 이때만 버튼이 눌린다 */
        val parked: Boolean = false,
        /** 근처 추천 1건 (03 배차 추천). 세워 두지 않았으면 비어 있다 */
        val picks: List<NextOrderCard> = emptyList(),
        /** 쉬어야 할 때가 되었으면 연속 운전 시간과 단계 (TRIP-031). 아니면 null */
        val rest: RestCheck? = null,
        /** 경로 위 쉴 곳, 경로 거리순 (TRIP-032). 지금 위치 기준으로 고친 거리 */
        val restStops: List<RestStop> = emptyList(),
        /** 휴게 안내 중 말한 곳. 카드에 강조해 보여 준다 */
        val restFocus: RestStop? = null,
        /** 휴게소를 보여 줄 때 함께 적는 출처 */
        val restAttribution: String? = null,
    ) : HomeState {
        /** 다음 화물차 라운지 */
        val nextLounge: RestStop? get() = restStops.firstOrNull { it.isLounge }

        /** "대기 시작" 버튼을 보여 주나: 상하차지 가까이 왔거나, 길 안내가 꺼졌을 때 */
        val showsStartWait: Boolean get() = nearStop || !naviActive
    }
}

enum class StopQuestionKind {
    /** 도착을 감지했다 (WAIT-007) */
    ARRIVE,

    /** 떠남을 감지했다 (WAIT-005) */
    DEPART,

    /** 떠남 확인으로 운행을 마쳤다. 대기 증빙을 만들지 묻는다 (WAIT-020) */
    PROOF,
}

/** 도착이나 떠남을 감지하고 차주에게 묻는 창 */
data class StopQuestion(
    val kind: StopQuestionKind,
    val stopName: String,
    val isPickup: Boolean,
    /** 확인하면 기록할 시각 "14:00" (감지한 때) */
    val timeLabel: String,
    /** 묻는 말을 마쳤다. 이때부터 시간을 잰다 */
    val asked: Boolean = false,
) {
    val title: String
        get() = when (kind) {
            StopQuestionKind.ARRIVE -> "도착 확인"
            StopQuestionKind.DEPART -> if (isPickup) "상차 끝 확인" else "하차 끝 확인"
            StopQuestionKind.PROOF -> "대기 증빙"
        }

    val question: String
        get() = when (kind) {
            StopQuestionKind.ARRIVE -> "${stopName}에 도착하셨어요?"
            StopQuestionKind.DEPART -> if (isPickup) "${stopName}에서 상차 끝내고 출발할까요?" else "${stopName}에서 하차 끝내고 운행을 마칠까요?"
            StopQuestionKind.PROOF -> "대기 증빙을 만들까요?"
        }

    val detail: String
        get() = when (kind) {
            StopQuestionKind.ARRIVE -> "${timeLabel}부터 대기로 기록할게요"
            StopQuestionKind.DEPART -> "${timeLabel}에 떠난 것으로 기록할게요"
            StopQuestionKind.PROOF -> "운행을 마쳤어요. 만들면 주선사에게 보낼 링크가 생겨요"
        }

    val confirmLabel: String
        get() = when (kind) {
            StopQuestionKind.ARRIVE -> "도착 맞아요"
            StopQuestionKind.DEPART -> "끝났어요"
            StopQuestionKind.PROOF -> "만들기"
        }

    val declineLabel: String
        get() = if (kind == StopQuestionKind.PROOF) "괜찮아요" else "아직이에요"
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

data class TripSummary(
    val route: String,
    val vehicle: String,
    val fare: Long,
    val paymentType: String,
    val brokerName: String,
)

/** 배차 추천 카드 하나 (03): 상하차지(운행이 없으면 세운 곳) 근처 오더. 누르면 오더를 열고 배차를 요청한다 */
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
    /** 공차 + 운송 구간 기름값(원) */
    val fuelCost: Long = 0,
    /** 예상 통행료(원). 경로를 받지 못했으면 null */
    val tollFare: Long? = null,
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
    val notes: List<String> = emptyList(),
    /** "12분 전 제보". 아무도 정보를 안 남겼으면 null (휴대폰 현장 정보와 같은 문구) */
    val reportedLabel: String? = null,
)

fun AssignedTrip.toSummary() = TripSummary(
    route = "${pickup.name} → ${dropoff.name}",
    vehicle = vehicle,
    fare = fare,
    paymentType = paymentType,
    brokerName = brokerName,
)
