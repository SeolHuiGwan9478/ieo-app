package com.ieo.app.domain.account

import com.ieo.app.core.network.ApiResult

interface AccountRepository {

    suspend fun me(): ApiResult<Profile>

    suspend fun updateName(name: String): ApiResult<Profile>
}
