package com.ieo.app.data.vehicle

import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
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
    )
}

/** heightM이 null이면 JSON에서 빠지고, 서버는 빠진 높이를 "비움"으로 받는다 */
@Serializable
data class UpdateVehicleRequest(val tonnage: String, val bodyType: String, val heightM: Double?)
