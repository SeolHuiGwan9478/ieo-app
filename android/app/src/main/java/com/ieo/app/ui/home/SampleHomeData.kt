package com.ieo.app.ui.home

import android.os.SystemClock
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

    fun idle() = HomeState.Idle(
        today = TodaySummary(tripCount = 1, netProfit = 320_000),
        unpaid = UnpaidAlert(brokerName = "○○물류", amount = 450_000, overdueDays = 3),
        nearby = true,
        myTrips = myTrips,
        orders = listOf(
            OrderCard("o1", "평택 청북", "대구 북구", "오늘", "17:00", "5톤 카고", 470_000, 380_000, 12),
            OrderCard("o2", "오산 가장", "광주 광산", "오늘", "17:30", "5톤 윙", 410_000, 300_000, null),
            OrderCard("o3", "화성 향남", "창원 성산", "내일", "06:00", "5톤 카고", 580_000, 450_000, 25),
        ),
    )

    /** 1시간 42분째 기다리는 중 */
    fun waiting() = HomeState.Waiting(
        trip = myTrips[0].toSummary(),
        stopName = "평택 포승 상차지",
        isPickup = true,
        waitStartedAt = SystemClock.elapsedRealtime() - (102 * 60 + 10) * 1000L,
        policy = WaitPolicy(freeMinutes = 120, unitMinutes = 30, unitAmount = 10_000, maxAmount = 50_000),
        site = SiteInfo(heightLimitMeters = 3.5, forklift = true, averageWaitMinutes = 80),
    )

    /** 하차지에서 기다리는 중. 다음 배차 추천 카드가 있다 (REC-021) */
    fun unloading() = waiting().copy(
        stopName = "대구 성서 하차지",
        isPickup = false,
        nextOrders = listOf(
            NextOrderCard(1, "칠곡", "부산 강서", "내일", "06:00", "5톤 윙", 372_229, 380_000, "한빛로지스", 17_000, longWait = true),
            NextOrderCard(2, "대구 북구", "울산 남구", "오늘", "18:30", "5톤 카고", 268_000, 280_000, "○○물류", 12_000, longWait = false),
        ),
    )

    fun driving() = HomeState.Driving(
        trip = myTrips[0].toSummary(),
        destinationName = "대구 성서",
        isPickup = false,
        etaText = "15:40",
        progress = 0.67f,
        remainingKm = 142,
        remainingMinutes = 108,
        etaShared = true,
        recommendation = Recommendation("칠곡 → 부산 강서", "16:00", 280_000),
    )
}
