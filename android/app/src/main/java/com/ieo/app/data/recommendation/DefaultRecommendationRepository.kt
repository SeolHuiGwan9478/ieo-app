package com.ieo.app.data.recommendation

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.recommendation.Recommendation
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.recommendation.RecommendationRepository

class DefaultRecommendationRepository(
    private val api: RecommendationApi,
    private val caller: ApiCaller,
) : RecommendationRepository {

    override suspend fun recommend(near: GeoPoint): ApiResult<Recommendation> =
        caller.call { api.recommend(near.latitude, near.longitude) }.mapResponse { it.toDomain() }

    override suspend fun preference(): ApiResult<RecommendationPreference> =
        caller.call { api.preference() }.mapResponse { it.toDomain() }

    override suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference> =
        caller.call { api.changePreference(RecommendationPreferenceBody.of(preference)) }.mapResponse { it.toDomain() }
}
