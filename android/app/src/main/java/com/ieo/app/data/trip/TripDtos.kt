package com.ieo.app.data.trip

import com.ieo.app.domain.trip.LocationSample
import com.ieo.app.domain.trip.NewTrip
import com.ieo.app.domain.trip.StopChoice
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

/** 다른 앱에서 받은 배차 등록 (서버 CreateTripRequest). 차량을 비우면 기본 차량 */
@Serializable
data class CreateTripRequest(
    val pickup: TripStopRequest,
    val dropoff: TripStopRequest,
    val fare: Long,
    val paymentType: String,
    val brokerName: String? = null,
) {
    companion object {
        fun from(trip: NewTrip) = CreateTripRequest(
            pickup = TripStopRequest.from(trip.pickup, trip.pickupAt),
            dropoff = TripStopRequest.from(trip.dropoff, plannedAt = null),
            fare = trip.fare,
            paymentType = trip.paymentType.name,
            brokerName = trip.brokerName,
        )
    }
}

/** 이미 있는 상하차지면 siteId, 처음 가는 곳이면 site */
@Serializable
data class TripStopRequest(
    val siteId: Long? = null,
    val site: NewSiteRequest? = null,
    val plannedAt: String? = null,
) {
    companion object {
        fun from(choice: StopChoice, plannedAt: Instant?) = when (choice) {
            is StopChoice.Known -> TripStopRequest(siteId = choice.siteId, plannedAt = plannedAt?.toString())
            is StopChoice.New -> TripStopRequest(
                site = NewSiteRequest(choice.name, choice.address, choice.latitude, choice.longitude),
                plannedAt = plannedAt?.toString(),
            )
        }
    }
}

@Serializable
data class NewSiteRequest(val name: String, val address: String, val lat: Double, val lng: Double)

/** 위치 묶음 (서버 TripLocationRequest) */
@Serializable
data class TripLocationRequest(val points: List<Point>) {

    @Serializable
    data class Point(
        val recordedAt: String,
        val lat: Double,
        val lng: Double,
        val remainMeters: Int? = null,
        val remainSeconds: Int? = null,
    )

    companion object {
        fun from(samples: List<LocationSample>) = TripLocationRequest(
            samples.map {
                Point(it.recordedAt.toString(), it.point.latitude, it.point.longitude, it.remainMeters, it.remainSeconds)
            },
        )
    }
}
