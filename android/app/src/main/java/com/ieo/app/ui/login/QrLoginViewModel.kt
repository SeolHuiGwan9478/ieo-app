package com.ieo.app.ui.login

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.auth.AuthRepository
import com.ieo.app.domain.auth.CarLoginSession
import com.ieo.app.domain.auth.CarLoginStatus
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** QR 로그인 화면의 상태 */
sealed interface QrLoginUiState {

    /** 서버에서 코드를 받는 중 */
    data object Loading : QrLoginUiState

    /** QR을 보여 주고 휴대폰 승인을 기다리는 중 */
    data class Waiting(
        val code: String,
        val qrContent: String,
        /** 만료 시각 (SystemClock.elapsedRealtime 기준) */
        val expiresAt: Long,
    ) : QrLoginUiState

    data object Expired : QrLoginUiState

    /** 휴대폰에서 거절함 */
    data object Rejected : QrLoginUiState

    /** 코드를 받지 못함 (서버 연결 실패 등) */
    data class Failed(val message: String) : QrLoginUiState
}

/** 화면이 ViewModel에 보내는 동작 */
interface QrLoginActions {
    /** QR 탭이 화면에 나타남: 승인 여부를 묻기 시작한다 */
    fun onVisible()

    /** QR 탭이 사라짐: 묻기를 멈춘다 */
    fun onHidden()

    /** "새 QR 받기" */
    fun refresh()
}

/**
 * 차량 QR 로그인: 서버에서 코드를 받아 QR로 보여 주고, 휴대폰이 승인할 때까지 서버에 묻는다.
 * 승인되면 저장소가 세션에 토큰을 넣고, 앱이 홈으로 넘어간다.
 * 화면이 보일 때만 묻도록 화면이 [onVisible]·[onHidden]을 부른다.
 */
class QrLoginViewModel(
    private val authRepository: AuthRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel(), QrLoginActions {

    private val _state = MutableStateFlow<QrLoginUiState>(QrLoginUiState.Loading)
    val state: StateFlow<QrLoginUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 지금 기다리는 세션. 화면을 잠깐 떠났다 와도 같은 코드를 이어서 쓴다 */
    private var session: CarLoginSession? = null

    override fun onVisible() = start()

    override fun onHidden() = stop()

    private fun start() {
        if (job?.isActive == true) return
        // 거절·만료 화면에서는 사용자가 "새 QR 받기"를 누를 때까지 기다린다
        if (_state.value is QrLoginUiState.Rejected || _state.value is QrLoginUiState.Expired) return
        job = viewModelScope.launch { runSession() }
    }

    private fun stop() {
        job?.cancel()
        job = null
    }

    override fun refresh() {
        stop()
        session = null
        _state.value = QrLoginUiState.Loading
        start()
    }

    private suspend fun runSession() {
        val current = session ?: openSession() ?: return
        val expiresAt = toElapsed(current)
        _state.value = QrLoginUiState.Waiting(current.code, current.qrContent, expiresAt)

        while (true) {
            delay(current.pollInterval.toMillis())
            // 서버는 승인 뒤 1분을 더 주므로, 서버가 답하지 않을 때만 이 시각으로 끝낸다
            if (elapsedRealtime() > expiresAt + GRACE_MILLIS) return finish(CarLoginStatus.EXPIRED)
            val result = authRepository.pollCarLogin(current)
            // 연결이 잠깐 끊긴 것은 다음에 다시 묻는다
            if (result is ApiResult.Success && result.data.isFinished) return finish(result.data)
        }
    }

    private suspend fun openSession(): CarLoginSession? {
        _state.value = QrLoginUiState.Loading
        return when (val result = authRepository.startCarLogin()) {
            is ApiResult.Success -> result.data.also { session = it }
            is ApiResult.Failure -> {
                _state.value = QrLoginUiState.Failed(result.error.message)
                null
            }
        }
    }

    private fun finish(status: CarLoginStatus) {
        session = null
        _state.value = when (status) {
            // 로그인 완료. 앱이 세션을 보고 홈으로 넘어가므로 화면은 그대로 둔다
            CarLoginStatus.APPROVED -> _state.value
            CarLoginStatus.REJECTED -> QrLoginUiState.Rejected
            else -> QrLoginUiState.Expired
        }
    }

    /** 서버 만료 시각을 기기 시계(elapsedRealtime) 기준으로 바꾼다. 기기 시계를 사용자가 바꿔도 남은 시간이 맞다 */
    private fun toElapsed(session: CarLoginSession): Long {
        val remaining = Duration.between(clock.instant(), session.expiresAt).toMillis().coerceAtLeast(0)
        return elapsedRealtime() + remaining
    }

    companion object {
        private const val GRACE_MILLIS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { QrLoginViewModel(appContainer().authRepository) }
        }
    }
}
