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
import com.ieo.app.domain.auth.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 휴대폰 번호 로그인 화면의 상태 */
data class PhoneLoginUiState(
    /** 숫자만 (하이픈은 화면에서 넣는다) */
    val phone: String = "",
    val code: String = "",
    /** 인증번호 만료 시각 (SystemClock.elapsedRealtime 기준). 0이면 아직 받지 않음 */
    val codeExpiresAt: Long = 0L,
    val isRequestingCode: Boolean = false,
    val isLoggingIn: Boolean = false,
    val errorMessage: String? = null,
) {
    val codeSent: Boolean get() = codeExpiresAt > 0L
    val phoneValid: Boolean get() = PhoneLoginViewModel.PHONE_PATTERN.matches(phone)
    val codeComplete: Boolean get() = code.length == PhoneLoginViewModel.CODE_LENGTH
}

/** 화면이 ViewModel에 보내는 동작. 미리보기에서는 아무것도 안 하는 구현을 넘긴다 */
interface PhoneLoginActions {
    fun onPhoneChange(input: String)
    fun onCodeChange(input: String)
    fun requestCode()
    fun login()
}

/**
 * 휴대폰 로그인: 인증번호 받기 → 확인 → 로그인.
 * 로그인에 성공하면 세션이 바뀌고, 화면 이동은 앱(MainActivity)이 세션을 보고 한다.
 * 차량 앱은 가입 화면이 없어서, 가입된 차주 계정만 로그인시킨다.
 */
class PhoneLoginViewModel(
    private val authRepository: AuthRepository,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel(), PhoneLoginActions {

    private val _state = MutableStateFlow(PhoneLoginUiState())
    val state: StateFlow<PhoneLoginUiState> = _state.asStateFlow()

    override fun onPhoneChange(input: String) {
        val digits = input.filter(Char::isDigit).take(PHONE_MAX_LENGTH)
        if (digits == _state.value.phone) return
        // 번호를 바꾸면 이전에 받은 인증번호는 쓸 수 없다
        _state.update { it.copy(phone = digits, code = "", codeExpiresAt = 0L, errorMessage = null) }
    }

    override fun onCodeChange(input: String) {
        _state.update { it.copy(code = input.filter(Char::isDigit).take(CODE_LENGTH), errorMessage = null) }
    }

    override fun requestCode() {
        val current = _state.value
        if (!current.phoneValid || current.isRequestingCode) return
        _state.update { it.copy(isRequestingCode = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.requestCode(current.phone)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isRequestingCode = false, code = "", codeExpiresAt = elapsedRealtime() + CODE_VALID_MILLIS)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isRequestingCode = false, errorMessage = result.error.message)
                }
            }
        }
    }

    override fun login() {
        val current = _state.value
        if (!current.phoneValid || !current.codeComplete || current.isLoggingIn) return
        _state.update { it.copy(isLoggingIn = true, errorMessage = null) }
        viewModelScope.launch {
            val message = verifyAndLogin(current.phone, current.code)
            _state.update { it.copy(isLoggingIn = false, errorMessage = message) }
        }
    }

    /** 성공하면 null, 실패하면 보여 줄 문장 */
    private suspend fun verifyAndLogin(phone: String, code: String): String? {
        val verification = when (val result = authRepository.verifyCode(phone, code)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result.error.message
        }
        val account = verification.existingAccount ?: return NO_ACCOUNT_MESSAGE
        if (account.role != UserRole.DRIVER) return NOT_DRIVER_MESSAGE
        return when (val result = authRepository.loginWithPhone(verification.phoneToken)) {
            is ApiResult.Success -> null
            is ApiResult.Failure -> result.error.message
        }
    }

    companion object {
        val PHONE_PATTERN = Regex("^01\\d{8,9}$")
        const val PHONE_MAX_LENGTH = 11
        const val CODE_LENGTH = 6

        /** 서버 인증번호 유효 시간(3분)과 같다 */
        const val CODE_VALID_SECONDS = 180
        private const val CODE_VALID_MILLIS = CODE_VALID_SECONDS * 1000L

        const val NO_ACCOUNT_MESSAGE = "가입된 계정이 없습니다. 휴대폰 앱에서 먼저 가입해 주세요."
        const val NOT_DRIVER_MESSAGE = "차주 계정으로만 로그인할 수 있습니다."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PhoneLoginViewModel(appContainer().authRepository) }
        }
    }
}
