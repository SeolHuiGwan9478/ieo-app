package com.ieo.app.data.vehicle

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.PleosMatch
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleDimensions
import com.ieo.app.domain.vehicle.VehicleRepository

class DefaultVehicleRepository(
    private val api: VehicleApi,
    private val caller: ApiCaller,
) : VehicleRepository {

    override suspend fun vehicles(): ApiResult<List<Vehicle>> =
        caller.call { api.vehicles() }.mapResponse { list -> list.map(VehicleResponse::toDomain) }

    override suspend fun update(
        vehicleId: Long,
        tonnage: Tonnage,
        bodyType: BodyType,
        heightMeters: Double?,
        dimensions: VehicleDimensions,
    ): ApiResult<Vehicle> =
        caller.call {
            api.update(
                vehicleId,
                UpdateVehicleRequest(tonnage.name, bodyType.name, heightMeters, dimensions.widthCm, dimensions.lengthCm, dimensions.totalWeightKg, dimensions.axleCount),
            )
        }
            .mapResponse { it.toDomain() }

    override suspend fun resolvePleos(vin: String): ApiResult<PleosMatch> =
        caller.call { api.resolvePleos(PleosVinRequest(vin)) }.mapResponse { it.toDomain() }

    override suspend fun linkPleos(vehicleId: Long, vin: String): ApiResult<Vehicle> =
        caller.call { api.linkPleos(vehicleId, PleosVinRequest(vin)) }.mapResponse { it.toDomain() }

    override suspend fun reportFuelEconomy(vehicleId: Long, litersPer100Km: Float): ApiResult<Vehicle> =
        caller.call { api.reportFuelEconomy(vehicleId, FuelEconomyRequest(litersPer100Km)) }.mapResponse { it.toDomain() }
}
