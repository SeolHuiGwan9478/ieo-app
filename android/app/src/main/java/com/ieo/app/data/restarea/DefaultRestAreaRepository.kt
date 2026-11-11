package com.ieo.app.data.restarea

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.restarea.RestAreaRepository
import com.ieo.app.domain.restarea.RouteRestStops
import com.ieo.app.domain.vehicle.GeoPoint

class DefaultRestAreaRepository(
    private val api: RestAreaApi,
    private val caller: ApiCaller,
) : RestAreaRepository {

    override suspend fun onRoute(tripId: Long, here: GeoPoint): ApiResult<RouteRestStops> =
        caller.call { api.onRoute(tripId, here.latitude, here.longitude) }.mapResponse { it.toDomain() }
}
