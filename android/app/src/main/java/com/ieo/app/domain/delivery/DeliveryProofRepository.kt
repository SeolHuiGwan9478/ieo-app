package com.ieo.app.domain.delivery

import com.ieo.app.core.network.ApiResult

interface DeliveryProofRepository {

    /** "인수증 받기". 이미 만들었으면 같은 링크 */
    suspend fun issue(tripId: Long): ApiResult<DeliveryProof>

    /** 서명됐는지 확인 */
    suspend fun get(tripId: Long): ApiResult<DeliveryProof>
}
