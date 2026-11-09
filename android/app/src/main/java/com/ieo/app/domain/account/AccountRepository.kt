package com.ieo.app.domain.account

import com.ieo.app.core.network.ApiResult

interface AccountRepository {

    suspend fun me(): ApiResult<Profile>

    suspend fun updateName(name: String): ApiResult<Profile>

    /** 음성 데이터 처리에 동의했는지. 확인하지 못하면 false */
    suspend fun voiceConsent(): Boolean = false

    /** 서버의 로그인된 차량 목록에서 이 차를 뺀다 */
    suspend fun endCarSession(): ApiResult<Unit> = ApiResult.Success(Unit)
}
