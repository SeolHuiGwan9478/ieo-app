package com.ieo.app.domain.trip

import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant

/** 쉬어야 할 때 (TRIP-031) */
enum class RestLevel {
    /** 1시간 45분: 15분 안에 쉬어야 한다 */
    SOON,

    /** 2시간: 지금 쉬어야 한다 */
    NOW,
}

/** 연속 운전 [drivingFor]와, 쉬라고 알릴 때가 되었으면 [level] */
data class RestCheck(val drivingFor: Duration, val level: RestLevel?)

/**
 * 연속 운전 시간 (TRIP-030, 031). 차가 달리기 시작한 때부터 잰다.
 * 신호 대기·정체처럼 잠깐 선 것은 운전에 넣고, 15분 넘게 서 있으면 쉰 것으로 보고 0으로 되돌린다.
 * 차량 신호가 끊기면(null) 세던 것을 그대로 둔다. 운행과 상관없이 잰다.
 */
class RestTimer(
    private val rest: Duration = REST,
    private val soon: Duration = SOON,
    private val limit: Duration = LIMIT,
) {
    private var drivingSince: Instant? = null
    private var stoppedSince: Instant? = null

    /** 달리고 있지 않으면 null */
    fun check(now: Instant, motion: VehicleMotion?): RestCheck? {
        if (motion != null) {
            if (motion.isStopped) {
                val since = stoppedSince ?: now.also { stoppedSince = it }
                if (Duration.between(since, now) >= rest) drivingSince = null
            } else {
                stoppedSince = null
                if (drivingSince == null) drivingSince = now
            }
        }
        val since = drivingSince ?: return null
        val drivingFor = Duration.between(since, now)
        val level = when {
            drivingFor >= limit -> RestLevel.NOW
            drivingFor >= soon -> RestLevel.SOON
            else -> null
        }
        return RestCheck(drivingFor, level)
    }

    companion object {
        /** 이만큼 서 있으면 쉰 것이다 */
        val REST: Duration = Duration.ofMinutes(15)
        val SOON: Duration = Duration.ofMinutes(105)
        val LIMIT: Duration = Duration.ofHours(2)
    }
}
