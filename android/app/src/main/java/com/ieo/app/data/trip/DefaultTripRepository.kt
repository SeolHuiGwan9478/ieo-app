package com.ieo.app.data.trip

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.trip.LocationSample
import com.ieo.app.domain.trip.NewTrip
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository

class DefaultTripRepository(
    private val api: TripApi,
    private val caller: ApiCaller,
) : TripRepository {

    override suspend fun myTrips(): ApiResult<List<Trip>> =
        caller.call { api.myTrips() }.mapResponse { trips -> trips.map(TripResponse::toDomain) }

    override suspend fun currentTrip(): ApiResult<Trip?> =
        caller.callOptional { api.currentTrip() }.mapResponse { it?.toDomain() }

    override suspend fun start(tripId: Long): ApiResult<Trip> =
        caller.call { api.start(tripId) }.mapResponse { it.toDomain() }

    override suspend fun register(trip: NewTrip): ApiResult<Trip> =
        caller.call { api.register(CreateTripRequest.from(trip)) }.mapResponse { it.toDomain() }

    override suspend fun sendLocations(tripId: Long, samples: List<LocationSample>): ApiResult<Unit> =
        caller.callNoContent { api.sendLocations(tripId, TripLocationRequest.from(samples)) }
}
