package com.ieo.app.data.site

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.site.SiteRepository

class DefaultSiteRepository(
    private val api: SiteApi,
    private val caller: ApiCaller,
) : SiteRepository {

    override suspend fun card(siteId: Long): ApiResult<SiteCard> =
        caller.call { api.card(siteId) }.mapResponse { it.toDomain() }
}
