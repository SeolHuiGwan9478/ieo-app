package com.ieo.app.domain.wait

import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant

/** 판단 결과 */
sealed interface DepartureCheck {
    /** 상하차지에 있다. 또는 신호를 모른다 */
    data object Staying : DepartureCheck

    /** 떠나는 중. [remaining]만큼 더 이어지면 떠남 */
    data class Leaving(val remaining: Duration) : DepartureCheck

    /** 떠남. [at]은 반경을 벗어나거나 달리기 시작한 때 */
    data class Departed(val at: Instant) : DepartureCheck
}

/**
 * 자동 떠남 판단 (WAIT-005): 상하차지 반경을 벗어나거나 시속 20km 이상으로 1분 넘게 달리면 떠남.
 * - 반경 밖은 GPS가 튀는 것을 걸러 30초 이어져야 한다. 반경 밖에서 도착을 누른 경우를 위해, 한 번이라도 반경 안에 들어온 뒤에만 센다.
 * - 신호가 끊기면(null) 세던 것을 그대로 둔다.
 * 상태를 들고 있으므로 상하차지마다 [reset]한다.
 */
class DepartureDetector(
    private val outsideHold: Duration = DEFAULT_OUTSIDE_HOLD,
    private val fastHold: Duration = DEFAULT_FAST_HOLD,
    private val fastKph: Double = DEFAULT_FAST_KPH,
) {

    private var seenInside = false
    private var outsideSince: Instant? = null
    private var fastSince: Instant? = null

    fun reset() {
        seenInside = false
        outsideSince = null
        fastSince = null
    }

    fun check(now: Instant, fence: GeoFence, location: GeoPoint?, motion: VehicleMotion?): DepartureCheck {
        if (location != null) {
            if (fence.contains(location)) {
                seenInside = true
                outsideSince = null
            } else if (seenInside) {
                outsideSince = outsideSince ?: now
            }
        }
        if (motion != null) {
            fastSince = if (motion.speedKph >= fastKph) fastSince ?: now else null
        }

        val started = listOfNotNull(outsideSince?.let { it to outsideHold }, fastSince?.let { it to fastHold })
        val departed = started.filter { (since, hold) -> Duration.between(since, now) >= hold }.minOfOrNull { it.first }
        if (departed != null) return DepartureCheck.Departed(departed)
        val remaining = started.minOfOrNull { (since, hold) -> hold - Duration.between(since, now) }
        return remaining?.let(DepartureCheck::Leaving) ?: DepartureCheck.Staying
    }

    companion object {
        val DEFAULT_OUTSIDE_HOLD: Duration = Duration.ofSeconds(30)
        val DEFAULT_FAST_HOLD: Duration = Duration.ofMinutes(1)
        const val DEFAULT_FAST_KPH = 20.0
    }
}
