package com.ieo.app.domain.delivery

import com.ieo.app.core.network.ApiResult

interface DeliveryProofRepository {

    suspend fun get(tripId: Long): ApiResult<DeliveryProof>

    /** 사진 파일 (JPG·PNG) */
    suspend fun photo(tripId: Long, photoId: Long): ApiResult<ByteArray>
}
