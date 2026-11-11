package com.ieo.app.ui.home

import android.os.SystemClock
import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.restarea.RestStopKind
import com.ieo.app.domain.wait.WaitPolicy

/**
 * 화면 확인용 샘플 데이터.
 * TODO: 운행·대기·배차 도메인(서버)과 차량 신호가 연결되면 실제 데이터로 바꾼다.
 */
object SampleHomeData {

    private val pyeongtaek = Place("평택 포승", "경기 평택시 포승읍 평택항로", 36.9690, 126.8410)
    private val daegu = Place("대구 성서", "대구 달서구 성서공단로", 35.8420, 128.4960)
    private val anseong = Place("안성 공도", "경기 안성시 공도읍", 37.0010, 127.1720)
    private val busan = Place("부산 강서", "부산 강서구 녹산산단로", 35.0880, 128.8630)

    val myTrips = listOf(
        AssignedTrip("t1", pyeongtaek, daegu, "오늘", "15:00", "5톤 카고", 450_000, "인수증", "○○물류"),
        AssignedTrip("t2", anseong, busan, "내일", "08:00", "5톤 카고", 620_000, "선불", "△△운송"),
    )

    /** 세운 곳 근처 추천 3건 (03 배차 추천) */
    val picks = listOf(
        NextOrderCard(1, "칠곡", "부산 강서", "내일", "06:00", "5톤 윙", 372_229, 380_000, "한빛로지스", 17_000, longWait = true, fuelCost = 57_000, tollFare = 17_000, brokerMetrics = BrokerMetrics(published = true, avgPaymentDays = 12)),
        NextOrderCard(2, "대구 북구", "울산 남구", "오늘", "18:30", "5톤 카고", 268_000, 280_000, "○○물류", 12_000, longWait = false, fuelCost = 12_000, tollFare = null),
        NextOrderCard(3, "대구 검단", "오산 가장", "내일", "07:00", "5톤 윙", 255_000, 300_000, "△△운송", 9_000, longWait = false, fuelCost = 45_000, tollFare = 13_000),
    )

    fun idle() = HomeState.Idle(
        today = TodaySummary(tripCount = 1, netProfit = 320_000),
        unpaid = UnpaidAlert(brokerName = "○○물류", amount = 450_000, overdueDays = 3),
        myTrips = myTrips,
        picks = picks,
    )

    /** 1시간 42분째 기다리는 중 */
    fun waiting() = HomeState.Waiting(
        trip = myTrips[0].toSummary(),
        stopName = "평택 포승 상차지",
        isPickup = true,
        waitStartedAt = SystemClock.elapsedRealtime() - (102 * 60 + 10) * 1000L,
        policy = WaitPolicy(freeMinutes = 120, unitMinutes = 30, unitAmount = 10_000, maxAmount = 50_000),
        site = SiteInfo(heightLimitMeters = 3.5, forklift = true, averageWaitMinutes = 80, notes = listOf("정문은 승용차만, 화물차는 후문(2게이트)으로 들어가요"), reportedLabel = "41분 전 제보"),
    )

    /** 하차지에서 기다리는 중. 세워 두어 근처 추천이 보인다 */
    fun unloading() = waiting().copy(
        stopName = "대구 성서 하차지",
        isPickup = false,
        picks = picks,
    )

    /** 경로 위 쉴 곳 (TRIP-032) */
    val restStops = listOf(
        RestStop(RestStopKind.LOUNGE, "성주휴게소", "창원", 18_000, listOf("수유실", "수면실", "샤워실", "세탁실", "쉼터"), "054-931-6100", 35.92, 128.27),
        RestStop(RestStopKind.DROWSY_SHELTER, "창녕졸음쉼터", null, 29_000, emptyList(), null, 35.55, 128.45),
        RestStop(RestStopKind.LOUNGE, "남성주참외휴게소", "창원", 37_000, listOf("수유실", "수면실", "샤워실", "세탁실"), null, 35.86, 128.34),
        RestStop(RestStopKind.LOUNGE, "영산휴게소", "창원", 92_000, listOf("수유실", "수면실", "샤워실", "세탁실"), null, 35.45, 128.52),
    )

    const val REST_ATTRIBUTION = "휴게소 정보: 공공데이터포털 전국휴게소정보표준데이터, 한국도로공사 고속도로 공공데이터 포털"

    fun driving() = HomeState.Driving(
        trip = myTrips[0].toSummary(),
        destinationName = "대구 성서",
        isPickup = false,
        etaText = "15:40",
        progress = 0.67f,
        remainingKm = 142,
        remainingMinutes = 108,
        etaShared = true,
    )
}
