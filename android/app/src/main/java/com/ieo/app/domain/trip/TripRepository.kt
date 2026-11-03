package com.ieo.app.domain.trip

import com.ieo.app.core.network.ApiResult

interface TripRepository {

    /** 최근 운행 (최신 등록 순) */
    suspend fun myTrips(): ApiResult<List<Trip>>

    /** 출발해서 진행 중인 운행. 없으면 null */
    suspend fun currentTrip(): ApiResult<Trip?>
}
