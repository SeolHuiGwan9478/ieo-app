package com.ieo.app.data.recommendation

import com.ieo.app.data.order.OrderResponse
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.recommendation.Recommendation
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.recommendation.RecommendationPreference
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface RecommendationApi {

    /** 추천 최대 1건. 상하차지에 있으면 그곳 근처, 운행이 없으면 세운 자리 근처 */
    @GET("api/recommendations")
    suspend fun recommend(@Query("lat") lat: Double, @Query("lng") lng: Double): Response<RecommendationListResponse>

    @GET("api/recommendation-preference")
    suspend fun preference(): Response<RecommendationPreferenceBody>

    @PUT("api/recommendation-preference")
    suspend fun changePreference(@Body body: RecommendationPreferenceBody): Response<RecommendationPreferenceBody>
}

// 서버 domain/recommendation/dto와 같은 모양

@Serializable
data class RecommendationListResponse(val items: List<RecommendationResponse> = emptyList(), val fresh: Boolean = true) {
    fun toDomain() = Recommendation(items.map(RecommendationResponse::toDomain), fresh)
}

@Serializable
data class RecommendationResponse(
    val order: OrderResponse,
    val emptyMeters: Long,
    val fuelCost: Long,
    val netProfit: Long,
    val longWait: Boolean = false,
    val tollFare: Long? = null,
) {
    fun toDomain() = NextOrder(
        order = order.toDomain(),
        emptyMeters = emptyMeters,
        fuelCost = fuelCost,
        netProfit = netProfit,
        longWait = longWait,
        tollFare = tollFare,
    )
}

/**
 * 서버 RecommendationPreferenceRequest·Response. 주고받는 모양이 같다.
 * 제외 화물은 기본값을 null로 둔다: 빈 목록도 보내야 서버가 "모두 받기"로 바꾼다 (null이면 그대로 둔다)
 */
@Serializable
data class RecommendationPreferenceBody(
    val enabled: Boolean,
    val radiusKm: Int,
    val minNetProfit: Long? = null,
    val excludedCargoCategories: List<String>? = null,
) {
    fun toDomain() = RecommendationPreference(
        enabled = enabled,
        radiusKm = radiusKm,
        minNetProfit = minNetProfit,
        excludedCargo = excludedCargoCategories.orEmpty().mapNotNull(CargoCategory::fromName).toSet(),
    )

    companion object {
        fun of(preference: RecommendationPreference) = RecommendationPreferenceBody(
            enabled = preference.enabled,
            radiusKm = preference.radiusKm,
            minNetProfit = preference.minNetProfit,
            excludedCargoCategories = preference.excludedCargo.sortedBy { it.ordinal }.map { it.name },
        )
    }
}
