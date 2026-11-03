package com.ieo.app.ui.home

import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStop
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock

/**
 * 서버 운행([Trip])을 홈 화면의 "내 배차" 카드([AssignedTrip])로 바꾼다.
 * [clock]을 바꿔 끼우면 테스트에서 날짜를 고정할 수 있다.
 */
class AssignedTripMapper(clock: Clock = Clock.system(DayTimeFormatter.KOREA)) {

    private val labels = DayTimeFormatter(clock)

    fun map(trip: Trip) = AssignedTrip(
        id = trip.id.toString(),
        pickup = trip.pickup.toPlace(),
        dropoff = trip.dropoff.toPlace(),
        pickupDay = trip.pickup.plannedAt?.let(labels::day) ?: UNSCHEDULED_DAY,
        pickupTime = trip.pickup.plannedAt?.let(labels::time) ?: UNSCHEDULED_TIME,
        vehicle = trip.plateNumber,
        fare = trip.fare,
        paymentType = trip.paymentTypeLabel,
        brokerName = trip.brokerName ?: NO_BROKER,
    )

    private fun TripStop.toPlace() = Place(name = name, address = address, latitude = latitude, longitude = longitude)

    private companion object {
        const val UNSCHEDULED_DAY = "시간"
        const val UNSCHEDULED_TIME = "미정"
        const val NO_BROKER = "직접 등록"
    }
}
