package com.ieo.app.testing

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitRepository
import com.ieo.app.domain.wait.WaitStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 서버를 흉내 내는 운행·대기 저장소. 서버처럼 도착·출발에 따라 운행 상태를 바꾼다.
 * [trips]의 첫 운행을 진행 중인 운행으로 다룬다.
 */
class FakeTripServer(var trip: Trip) : TripRepository, WaitRepository {

    val calls = mutableListOf<String>()
    private val records = mutableMapOf<StopType, WaitRecord>()

    override suspend fun myTrips() = ApiResult.Success(listOf(trip))

    override suspend fun currentTrip(): ApiResult<Trip?> =
        ApiResult.Success(trip.takeIf { it.status.isActive && it.status != TripStatus.SCHEDULED })

    override suspend fun start(tripId: Long): ApiResult<Trip> {
        calls += "start"
        trip = trip.copy(status = TripStatus.TO_PICKUP)
        return ApiResult.Success(trip)
    }

    override suspend fun waits(tripId: Long) = ApiResult.Success(records.values.toList())

    override suspend fun arrive(tripId: Long, stop: StopType): ApiResult<WaitRecord> {
        calls += "arrive $stop"
        trip = trip.copy(status = if (stop == StopType.PICKUP) TripStatus.AT_PICKUP else TripStatus.AT_DROPOFF)
        return ApiResult.Success(records.getOrPut(stop) { TripFixtures.wait(stop) })
    }

    override suspend fun startWork(tripId: Long, stop: StopType): ApiResult<WaitRecord> {
        calls += "work $stop"
        val record = records.getValue(stop).copy(status = WaitStatus.WORKING, workStartedAt = TripFixtures.NOW)
        records[stop] = record
        return ApiResult.Success(record)
    }

    override suspend fun depart(tripId: Long, stop: StopType): ApiResult<WaitRecord> {
        calls += "depart $stop"
        trip = trip.copy(status = if (stop == StopType.PICKUP) TripStatus.IN_TRANSIT else TripStatus.COMPLETED)
        val record = records.getValue(stop).copy(status = WaitStatus.DONE, departedAt = TripFixtures.NOW)
        records[stop] = record
        return ApiResult.Success(record)
    }
}

class FakeNavigator : Navigator {

    override val progress = MutableStateFlow<NaviProgress?>(null)
    val guidedTo = mutableListOf<String>()

    override fun connect() = Unit

    override fun release() = Unit

    override fun startGuidance(destination: Destination): Boolean {
        guidedTo += destination.name
        progress.value = NaviProgress(destinationName = destination.name)
        return true
    }

    override fun refresh() = Unit

    override fun stopGuidance() {
        progress.value = null
    }

    /** 내비가 "목적지 도착"을 알린 것처럼 */
    fun arrive() {
        progress.value = progress.value?.copy(arrived = true, remainMeters = 0, remainSeconds = 0)
    }
}
