package com.ieo.app.data.account

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.account.AccountRepository
import com.ieo.app.domain.account.Profile

class DefaultAccountRepository(
    private val api: AccountApi,
    private val caller: ApiCaller,
) : AccountRepository {

    override suspend fun me(): ApiResult<Profile> = caller.call { api.me() }.mapResponse { it.toDomain() }

    override suspend fun updateName(name: String): ApiResult<Profile> =
        caller.call { api.update(UpdateUserRequest(name)) }.mapResponse { it.toDomain() }

    override suspend fun voiceConsent(): Boolean =
        (caller.call { api.consents() } as? ApiResult.Success)?.data?.any { it.type == "VOICE" && it.agreed } ?: false

    override suspend fun endCarSession(): ApiResult<Unit> = caller.callNoContent { api.endCarSession() }
}
