package com.ieo.app.domain.trip

import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant

/** 쉬어야 할 때 (TRIP-031). 뒤로 갈수록 급하다 */
enum class RestLevel {
    /** 1시간 45분: 15분 안에 쉬어야 한다 */
    SOON,

    /** 2시간: 지금 15분 이상 쉬어야 한다 */
    NOW,

    /** 2시간 45분: 정체 등으로 연장운행 중. 15분 안에 30분 이상 쉬어야 한다 */
    EXTENDED_SOON,

    /** 3시간: 연장운행 한도. 바로 30분 이상 쉬어야 한다 */
    EXTENDED_NOW,
    ;

    /** 2시간을 넘겨 연장운행 중이다. 30분을 쉬어야 한다 */
    val extended: Boolean get() = this == EXTENDED_SOON || this == EXTENDED_NOW
}

/** 연속 운전 [drivingFor]와, 쉬라고 알릴 때가 되었으면 [level] */
data class RestCheck(val drivingFor: Duration, val level: RestLevel?)

/**
 * 연속 운전 시간 (TRIP-030, 031). 차가 달리기 시작한 때부터 잰다.
 * 신호 대기·정체처럼 잠깐 선 것은 운전에 넣고, 15분 넘게 서 있으면 쉰 것으로 보고 0으로 되돌린다.
 * 화물자동차 운수사업법 시행규칙 제22조제6호: 2시간 연속운전 뒤 15분 이상 쉰다. 정체 등으로 1시간까지 연장할 수 있고
 * 그때는 30분 이상 쉬어야 하므로, 2시간을 넘긴 뒤에 섰으면 30분을 서 있어야 0으로 되돌린다.
 * 차량 신호가 끊기면(null) 세던 것을 그대로 둔다. 운행과 상관없이 잰다.
 */
class RestTimer(
    private val rest: Duration = REST,
    private val extendedRest: Duration = EXTENDED_REST,
    private val soon: Duration = SOON,
    private val limit: Duration = LIMIT,
    private val extendedSoon: Duration = EXTENDED_SOON,
    private val extendedLimit: Duration = EXTENDED_LIMIT,
) {
    private var drivingSince: Instant? = null
    private var stoppedSince: Instant? = null

    /** 달리고 있지 않으면 null */
    fun check(now: Instant, motion: VehicleMotion?): RestCheck? {
        if (motion != null) {
            if (motion.isStopped) {
                val since = stoppedSince ?: now.also { stoppedSince = it }
                val drovePastLimit = drivingSince?.let { Duration.between(it, since) >= limit } == true
                if (Duration.between(since, now) >= (if (drovePastLimit) extendedRest else rest)) drivingSince = null
            } else {
                stoppedSince = null
                if (drivingSince == null) drivingSince = now
            }
        }
        val since = drivingSince ?: return null
        val drivingFor = Duration.between(since, now)
        val level = when {
            drivingFor >= extendedLimit -> RestLevel.EXTENDED_NOW
            drivingFor >= extendedSoon -> RestLevel.EXTENDED_SOON
            drivingFor >= limit -> RestLevel.NOW
            drivingFor >= soon -> RestLevel.SOON
            else -> null
        }
        return RestCheck(drivingFor, level)
    }

    companion object {
        /** 이만큼 서 있으면 쉰 것이다 */
        val REST: Duration = Duration.ofMinutes(15)

        /** 2시간을 넘겨 달린 뒤에는 이만큼 쉬어야 한다 */
        val EXTENDED_REST: Duration = Duration.ofMinutes(30)
        val SOON: Duration = Duration.ofMinutes(105)
        val LIMIT: Duration = Duration.ofHours(2)
        val EXTENDED_SOON: Duration = Duration.ofMinutes(165)
        val EXTENDED_LIMIT: Duration = Duration.ofHours(3)
    }
}
