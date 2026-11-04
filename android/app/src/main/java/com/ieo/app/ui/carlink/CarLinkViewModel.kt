package com.ieo.app.ui.carlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.ui.common.josa
import com.ieo.app.domain.vehicle.CarIdentity
import com.ieo.app.domain.vehicle.PleosMatch
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 고를 차량 한 줄 */
data class VehicleChoice(val id: Long, val plateNumber: String, val spec: String)

data class CarLinkUiState(
    /** 내 차량이 여러 대라 이 트럭이 어느 것인지 고르는 중. 아니면 null */
    val choices: List<VehicleChoice>? = null,
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 */
    val message: String? = null,
)

interface CarLinkActions {
    fun choose(vehicleId: Long)
    fun dismiss()
}

/**
 * Pleos 트럭과 내 차량 잇기 (VEH-004). 로그인해서 홈에 들어올 때마다 차대번호를 읽어 서버에 묻는다.
 * 서버가 찾은 차량이 기본 차량이 되어 배차 요청·운행 등록에 쓰인다.
 * 차대번호를 못 읽거나 서버에 닿지 않으면 조용히 넘어간다. 설정 탭의 기본 차량을 그대로 쓴다.
 */
class CarLinkViewModel(
    private val identity: CarIdentity,
    private val vehicles: VehicleRepository,
) : ViewModel(), CarLinkActions {

    private val _state = MutableStateFlow(CarLinkUiState())
    val state: StateFlow<CarLinkUiState> = _state.asStateFlow()

    private var vin: String? = null
    private var resolving = false

    fun resolve() {
        if (resolving) return
        resolving = true
        viewModelScope.launch {
            try {
                val read = vin ?: identity.vin()?.also { vin = it } ?: return@launch
                val match = (vehicles.resolvePleos(read) as? ApiResult.Success)?.data ?: return@launch
                when (match) {
                    is PleosMatch.Linked -> if (match.autoLinked) show(linkedMessage(match.vehicle))
                    is PleosMatch.Select -> _state.update { it.copy(choices = match.candidates.map(::choice)) }
                    PleosMatch.Register -> show(REGISTER_MESSAGE)
                    is PleosMatch.OtherOwner -> show(otherOwnerMessage(match.myDefault))
                }
            } finally {
                resolving = false
            }
        }
    }

    override fun choose(vehicleId: Long) {
        val read = vin ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = vehicles.linkPleos(vehicleId, read)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(choices = null) }
                    show(linkedMessage(result.data))
                }
                is ApiResult.Failure -> show(result.error.message)
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    /** "나중에": 이번에는 기본 차량으로 시작하고, 다음 로그인에 다시 묻는다 */
    override fun dismiss() = _state.update { it.copy(choices = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun show(message: String) = _state.update { it.copy(message = message) }

    private fun choice(vehicle: Vehicle) =
        VehicleChoice(vehicle.id, vehicle.plateNumber, "${vehicle.tonnage.label} ${vehicle.bodyType.label}")

    companion object {
        const val REGISTER_MESSAGE = "등록한 차량이 없어요. 휴대폰 앱에서 차량을 등록해 주세요"

        fun linkedMessage(vehicle: Vehicle) = "이 트럭을 ${vehicle.plateNumber} 차량으로 연결했어요. 설정에서 바꿀 수 있어요"

        fun otherOwnerMessage(myDefault: Vehicle?) =
            "이 트럭은 다른 차주 계정에 연결돼 있어요" + (myDefault?.let { ". 기본 차량 ${josa(it.plateNumber, "으로", "로")} 시작해요" } ?: "")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                CarLinkViewModel(container.carIdentity, container.vehicleRepository)
            }
        }
    }
}
