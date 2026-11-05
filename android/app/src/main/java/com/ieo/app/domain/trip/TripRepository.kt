package com.ieo.app.domain.trip

import com.ieo.app.core.network.ApiResult

interface TripRepository {

    /** 최근 운행 (최신 등록 순) */
    suspend fun myTrips(): ApiResult<List<Trip>>

    /** 출발해서 진행 중인 운행. 없으면 null */
    suspend fun currentTrip(): ApiResult<Trip?>

    /** 출발 (예약 → 상차지로 이동 중). 진행 중인 운행이 이미 있으면 서버가 거절한다 */
    suspend fun start(tripId: Long): ApiResult<Trip>

    /** 다른 앱에서 받은 배차 등록. 내 배차에 예약 상태로 들어간다 */
    suspend fun register(trip: NewTrip): ApiResult<Trip>

    /** 위치 묶음 보내기 (TRIP-013). 끝난 운행이면 서버가 받기만 하고 버린다 */
    suspend fun sendLocations(tripId: Long, samples: List<LocationSample>): ApiResult<Unit>
}
