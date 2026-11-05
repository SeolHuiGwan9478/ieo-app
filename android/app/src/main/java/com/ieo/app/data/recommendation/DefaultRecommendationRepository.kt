package com.ieo.app.data.recommendation

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.recommendation.NextOrders
import com.ieo.app.domain.recommendation.RecommendationAnswer
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.recommendation.RecommendationRepository

class DefaultRecommendationRepository(
    private val api: RecommendationApi,
    private val caller: ApiCaller,
) : RecommendationRepository {

    override suspend fun recommend(tripId: Long): ApiResult<NextOrders> =
        caller.call { api.recommend(tripId) }.mapResponse { it.toDomain() }

    override suspend fun answer(tripId: Long, orderId: Long, answer: RecommendationAnswer?): ApiResult<DispatchRequest?> =
        caller.call { api.answer(tripId, orderId, AnswerRecommendationRequest(answer?.name)) }
            .mapResponse { it.dispatchRequest?.toDomain() }

    override suspend fun preference(): ApiResult<RecommendationPreference> =
        caller.call { api.preference() }.mapResponse { it.toDomain() }

    override suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference> =
        caller.call { api.changePreference(RecommendationPreferenceBody.of(preference)) }.mapResponse { it.toDomain() }
}
