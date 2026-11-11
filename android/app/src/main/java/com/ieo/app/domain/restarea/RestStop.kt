package com.ieo.app.domain.restarea

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.vehicle.GeoPoint
import java.time.Instant

enum class RestStopKind {
    /** 수면실·샤워실·세탁실이 있는 휴게소 (화물차 라운지) */
    LOUNGE,

    /** 졸음쉼터. TMAP 경로에서 온다 */
    DROWSY_SHELTER,
}

/** 경로 위 쉴 곳 하나 (TRIP-032). [aheadMeters]는 받은 때 위치에서 경로를 따라간 거리 */
data class RestStop(
    val kind: RestStopKind,
    val name: String,
    val direction: String?,
    val aheadMeters: Int,
    val facilities: List<String>,
    val phone: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val isLounge: Boolean get() = kind == RestStopKind.LOUNGE
}

/**
 * @param routed      경로를 받았다. false면 [stops]가 비어 있다
 * @param attribution 화면에 함께 적을 출처
 */
data class RouteRestStops(val routed: Boolean, val stops: List<RestStop>, val attribution: String)

/** 휴게소 (docs/design/08_휴게소.md) */
interface RestAreaRepository {

    /** 지금 위치에서 그 운행의 이번 목적지까지 경로 위 쉴 곳, 경로 거리순. 2km보다 가까운 곳은 없다 */
    suspend fun onRoute(tripId: Long, here: GeoPoint): ApiResult<RouteRestStops>

    /** 저장소 없이 쓸 때 (미리보기·테스트) */
    object None : RestAreaRepository {
        override suspend fun onRoute(tripId: Long, here: GeoPoint): ApiResult<RouteRestStops> =
            ApiResult.Success(RouteRestStops(routed = false, stops = emptyList(), attribution = ""))
    }
}

/**
 * 받은 때의 경로 위 쉴 곳. 다시 받지 않고도 남은 거리를 고칠 수 있게 받은 때의 내비 남은 거리를 함께 둔다:
 * 그 뒤 줄어든 내비 남은 거리만큼 경로를 따라 온 것이다. 2km 안으로 들어온 곳은 진입로를 지났다고 보고 뺀다.
 */
data class RouteRestStopsAt(
    val tripId: Long,
    val stops: List<RestStop>,
    val attribution: String,
    val fetchedAt: Instant,
    val remainMetersAtFetch: Int?,
) {
    fun current(remainMetersNow: Int?): List<RestStop> {
        val traveled = if (remainMetersAtFetch != null && remainMetersNow != null) {
            (remainMetersAtFetch - remainMetersNow).coerceAtLeast(0)
        } else {
            0
        }
        return stops.map { it.copy(aheadMeters = it.aheadMeters - traveled) }.filter { it.aheadMeters >= MIN_AHEAD_METERS }
    }

    companion object {
        const val MIN_AHEAD_METERS = 2_000
    }
}
