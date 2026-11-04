package com.ieo.app.data.trip

import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.trip.TripStop
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/trip/dto와 같은 모양. 쓰지 않는 필드는 뺐다 (ignoreUnknownKeys)

@Serializable
data class TripResponse(
    val id: Long,
    val status: String,
    val fare: Long,
    val paymentTypeLabel: String,
    val brokerName: String? = null,
    val plateNumber: String,
    val pickup: TripStopResponse,
    val dropoff: TripStopResponse,
) {
    fun toDomain() = Trip(
        id = id,
        status = TripStatus.valueOf(status),
        fare = fare,
        paymentTypeLabel = paymentTypeLabel,
        brokerName = brokerName,
        plateNumber = plateNumber,
        pickup = pickup.toDomain(),
        dropoff = dropoff.toDomain(),
    )
}

@Serializable
data class TripStopResponse(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val geofenceRadiusM: Int,
    val plannedAt: String? = null,
    val siteId: Long,
) {
    fun toDomain() = TripStop(
        name = name,
        address = address,
        latitude = lat,
        longitude = lng,
        geofenceRadiusMeters = geofenceRadiusM,
        plannedAt = plannedAt?.let(Instant::parse),
        siteId = siteId,
    )
}
