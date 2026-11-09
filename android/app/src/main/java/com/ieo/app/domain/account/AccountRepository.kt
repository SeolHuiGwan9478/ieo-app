package com.ieo.app.domain.account

import com.ieo.app.core.network.ApiResult

interface AccountRepository {

    suspend fun me(): ApiResult<Profile>

    suspend fun updateName(name: String): ApiResult<Profile>

    /** 서버의 로그인된 차량 목록에서 이 차를 뺀다 */
    suspend fun endCarSession(): ApiResult<Unit> = ApiResult.Success(Unit)
}
