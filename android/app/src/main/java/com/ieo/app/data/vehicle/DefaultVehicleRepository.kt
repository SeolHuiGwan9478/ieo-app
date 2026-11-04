package com.ieo.app.data.vehicle

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleRepository

class DefaultVehicleRepository(
    private val api: VehicleApi,
    private val caller: ApiCaller,
) : VehicleRepository {

    override suspend fun vehicles(): ApiResult<List<Vehicle>> =
        caller.call { api.vehicles() }.mapResponse { list -> list.map(VehicleResponse::toDomain) }

    override suspend fun update(vehicleId: Long, tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?): ApiResult<Vehicle> =
        caller.call { api.update(vehicleId, UpdateVehicleRequest(tonnage.name, bodyType.name, heightMeters)) }
            .mapResponse { it.toDomain() }

    override suspend fun setDefault(vehicleId: Long): ApiResult<Vehicle> =
        caller.call { api.setDefault(vehicleId) }.mapResponse { it.toDomain() }
}
