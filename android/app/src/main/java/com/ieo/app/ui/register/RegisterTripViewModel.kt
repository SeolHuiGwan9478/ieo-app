package com.ieo.app.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.navi.NaviPlace
import com.ieo.app.domain.navi.NaviPlaceSource
import com.ieo.app.domain.trip.NewTrip
import com.ieo.app.domain.trip.StopChoice
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 고를 수 있는 상하차지 한 곳 */
data class PlaceOption(val key: String, val title: String, val address: String, val source: String, val choice: StopChoice)

enum class PlannedDay(val label: String) { UNKNOWN("미정"), TODAY("오늘"), TOMORROW("내일") }

data class RegisterTripUiState(
    val pickupOptions: List<PlaceOption> = emptyList(),
    val dropoffOptions: List<PlaceOption> = emptyList(),
    val pickup: PlaceOption? = null,
    val dropoff: PlaceOption? = null,
    val day: PlannedDay = PlannedDay.TODAY,
    val time: LocalTime = LocalTime.of(9, 0),
    val paymentType: PaymentType = PaymentType.RECEIPT,
    /** 전에 같이 일한 주선사 */
    val brokerOptions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    /** 등록을 마쳤다. 창을 닫고 홈을 새로 받는다 ([RegisterTripViewModel.doneHandled]) */
    val done: Boolean = false,
)

interface RegisterTripActions {
    fun selectPickup(key: String)
    fun selectDropoff(key: String)
    fun selectDay(day: PlannedDay)
    fun shiftTime(minutes: Long)
    fun selectPayment(type: PaymentType)
    fun submit(fare: Long, brokerName: String?)
}

/**
 * 다른 앱에서 받은 배차 등록 (TRIP-001). 상하차지는 키보드 없이 고른다:
 * 지금 여기(상차지만), 다녀온 곳(내 운행 기록), 내비 즐겨찾기·최근 목적지.
 * 주소 검색은 붙이지 않았다. 처음 가는 곳은 내비에서 한 번 검색해 두면 여기 나온다.
 */
class RegisterTripViewModel(
    private val tripRepository: TripRepository,
    private val naviPlaces: NaviPlaceSource,
    private val clock: Clock = Clock.system(DayTimeFormatter.KOREA),
) : ViewModel(), RegisterTripActions {

    private val _state = MutableStateFlow(RegisterTripUiState())
    val state: StateFlow<RegisterTripUiState> = _state.asStateFlow()

    /** 창을 열 때마다 처음부터. 상차 시각은 다음 정시로 */
    fun start() {
        val nextHour = ZonedDateTime.now(clock).withZoneSameInstant(DayTimeFormatter.KOREA).toLocalTime()
            .withMinute(0).withSecond(0).withNano(0).plusHours(1)
        _state.value = RegisterTripUiState(time = nextHour, isLoading = true)
        viewModelScope.launch { load() }
    }

    override fun selectPickup(key: String) = _state.update { s -> s.copy(pickup = s.pickupOptions.firstOrNull { it.key == key }) }

    override fun selectDropoff(key: String) = _state.update { s -> s.copy(dropoff = s.dropoffOptions.firstOrNull { it.key == key }) }

    override fun selectDay(day: PlannedDay) = _state.update { it.copy(day = day) }

    override fun shiftTime(minutes: Long) = _state.update { it.copy(time = it.time.plusMinutes(minutes)) }

    override fun selectPayment(type: PaymentType) = _state.update { it.copy(paymentType = type) }

    override fun submit(fare: Long, brokerName: String?) {
        val s = _state.value
        val pickup = s.pickup ?: return show(PICKUP_REQUIRED_MESSAGE)
        val dropoff = s.dropoff ?: return show(DROPOFF_REQUIRED_MESSAGE)
        if (pickup.address == dropoff.address) return show(SAME_STOP_MESSAGE)
        if (fare <= 0) return show(FARE_REQUIRED_MESSAGE)
        if (s.isBusy) return
        val trip = NewTrip(
            pickup = pickup.choice,
            dropoff = dropoff.choice,
            pickupAt = plannedAt(s.day, s.time),
            fare = fare,
            paymentType = s.paymentType,
            brokerName = brokerName?.trim()?.ifEmpty { null },
        )
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = tripRepository.register(trip)) {
                is ApiResult.Success -> _state.update { it.copy(isBusy = false, done = true, message = REGISTERED_MESSAGE) }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, message = result.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun doneHandled() = _state.update { it.copy(done = false) }

    private fun plannedAt(day: PlannedDay, time: LocalTime): Instant? {
        val today = ZonedDateTime.now(clock).withZoneSameInstant(DayTimeFormatter.KOREA).toLocalDate()
        val date = when (day) {
            PlannedDay.UNKNOWN -> return null
            PlannedDay.TODAY -> today
            PlannedDay.TOMORROW -> today.plusDays(1)
        }
        return date.atTime(time).atZone(DayTimeFormatter.KOREA).toInstant()
    }

    /** 내 운행 기록과 내비를 함께 묻는다. 하나가 안 돼도 나머지로 고를 수 있다 */
    private suspend fun load() {
        val trips = viewModelScope.async { tripRepository.myTrips() }
        val saved = viewModelScope.async { naviPlaces.savedPlaces() }
        val here = viewModelScope.async { naviPlaces.currentPlace() }
        val myTrips = (trips.await() as? ApiResult.Success)?.data.orEmpty()
        val visited = visitedPlaces(myTrips)
        val navi = saved.await().map(::naviOption).filterNot { option -> visited.any { it.address == option.address } }
        val current = here.await()?.let(::naviOption)
        val pickups = (listOfNotNull(current) + visited + navi).distinctBy { it.key }.take(MAX_OPTIONS)
        val dropoffs = (visited + navi).distinctBy { it.key }.take(MAX_OPTIONS)
        _state.update {
            it.copy(
                pickupOptions = pickups,
                dropoffOptions = dropoffs,
                // 지금 상차지에 와서 등록하는 일이 많다
                pickup = current,
                brokerOptions = myTrips.mapNotNull(Trip::brokerName).distinct().take(MAX_BROKERS),
                isLoading = false,
            )
        }
    }

    /** 최근 운행부터. 같은 상하차지는 한 번만 */
    private fun visitedPlaces(trips: List<Trip>): List<PlaceOption> = trips
        .flatMap { listOf(it.pickup, it.dropoff) }
        .distinctBy { it.siteId }
        .map { stop ->
            PlaceOption("site-${stop.siteId}", stop.name, stop.address, VISITED, StopChoice.Known(stop.siteId, stop.name, stop.address))
        }

    private fun naviOption(place: NaviPlace) = PlaceOption(
        key = "${place.kind}-${place.latitude}-${place.longitude}",
        title = if (place.kind == NaviPlace.Kind.CURRENT) HERE else place.name,
        address = place.address,
        source = when (place.kind) {
            NaviPlace.Kind.BOOKMARK -> "내비 즐겨찾기"
            NaviPlace.Kind.RECENT -> "내비 최근 목적지"
            NaviPlace.Kind.CURRENT -> "지금 차 위치"
        },
        choice = StopChoice.New(place.name, place.address, place.latitude, place.longitude),
    )

    private fun show(message: String) = _state.update { it.copy(message = message) }

    companion object {
        const val REGISTERED_MESSAGE = "내 배차에 넣었어요"
        const val PICKUP_REQUIRED_MESSAGE = "상차지를 골라 주세요"
        const val DROPOFF_REQUIRED_MESSAGE = "하차지를 골라 주세요"
        const val SAME_STOP_MESSAGE = "상차지와 하차지가 같아요"
        const val FARE_REQUIRED_MESSAGE = "운임을 입력해 주세요"
        private const val VISITED = "다녀온 곳"
        private const val HERE = "지금 여기"
        private const val MAX_OPTIONS = 12
        private const val MAX_BROKERS = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RegisterTripViewModel(container.tripRepository, container.naviPlaces)
            }
        }
    }
}
