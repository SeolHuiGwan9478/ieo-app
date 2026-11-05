package com.ieo.app.domain.recommendation

import com.ieo.app.domain.order.Order
import java.time.Instant

/**
 * 지금 운행 다음에 이어서 할 배차 후보 하나 (서버 RecommendationResponse, 03 배차 추천).
 * [order]의 distanceMeters는 하차지에서 상차지까지 공차 거리다.
 */
data class NextOrder(
    val order: Order,
    /** 하차지 → 다음 상차지 공차 거리(어림) */
    val emptyMeters: Long,
    /** 공차 연료비(원) */
    val fuelCost: Long,
    /** 실수익 = 운임 − 공차 연료비 (REC-010) */
    val netProfit: Long,
    /** 상차지 평균 대기가 길었던 곳 (REC-012) */
    val longWait: Boolean,
)

/** 실수익 높은 순 최대 5건. [freeAt]은 하차가 끝날 예정 시각, 추천하지 않는 상태면 null */
data class NextOrders(val freeAt: Instant?, val items: List<NextOrder>)

/** 안내한 추천에 차주가 한 대답. 대답이 없으면 null로 보낸다 */
enum class RecommendationAnswer { NEXT, REJECTED, ACCEPTED }
