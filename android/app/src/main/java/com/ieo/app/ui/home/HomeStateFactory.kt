package com.ieo.app.ui.home

import android.os.SystemClock
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.wait.WaitRecord
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 홈 화면을 만드는 데 필요한 서버 데이터 */
data class TripSnapshot(
    /** 끝나지 않은 운행 (상차 시각 순) */
    val upcoming: List<Trip> = emptyList(),
    /** 출발해서 진행 중인 운행 */
    val current: Trip? = null,
    /** 진행 중인 운행이 상하차지에 있을 때 그곳의 대기 기록 */
    val wait: WaitRecord? = null,
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
    private val tripMapper: AssignedTripMapper = AssignedTripMapper(),
    private val clock: Clock = Clock.systemUTC(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    fun create(snapshot: TripSnapshot, navi: NaviProgress?): HomeState {
        val trip = snapshot.current ?: return idle(snapshot.upcoming)
        val stop = trip.currentStop ?: return idle(snapshot.upcoming)
        val wait = snapshot.wait?.takeIf { it.stopType == stop }
        // 상하차지에 있는데 대기 기록을 못 받았으면 주행 화면의 "도착했어요"로 다시 받게 한다 (도착은 여러 번 불러도 같다)
        return if (trip.status.isAtStop && wait != null) waiting(trip, stop, wait) else driving(trip, stop, navi)
    }

    private fun idle(upcoming: List<Trip>): HomeState.Idle =
        // TODO: 오늘 수익·미수금(장부 API)과 근처 오더(배차 API)를 붙이면 샘플을 걷어낸다
        SampleHomeData.idle().copy(myTrips = upcoming.map(tripMapper::map))

    private fun driving(trip: Trip, stop: StopType, navi: NaviProgress?): HomeState.Driving {
        val remainSeconds = navi?.remainSeconds
        return HomeState.Driving(
            trip = tripMapper.map(trip).toSummary(),
            destinationName = trip.stop(stop).name,
            isPickup = stop == StopType.PICKUP,
            etaText = remainSeconds?.let { clock.instant().plusSeconds(it.toLong()).atZone(KOREA).format(TIME_FORMAT) },
            progress = navi?.fraction,
            remainingKm = navi?.remainMeters?.let { (it + 500) / 1000 },
            remainingMinutes = remainSeconds?.let { (it + 59) / 60 },
            // TODO: ETA 알림(09 알림)을 만들면 서버 값으로 바꾼다
            etaShared = false,
            recommendation = null,
            naviActive = navi != null,
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

    private companion object {
        val KOREA: ZoneId = ZoneId.of("Asia/Seoul")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
