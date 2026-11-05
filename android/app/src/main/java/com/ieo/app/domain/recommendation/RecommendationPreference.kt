package com.ieo.app.domain.recommendation

/**
 * 다음 배차 추천 설정 (REC-004, 025). 서버에 차주당 하나, 안 정했으면 기본값.
 * [minNetProfit]이 null이면 실수익 조건 없음
 */
data class RecommendationPreference(
    val enabled: Boolean = true,
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    val minNetProfit: Long? = null,
) {
    companion object {
        const val DEFAULT_RADIUS_KM = 30
        const val MIN_RADIUS_KM = 5
        const val MAX_RADIUS_KM = 100
    }
}
