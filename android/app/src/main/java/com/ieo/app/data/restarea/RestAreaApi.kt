package com.ieo.app.data.restarea

import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.restarea.RestStopKind
import com.ieo.app.domain.restarea.RouteRestStops
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RestAreaApi {

    /** 운행 경로 위 쉴 곳 (TRIP-032) */
    @GET("api/trips/{tripId}/rest-areas")
    suspend fun onRoute(
        @Path("tripId") tripId: Long,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
    ): Response<RouteRestAreaListResponse>
}

// 서버 domain/restarea/dto와 같은 모양

@Serializable
data class RouteRestAreaListResponse(
    val routed: Boolean = false,
    val items: List<RouteRestAreaResponse> = emptyList(),
    val attribution: String = "",
) {
    fun toDomain() = RouteRestStops(routed, items.mapNotNull(RouteRestAreaResponse::toDomain), attribution)
}

@Serializable
data class RouteRestAreaResponse(
    val kind: String,
    val name: String,
    val direction: String? = null,
    val aheadMeters: Int,
    val facilities: List<String> = emptyList(),
    val phone: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** 모르는 종류는 버린다 (서버가 먼저 새 종류를 보낼 때) */
    fun toDomain(): RestStop? {
        val kind = RestStopKind.entries.firstOrNull { it.name == kind } ?: return null
        return RestStop(kind, name, direction, aheadMeters, facilities, phone, latitude, longitude)
    }
}
