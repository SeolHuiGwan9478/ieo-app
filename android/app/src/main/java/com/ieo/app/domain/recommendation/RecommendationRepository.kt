package com.ieo.app.domain.recommendation

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.order.DispatchRequest

interface RecommendationRepository {

    /** 운송 중인 운행 다음에 이어서 할 배차 후보. 운송 전이거나 추천을 껐으면 빈 목록 */
    suspend fun recommend(tripId: Long): ApiResult<NextOrders>

    /**
     * 안내한 추천과 대답을 서버에 적는다. [answer]가 null이면 안내만 하고 대답이 없었던 것.
     * 수락이면 배차 요청이 가고 그 요청을 돌려준다
     */
    suspend fun answer(tripId: Long, orderId: Long, answer: RecommendationAnswer?): ApiResult<DispatchRequest?>
}
