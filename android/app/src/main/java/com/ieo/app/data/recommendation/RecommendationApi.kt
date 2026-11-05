package com.ieo.app.data.recommendation

import com.ieo.app.data.order.DispatchRequestResponse
import com.ieo.app.data.order.OrderResponse
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.recommendation.NextOrders
import java.time.Instant
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface RecommendationApi {

    @GET("api/trips/{tripId}/recommendations")
    suspend fun recommend(@Path("tripId") tripId: Long): Response<RecommendationListResponse>

    @PUT("api/trips/{tripId}/recommendations/{orderId}")
    suspend fun answer(
        @Path("tripId") tripId: Long,
        @Path("orderId") orderId: Long,
        @Body body: AnswerRecommendationRequest,
    ): Response<RecommendationAnswerResponse>
}

// 서버 domain/recommendation/dto와 같은 모양

@Serializable
data class RecommendationListResponse(val freeAt: String? = null, val items: List<RecommendationResponse> = emptyList()) {
    fun toDomain() = NextOrders(freeAt = freeAt?.let(Instant::parse), items = items.map(RecommendationResponse::toDomain))
}

@Serializable
data class RecommendationResponse(
    val order: OrderResponse,
    val emptyMeters: Long,
    val fuelCost: Long,
    val netProfit: Long,
    val longWait: Boolean = false,
) {
    fun toDomain() = NextOrder(
        order = order.toDomain(),
        emptyMeters = emptyMeters,
        fuelCost = fuelCost,
        netProfit = netProfit,
        longWait = longWait,
    )
}

@Serializable
data class AnswerRecommendationRequest(val answer: String? = null)

@Serializable
data class RecommendationAnswerResponse(val orderId: Long, val answer: String? = null, val dispatchRequest: DispatchRequestResponse? = null)
