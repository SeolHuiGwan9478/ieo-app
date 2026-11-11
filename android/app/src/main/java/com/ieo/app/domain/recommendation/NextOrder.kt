package com.ieo.app.domain.recommendation

import com.ieo.app.domain.order.Order

/**
 * 배차 추천 하나 (서버 RecommendationResponse, 03 배차 추천): 차를 세운 자리 근처 오더.
 * [order]의 distanceMeters는 세운 자리에서 상차지까지 공차 거리다.
 */
data class NextOrder(
    val order: Order,
    /** 세운 자리 → 상차지 공차 거리 */
    val emptyMeters: Long,
    /** 공차 + 운송 구간 기름값(원) */
    val fuelCost: Long,
    /** 실수익 = 운임 − 기름값 − 통행료 (REC-010) */
    val netProfit: Long,
    /** 상차지 평균 대기가 길었던 곳 (REC-012) */
    val longWait: Boolean,
    /** 공차 + 운송 구간 예상 통행료(원). 경로를 받지 못했으면 null */
    val tollFare: Long? = null,
)
