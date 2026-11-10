package com.ieo.app.ui.settings

import com.ieo.app.domain.account.DriverStanding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.BuildConfig
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.session.SessionManager
import com.ieo.app.di.appContainer
import com.ieo.app.domain.account.AccountRepository
import com.ieo.app.domain.account.Profile
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleDimensions
import com.ieo.app.domain.vehicle.VehicleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 아래에서 올라오는 창 */
sealed interface SettingsForm {
    data class EditName(val current: String) : SettingsForm
    data class EditVehicle(val vehicle: Vehicle) : SettingsForm
    data class EditRecommendation(val current: RecommendationPreference) : SettingsForm
    data object ConfirmSignOut : SettingsForm
}

data class SettingsUiState(
    val profile: Profile? = null,
    /** 기본 차량이 맨 앞 */
    val vehicles: List<Vehicle> = emptyList(),
    /** 다음 배차 추천 설정. 못 받았으면 null */
    val recommendation: RecommendationPreference? = null,
    val appVersion: String = "",
    val form: SettingsForm? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    /** 서류 인증과 이행 기록. 못 받으면 null */
    val standing: DriverStanding? = null,
)

interface SettingsActions {
    fun refresh()
    fun openForm(form: SettingsForm)
    fun close()
    fun saveName(name: String)
    fun saveVehicle(tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?, dimensions: VehicleDimensions)
    fun saveRecommendation(preference: RecommendationPreference)
    fun signOut()
}

/**
 * 설정 탭 (ACC-003, VEH-001·002, REC-004·025): 내 정보, 내 차량, 다음 배차 추천, 로그아웃.
 * 차량 추가·삭제와 탈퇴는 휴대폰 앱에서 한다. 차량 화면에서 긴 입력을 하지 않게.
 */
class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val vehicleRepository: VehicleRepository,
    private val recommendationRepository: RecommendationRepository,
    private val session: SessionManager,
    appVersion: String,
) : ViewModel(), SettingsActions {

    private val _state = MutableStateFlow(SettingsUiState(appVersion = appVersion))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    override fun refresh() {
        viewModelScope.launch { load() }
    }

    override fun openForm(form: SettingsForm) = _state.update { it.copy(form = form) }

    override fun close() = _state.update { it.copy(form = null) }

    override fun saveName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return show(NAME_REQUIRED_MESSAGE)
        runAction {
            val profile = accountRepository.updateName(trimmed).orShow() ?: return@runAction
            _state.update { it.copy(profile = profile, form = null, message = "이름을 바꿨어요") }
        }
    }

    override fun saveVehicle(tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?, dimensions: VehicleDimensions) {
        val vehicle = (_state.value.form as? SettingsForm.EditVehicle)?.vehicle ?: return
        if (heightMeters != null && heightMeters !in Vehicle.MIN_HEIGHT_METERS..Vehicle.MAX_HEIGHT_METERS) {
            return show(HEIGHT_RANGE_MESSAGE)
        }
        runAction {
            vehicleRepository.update(vehicle.id, tonnage, bodyType, heightMeters, dimensions).orShow() ?: return@runAction
            _state.update { it.copy(form = null, message = "${vehicle.plateNumber} 차량 정보를 고쳤어요") }
            load()
        }
    }

    override fun saveRecommendation(preference: RecommendationPreference) {
        if (preference.radiusKm !in RecommendationPreference.MIN_RADIUS_KM..RecommendationPreference.MAX_RADIUS_KM) {
            return show(RADIUS_RANGE_MESSAGE)
        }
        runAction {
            val saved = recommendationRepository.changePreference(preference).orShow() ?: return@runAction
            _state.update { it.copy(recommendation = saved, form = null, message = RECOMMENDATION_SAVED_MESSAGE) }
        }
    }

    /** 서버 목록에서 이 차를 빼고 토큰을 지운다. 로그인 화면으로는 SessionManager를 따라 넘어간다 */
    override fun signOut() {
        _state.update { it.copy(form = null) }
        viewModelScope.launch {
            // 서버에 못 알려도 이 차에서는 로그아웃한다
            withTimeoutOrNull(SIGN_OUT_WAIT_MS) { accountRepository.endCarSession() }
            session.signOut()
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        val profile = viewModelScope.async { accountRepository.me() }
        val vehicles = viewModelScope.async { vehicleRepository.vehicles() }
        val recommendation = viewModelScope.async { recommendationRepository.preference() }
        val standing = viewModelScope.async { accountRepository.standing() }
        profile.await().orShow()?.let { me -> _state.update { it.copy(profile = me) } }
        vehicles.await().orShow()?.let { list ->
            _state.update { it.copy(vehicles = list.sortedByDescending(Vehicle::isDefault)) }
        }
        // 추천 설정을 못 받아도 다른 칸은 보여 준다. 메시지를 두 번 띄우지 않게 조용히 넘긴다
        (recommendation.await() as? ApiResult.Success)?.data?.let { pref -> _state.update { it.copy(recommendation = pref) } }
        standing.await()?.let { value -> _state.update { it.copy(standing = value) } }
        _state.update { it.copy(isLoading = false) }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun <T> ApiResult<T>.orShow(): T? = when (this) {
        is ApiResult.Success -> data
        is ApiResult.Failure -> {
            show(error.message)
            null
        }
    }

    private fun show(message: String) = _state.update { it.copy(message = message) }

    companion object {
        private const val SIGN_OUT_WAIT_MS = 2_000L
        const val NAME_REQUIRED_MESSAGE = "이름을 입력해 주세요"
        const val HEIGHT_RANGE_MESSAGE = "높이는 1.5m에서 4.2m 사이로 정해 주세요"
        const val RADIUS_RANGE_MESSAGE = "반경은 5km에서 100km 사이로 정해 주세요"
        const val RECOMMENDATION_SAVED_MESSAGE = "추천 설정을 바꿨어요"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(
                    accountRepository = container.accountRepository,
                    vehicleRepository = container.vehicleRepository,
                    recommendationRepository = container.recommendationRepository,
                    session = container.session,
                    appVersion = BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
