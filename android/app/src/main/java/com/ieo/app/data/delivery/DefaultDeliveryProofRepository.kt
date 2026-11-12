package com.ieo.app.data.delivery

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultDeliveryProofRepository(
    private val api: DeliveryProofApi,
    private val caller: ApiCaller,
) : DeliveryProofRepository {

    override suspend fun get(tripId: Long): ApiResult<DeliveryProof> =
        caller.call { api.get(tripId) }.mapResponse { it.toDomain() }

    override suspend fun photo(tripId: Long, photoId: Long): ApiResult<ByteArray> =
        caller.call { api.photo(tripId, photoId) }.mapResponse { body -> withContext(Dispatchers.IO) { body.use { it.bytes() } } }
}
