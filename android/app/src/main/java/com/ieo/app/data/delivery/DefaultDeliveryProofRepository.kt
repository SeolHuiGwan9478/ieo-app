package com.ieo.app.data.delivery

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofRepository

/** [publicBaseUrl]: 수하인이 여는 링크의 앞부분. 지금은 API 서버가 직접 보여 준다 */
class DefaultDeliveryProofRepository(
    private val api: DeliveryProofApi,
    private val caller: ApiCaller,
    private val publicBaseUrl: String,
) : DeliveryProofRepository {

    override suspend fun issue(tripId: Long): ApiResult<DeliveryProof> =
        caller.call { api.issue(tripId) }.mapResponse { it.toDomain(publicBaseUrl) }

    override suspend fun get(tripId: Long): ApiResult<DeliveryProof> =
        caller.call { api.get(tripId) }.mapResponse { it.toDomain(publicBaseUrl) }
}
