package com.ieo.app.data.wait

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.wait.WaitMark
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

    override suspend fun arrive(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> =
        caller.call { api.arrive(tripId, stop.name, request(at, mark)) }.mapResponse { it.toDomain() }

    override suspend fun startWork(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> =
        caller.call { api.startWork(tripId, stop.name, request(at, mark)) }.mapResponse { it.toDomain() }

    override suspend fun depart(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> =
        caller.call { api.depart(tripId, stop.name, request(at, mark)) }.mapResponse { it.toDomain() }

    override suspend fun sendProof(tripId: Long): ApiResult<WaitProofLink> =
        caller.call { api.sendProof(tripId) }.mapResponse { it.toDomain(publicBaseUrl) }

    override suspend fun createProof(tripId: Long): ApiResult<WaitProofLink> =
        caller.call { api.createProof(tripId) }.mapResponse { it.toDomain(publicBaseUrl) }

    private fun request(at: Instant?, mark: WaitMark) = WaitTimeRequest(
        at = at?.toString(),
        latitude = mark.location?.latitude,
        longitude = mark.location?.longitude,
        source = mark.source.name,
    )
}
