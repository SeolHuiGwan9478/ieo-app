package com.ieo.app.domain.wait

import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant

/** 상하차지 반경 (TripStop의 좌표와 반경) */
data class GeoFence(val center: GeoPoint, val radiusMeters: Int) {
    fun contains(point: GeoPoint): Boolean = center.distanceTo(point) <= radiusMeters
}

/** 판단 결과 */
sealed interface ArrivalCheck {
    /** 반경 밖이거나 달리는 중, 또는 신호를 모른다 */
    data object NotYet : ArrivalCheck

    /** 반경 안에서 멈췄다. [remaining]만큼 더 서 있으면 도착 */
    data class Stopping(val remaining: Duration) : ArrivalCheck

    /** 도착 감지. 차주가 확인하면 대기를 시작한다 (WAIT-007). [at]은 멈추기 시작한 때로, 확인하면 대기 시간은 여기서부터 센다 */
    data class Arrived(val at: Instant) : ArrivalCheck
}

/**
 * 도착 감지 (WAIT-001): 상하차지 반경 안에서 3분 이상 세워 두면(기어 P 또는 주차 브레이크) 도착으로 본다.
 * 반경을 벗어나거나 다시 달리면 처음부터 센다. 신호가 끊기면(null) 세던 것을 그대로 둔다.
 * 상태를 들고 있으므로 운행의 정차 지점마다 [reset]한다.
 */
class ArrivalDetector(private val dwell: Duration = DEFAULT_DWELL) {

    private var stoppedSince: Instant? = null

    fun reset() {
        stoppedSince = null
    }

    fun check(now: Instant, fence: GeoFence, location: GeoPoint?, motion: VehicleMotion?): ArrivalCheck {
        if (location == null || motion == null) {
            return stoppedSince?.let { remainingOrArrived(now, it) } ?: ArrivalCheck.NotYet
        }
        if (!fence.contains(location) || !motion.isParked) {
            stoppedSince = null
            return ArrivalCheck.NotYet
        }
        val since = stoppedSince ?: now.also { stoppedSince = it }
        return remainingOrArrived(now, since)
    }

    private fun remainingOrArrived(now: Instant, since: Instant): ArrivalCheck {
        val stopped = Duration.between(since, now)
        return if (stopped >= dwell) ArrivalCheck.Arrived(since) else ArrivalCheck.Stopping(dwell - stopped)
    }

    companion object {
        val DEFAULT_DWELL: Duration = Duration.ofMinutes(3)
    }
}
