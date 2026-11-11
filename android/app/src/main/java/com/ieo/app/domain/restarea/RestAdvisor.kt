package com.ieo.app.domain.restarea

import com.ieo.app.domain.trip.RestLevel

/** 휴게 안내 때 말할 곳 (docs/design/08_휴게소.md 4장) */
sealed interface RestAdvice {
    val stop: RestStop

    /** 제때 갈 수 있는 라운지 */
    data class Lounge(override val stop: RestStop) : RestAdvice

    /** 제때 갈 수 있는 라운지가 없어 졸음쉼터. 그다음 라운지도 함께 말한다 */
    data class Shelter(override val stop: RestStop, val nextLounge: RestStop?) : RestAdvice

    /** 제때 갈 수 있는 곳이 없어 다음 라운지만 */
    data class NextLounge(override val stop: RestStop) : RestAdvice
}

/**
 * 경로 위 쉴 곳에서 말할 곳을 고른다. 15분이면 화물차가 약 20km를 가므로 [REACH_METERS] 안을 제때 갈 수 있는 곳으로 본다.
 * - 1시간 45분·2시간 45분 (15분 안에): 라운지 → 졸음쉼터 → 다음 라운지
 * - 2시간·3시간 (지금): 종류와 상관없이 가장 가까운 곳
 * [stops]는 경로 거리순이고, 지금 위치 기준 거리로 고쳐진 것이다
 */
object RestAdvisor {

    const val REACH_METERS = 20_000

    fun advise(level: RestLevel, stops: List<RestStop>): RestAdvice? {
        val nextLounge = stops.firstOrNull { it.isLounge }
        if (level == RestLevel.NOW || level == RestLevel.EXTENDED_NOW) {
            val nearest = stops.firstOrNull() ?: return null
            return if (nearest.isLounge) RestAdvice.Lounge(nearest) else RestAdvice.Shelter(nearest, nextLounge)
        }
        val reachable = stops.filter { it.aheadMeters <= REACH_METERS }
        reachable.firstOrNull { it.isLounge }?.let { return RestAdvice.Lounge(it) }
        reachable.firstOrNull()?.let { return RestAdvice.Shelter(it, nextLounge) }
        return nextLounge?.let(RestAdvice::NextLounge)
    }
}
