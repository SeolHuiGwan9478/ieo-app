package com.ieo.app.ui.home

import android.os.SystemClock
import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.restarea.RestAdvisor
import com.ieo.app.domain.restarea.RouteRestStopsAt
import com.ieo.app.domain.trip.RestCheck
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.wait.ArrivalCheck
import com.ieo.app.domain.wait.DepartureCheck
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
    /** 진행 중인 운행이 상하차지에 있을 때 그곳의 정보 카드. 못 받았으면 null */
    val site: SiteCard? = null,
    /** 오늘 운행 수와 실수익 */
    val today: DailySummary? = null,
    val receivables: Receivables? = null,
    /** 차를 세웠을 때 받은 근처 추천 3건 (03 배차 추천). 움직이면 비운다 */
    val nextOrders: List<NextOrder> = emptyList(),
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

    fun create(
        snapshot: HomeSnapshot,
        navi: NaviProgress?,
        arrival: ArrivalCheck = ArrivalCheck.NotYet,
        departure: DepartureCheck = DepartureCheck.Staying,
        /** 가는 상하차지 3km 안 */
        nearStop: Boolean = false,
        /** 세워 두었다 (기어 P 또는 주차 브레이크) */
        parked: Boolean = false,
        /** 연속 운전 시간 (TRIP-030). 쉬어야 할 때만 운행 화면에 보여 준다 */
        rest: RestCheck? = null,
        /** 받아 둔 경로 위 쉴 곳 (TRIP-032) */
        restStops: RouteRestStopsAt? = null,
    ): HomeState {
        val picks = snapshot.nextOrders.map(::nextOrderCard)
        val trip = snapshot.current ?: return idle(snapshot, picks)
        val stop = trip.currentStop ?: return idle(snapshot, picks)
        val wait = snapshot.wait?.takeIf { it.stopType == stop }
        // 상하차지에 있는데 대기 기록을 못 받았으면 주행 화면의 "대기 시작"으로 다시 받게 한다 (도착은 여러 번 불러도 같다)
        if (!trip.status.isAtStop || wait == null) return driving(trip, stop, navi, arrival, nearStop, parked, picks, rest, restStops)
        val site = snapshot.site?.takeIf { it.id == trip.stop(stop).siteId }
        return waiting(trip, stop, wait, site, departure, picks)
    }

    private fun idle(snapshot: HomeSnapshot, picks: List<NextOrderCard>) = HomeState.Idle(
        today = snapshot.today?.let { TodaySummary(it.tripCount, it.netProfit) } ?: TodaySummary(tripCount = 0, netProfit = 0),
        unpaid = snapshot.receivables?.mostOverdue(labels.today())?.let { UnpaidAlert(it.brokerName, it.amount, it.overdueDays) },
        myTrips = snapshot.upcoming.map(tripMapper::map),
        picks = picks,
    )

    private fun driving(
        trip: Trip,
        stop: StopType,
        navi: NaviProgress?,
        arrival: ArrivalCheck,
        nearStop: Boolean,
        parked: Boolean,
        picks: List<NextOrderCard>,
        rest: RestCheck?,
        restStops: RouteRestStopsAt?,
    ): HomeState.Driving {
        val stops = restStops?.takeIf { it.tripId == trip.id }?.current(navi?.remainMeters).orEmpty()
        val alert = rest?.takeIf { it.level != null }
        val remainSeconds = navi?.remainSeconds
        return HomeState.Driving(
            trip = tripMapper.map(trip).toSummary(),
            destinationName = trip.stop(stop).name,
            isPickup = stop == StopType.PICKUP,
            etaText = remainSeconds?.let { labels.timeAfter(it.toLong()) },
            progress = navi?.fraction,
            remainingKm = navi?.remainMeters?.let { (it + 500) / 1000 },
            remainingMinutes = remainSeconds?.let { (it + 59) / 60 },
            etaShared = trip.etaShared,
            naviActive = navi != null && !navi.failed,
            arrivingInSeconds = (arrival as? ArrivalCheck.Stopping)?.remaining?.seconds,
            nearStop = nearStop,
            parked = parked,
            picks = picks,
            rest = alert,
            restStops = stops,
            restFocus = alert?.level?.let { RestAdvisor.advise(it, stops)?.stop },
            restAttribution = restStops?.attribution?.takeIf { stops.isNotEmpty() },
        )
    }

    /** 화면에 보여 줄 시각 "14:00" */
    fun time(at: Instant): String = labels.time(at)

    private fun waiting(
        trip: Trip,
        stop: StopType,
        wait: WaitRecord,
        site: SiteCard?,
        departure: DepartureCheck,
        picks: List<NextOrderCard>,
    ) = HomeState.Waiting(
        trip = tripMapper.map(trip).toSummary(),
        stopName = trip.stop(stop).name,
        isPickup = stop == StopType.PICKUP,
        waitStartedAt = toElapsed(wait.arrivedAt),
        chargeableFrom = toElapsed(wait.chargeableFrom),
        workStartedAt = wait.workStartedAt?.let(::toElapsed),
        policy = wait.policy,
        site = site?.let(::siteInfo),
        departingInSeconds = (departure as? DepartureCheck.Leaving)?.remaining?.seconds,
        picks = picks,
    )

    /** 추천 카드 (03): 상차지, 하차지, 상차 시각, 톤수, 실수익, 주선사 */
    fun nextOrderCard(next: NextOrder) = NextOrderCard(
        orderId = next.order.id,
        pickup = next.order.pickup.name,
        dropoff = next.order.dropoff.name,
        pickupDay = labels.day(next.order.pickupFrom),
        pickupTime = labels.time(next.order.pickupFrom),
        vehicle = next.order.vehicleLabel,
        netProfit = next.netProfit,
        fare = next.order.fare,
        brokerName = next.order.brokerLabel,
        emptyMeters = next.emptyMeters,
        longWait = next.longWait,
        cargo = next.order.specialCargo,
        brokerMetrics = next.order.brokerMetrics,
        fuelCost = next.fuelCost,
        tollFare = next.tollFare,
    )

    private fun siteInfo(card: SiteCard) = SiteInfo(
        heightLimitMeters = card.heightLimitMeters,
        heightWarning = card.heightWarning,
        forklift = card.forklift,
        parking = card.parking,
        averageWaitMinutes = card.averageWaitMinutes,
        notes = card.notes.take(SITE_NOTE_COUNT),
        reportedLabel = card.reportedAt?.let { "${timeAgo(it)} 제보" },
    )

    /** "방금", "12분 전", "3시간 전", "11월 9일" (휴대폰 앱과 같은 규칙) */
    private fun timeAgo(at: Instant): String {
        val minutes = Duration.between(at, clock.instant()).toMinutes()
        return when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            minutes < 24 * 60 -> "${minutes / 60}시간 전"
            else -> labels.shortDate(at.atZone(DayTimeFormatter.KOREA).toLocalDate())
        }
    }

    /** 서버 시각을 기기 시계 기준으로. 기기 시계를 바꿔도 타이머가 맞는다 */
    private fun toElapsed(at: Instant): Long = elapsedRealtime() - Duration.between(at, clock.instant()).toMillis()

    private companion object {
        /** 정차 화면에 보여 줄 메모 수 */
        const val SITE_NOTE_COUNT = 2
    }
}
