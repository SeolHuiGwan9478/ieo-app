package com.ieo.app.data.link

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.link.DriverLink
import com.ieo.app.domain.link.DriverLinkRepository

class DefaultDriverLinkRepository(
    private val api: DriverLinkApi,
    private val caller: ApiCaller,
) : DriverLinkRepository {

    override suspend fun invitations(): ApiResult<List<DriverLink>> =
        caller.call { api.invitations() }.mapResponse { list -> list.map(DriverLinkResponse::toDomain) }

    override suspend fun links(): ApiResult<List<DriverLink>> =
        caller.call { api.links() }.mapResponse { list -> list.map(DriverLinkResponse::toDomain) }

}
