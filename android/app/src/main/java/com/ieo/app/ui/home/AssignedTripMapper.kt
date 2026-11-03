package com.ieo.app.ui.home

import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStop
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 서버 운행([Trip])을 홈 화면의 "내 배차" 카드([AssignedTrip])로 바꾼다.
 * 날짜·시각 표기("오늘 15:00")를 여기서 정한다. [clock]을 바꿔 끼우면 테스트에서 날짜를 고정할 수 있다.
 */
class AssignedTripMapper(
    private val clock: Clock = Clock.system(KOREA),
) {

    fun map(trip: Trip) = AssignedTrip(
        id = trip.id.toString(),
        pickup = trip.pickup.toPlace(),
        dropoff = trip.dropoff.toPlace(),
        pickupDay = trip.pickup.plannedAt?.let(::dayLabel) ?: UNSCHEDULED_DAY,
        pickupTime = trip.pickup.plannedAt?.let(::timeLabel) ?: UNSCHEDULED_TIME,
        vehicle = trip.plateNumber,
        fare = trip.fare,
        paymentType = trip.paymentTypeLabel,
        brokerName = trip.brokerName ?: NO_BROKER,
    )

    /** "오늘", "내일", 그 밖은 "11월 5일" */
    private fun dayLabel(at: Instant): String {
        val day = at.atZone(KOREA).toLocalDate()
        val today = clock.instant().atZone(KOREA).toLocalDate()
        return when (day) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            today.minusDays(1) -> "어제"
            else -> day.format(DAY_FORMAT)
        }
    }

    private fun timeLabel(at: Instant): String = at.atZone(KOREA).format(TIME_FORMAT)

    private fun TripStop.toPlace() = Place(name = name, address = address, latitude = latitude, longitude = longitude)

    private companion object {
        val KOREA: ZoneId = ZoneId.of("Asia/Seoul")
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val UNSCHEDULED_DAY = "시간"
        const val UNSCHEDULED_TIME = "미정"
        const val NO_BROKER = "직접 등록"
    }
}
