package com.ieo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 홈 화면에서 서버로 받아 오는 부분. 지금은 "내 배차"만 서버 값이고, 나머지는 샘플이다 */
data class HomeUiState(
    val myTrips: List<AssignedTrip> = emptyList(),
    val isLoading: Boolean = false,
    /** 한 번 보여 주고 지운다 ([HomeViewModel.errorShown]) */
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val tripRepository: TripRepository,
    private val mapper: AssignedTripMapper = AssignedTripMapper(),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = tripRepository.myTrips()) {
                is ApiResult.Success -> _state.update {
                    it.copy(myTrips = upcoming(result.data).map(mapper::map), isLoading = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(errorMessage = null) }

    /** 끝나지 않은 운행을 상차 시각이 이른 순으로. 시각이 없는 운행은 뒤로 */
    private fun upcoming(trips: List<Trip>): List<Trip> = trips
        .filter { it.status.isActive }
        .sortedBy { it.pickup.plannedAt ?: Instant.MAX }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().tripRepository) }
        }
    }
}
