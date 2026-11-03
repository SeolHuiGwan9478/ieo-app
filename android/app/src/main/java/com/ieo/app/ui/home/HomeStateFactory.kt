package com.ieo.app.ui.home

import android.os.SystemClock
import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** 홈 화면을 만드는 데 필요한 서버 데이터. 못 받은 값은 비어 있다 */
data class HomeSnapshot(
    /** 끝나지 않은 운행 (상차 시각 순) */
    val upcoming: List<Trip> = emptyList(),
    /** 출발해서 진행 중인 운행 */
    val current: Trip? = null,
    /** 진행 중인 운행이 상하차지에 있을 때 그곳의 대기 기록 */
    val wait: WaitRecord? = null,
    /** 오늘 운행 수와 순수익 */
    val today: DailySummary? = null,
    val receivables: Receivables? = null,
    /** 모집 중 오더 */
    val orders: List<Order> = emptyList(),
)

/**
 * 서버 데이터와 내비 진행 상황으로 홈 화면 상태([HomeState])를 정한다. 화면을 고르는 규칙이 여기 한곳에 있다.
 * - 진행 중인 운행이 없으면 → 운행 전 (내 배차 목록)
 * - 상하차지로 가는 중이면 → 주행 중
 * - 상하차지에 있으면 → 대기 중 (작업을 시작했으면 작업 중)
 *
 * 서버 시각(Instant)을 화면 타이머용 기기 시계(elapsedRealtime)로 바꾸는 일도 여기서 한다.
 */
class HomeStateFactory(
    private val clock: Clock = Clock.systemUTC(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    private val labels = DayTimeFormatter(clock)
    private val tripMapper = AssignedTripMapper(clock)
    private val orderMapper = OrderCardMapper(clock)

    fun create(snapshot: HomeSnapshot, navi: NaviProgress?): HomeState {
        val trip = snapshot.current ?: return idle(snapshot)
        val stop = trip.currentStop ?: return idle(snapshot)
        val wait = snapshot.wait?.takeIf { it.stopType == stop }
        // 상하차지에 있는데 대기 기록을 못 받았으면 주행 화면의 "도착했어요"로 다시 받게 한다 (도착은 여러 번 불러도 같다)
        return if (trip.status.isAtStop && wait != null) waiting(trip, stop, wait) else driving(trip, stop, navi)
    }

    private fun idle(snapshot: HomeSnapshot) = HomeState.Idle(
        today = snapshot.today?.let { TodaySummary(it.tripCount, it.netProfit) } ?: TodaySummary(tripCount = 0, netProfit = 0),
        unpaid = snapshot.receivables?.mostOverdue(labels.today())?.let { UnpaidAlert(it.brokerName, it.amount, it.overdueDays) },
        // TODO: 차량 위치를 받으면 그 지역 이름으로 "근처 오더"를 보여 준다
        baseLocation = null,
        myTrips = snapshot.upcoming.map(tripMapper::map),
        orders = snapshot.orders.map(orderMapper::map),
    )

    private fun driving(trip: Trip, stop: StopType, navi: NaviProgress?): HomeState.Driving {
        val remainSeconds = navi?.remainSeconds
        return HomeState.Driving(
            trip = tripMapper.map(trip).toSummary(),
            destinationName = trip.stop(stop).name,
            isPickup = stop == StopType.PICKUP,
            etaText = remainSeconds?.let { labels.timeAfter(it.toLong()) },
            progress = navi?.fraction,
            remainingKm = navi?.remainMeters?.let { (it + 500) / 1000 },
            remainingMinutes = remainSeconds?.let { (it + 59) / 60 },
            // TODO: ETA 알림(09 알림)을 만들면 서버 값으로 바꾼다
            etaShared = false,
            recommendation = null,
            naviActive = navi != null && !navi.failed,
        )
    }

    private fun waiting(trip: Trip, stop: StopType, wait: WaitRecord) = HomeState.Waiting(
        trip = tripMapper.map(trip).toSummary(),
        stopName = trip.stop(stop).name,
        isPickup = stop == StopType.PICKUP,
        waitStartedAt = toElapsed(wait.arrivedAt),
        chargeableFrom = toElapsed(wait.chargeableFrom),
        workStartedAt = wait.workStartedAt?.let(::toElapsed),
        policy = wait.policy,
        // TODO: 상하차지 카드 API(/api/sites/{id})를 붙인다
        site = null,
    )

    /** 서버 시각을 기기 시계 기준으로. 기기 시계를 바꿔도 타이머가 맞는다 */
    private fun toElapsed(at: Instant): Long = elapsedRealtime() - Duration.between(at, clock.instant()).toMillis()
}
