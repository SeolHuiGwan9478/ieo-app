package com.ieo.app.data.wait

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository

class DefaultWaitRepository(
    private val api: WaitApi,
    private val caller: ApiCaller,
) : WaitRepository {

    override suspend fun waits(tripId: Long): ApiResult<List<WaitRecord>> =
        caller.call { api.waits(tripId) }.mapResponse { records -> records.map(WaitRecordResponse::toDomain) }

    override suspend fun arrive(tripId: Long, stop: StopType): ApiResult<WaitRecord> =
        caller.call { api.arrive(tripId, stop.name) }.mapResponse { it.toDomain() }

    override suspend fun startWork(tripId: Long, stop: StopType): ApiResult<WaitRecord> =
        caller.call { api.startWork(tripId, stop.name) }.mapResponse { it.toDomain() }

    override suspend fun depart(tripId: Long, stop: StopType): ApiResult<WaitRecord> =
        caller.call { api.depart(tripId, stop.name) }.mapResponse { it.toDomain() }
}
