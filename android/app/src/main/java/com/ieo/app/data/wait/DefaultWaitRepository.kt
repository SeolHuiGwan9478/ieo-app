package com.ieo.app.data.wait

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import java.time.Instant

/** [publicBaseUrl]: 주선사가 여는 공개 링크의 앞부분. 지금은 API 서버가 직접 보여 준다 */
class DefaultWaitRepository(
    private val api: WaitApi,
    private val caller: ApiCaller,
    private val publicBaseUrl: String,
) : WaitRepository {

    override suspend fun waits(tripId: Long): ApiResult<List<WaitRecord>> =
        caller.call { api.waits(tripId) }.mapResponse { records -> records.map(WaitRecordResponse::toDomain) }

    override suspend fun arrive(tripId: Long, stop: StopType, at: Instant?): ApiResult<WaitRecord> =
        caller.call { api.arrive(tripId, stop.name, WaitTimeRequest(at?.toString())) }.mapResponse { it.toDomain() }

    override suspend fun startWork(tripId: Long, stop: StopType): ApiResult<WaitRecord> =
        caller.call { api.startWork(tripId, stop.name) }.mapResponse { it.toDomain() }

    override suspend fun depart(tripId: Long, stop: StopType): ApiResult<WaitRecord> =
        caller.call { api.depart(tripId, stop.name) }.mapResponse { it.toDomain() }

    override suspend fun createProof(tripId: Long, stop: StopType): ApiResult<WaitProofLink> =
        caller.call { api.createProof(tripId, stop.name) }.mapResponse { it.toDomain(publicBaseUrl) }
}
