package com.ieo.app.data.vehicle

import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.PleosMatch
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleDimensions
import kotlinx.serialization.Serializable

// 서버 domain/vehicle/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class VehicleResponse(
    val id: Long,
    val plateNumber: String,
    val tonnage: String,
    val bodyType: String,
    val heightM: Double? = null,
    val measuredKmPerLiter: Double? = null,
    val pleosLinked: Boolean = false,
    val defaultVehicle: Boolean = false,
    val widthCm: Int? = null,
    val lengthCm: Int? = null,
    val totalWeightKg: Int? = null,
    val axleCount: Int? = null,
) {
    fun toDomain() = Vehicle(
        id = id,
        plateNumber = plateNumber,
        tonnage = Tonnage.valueOf(tonnage),
        bodyType = BodyType.valueOf(bodyType),
        heightMeters = heightM,
        measuredKmPerLiter = measuredKmPerLiter,
        pleosLinked = pleosLinked,
        isDefault = defaultVehicle,
        dimensions = VehicleDimensions(widthCm, lengthCm, totalWeightKg, axleCount),
    )
}

/** heightM이 null이면 JSON에서 빠지고, 서버는 빠진 높이를 "비움"으로 받는다 */
@Serializable
data class UpdateVehicleRequest(
    val tonnage: String,
    val bodyType: String,
    val heightM: Double?,
    val widthCm: Int? = null,
    val lengthCm: Int? = null,
    val totalWeightKg: Int? = null,
    val axleCount: Int? = null,
)

@Serializable
data class PleosVinRequest(val vin: String)

/** Pleos getCumulativeFuelEconomy() 값 그대로. 단위 L/100km */
data class FuelEconomyRequest(val litersPer100Km: Float)

/** status: LINKED, SELECT, REGISTER, OTHER_OWNER */
@Serializable
data class PleosResolveResponse(
    val status: String,
    val autoLinked: Boolean = false,
    val vehicle: VehicleResponse? = null,
    val candidates: List<VehicleResponse> = emptyList(),
) {
    fun toDomain(): PleosMatch = when (status) {
        "LINKED" -> vehicle?.let { PleosMatch.Linked(it.toDomain(), autoLinked) } ?: PleosMatch.OtherOwner(null)
        "SELECT" -> PleosMatch.Select(candidates.map(VehicleResponse::toDomain))
        "REGISTER" -> PleosMatch.Register
        else -> PleosMatch.OtherOwner(vehicle?.toDomain())
    }
}
