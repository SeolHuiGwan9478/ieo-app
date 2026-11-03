package com.ieo.app.ui.login

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.auth.ExistingAccount
import com.ieo.app.domain.auth.PhoneVerification
import com.ieo.app.domain.auth.UserRole
import com.ieo.app.testing.FakeAuthRepository
import com.ieo.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneLoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = PhoneLoginViewModel(repository, elapsedRealtime = { 1_000L })

    @Test
    fun `번호는 숫자만 11자리까지 받는다`() {
        viewModel.onPhoneChange("010-1234-5678-9")

        assertEquals("01012345678", viewModel.state.value.phone)
        assertTrue(viewModel.state.value.phoneValid)
    }

    @Test
    fun `인증번호를 보내면 3분 타이머가 시작된다`() = runTest(main.dispatcher) {
        viewModel.onPhoneChange("01012345678")
        viewModel.requestCode()
        advanceUntilIdle()

        assertEquals(1_000L + 180_000L, viewModel.state.value.codeExpiresAt)
        assertFalse(viewModel.state.value.isRequestingCode)
    }

    @Test
    fun `번호를 바꾸면 받은 인증번호를 지운다`() = runTest(main.dispatcher) {
        viewModel.onPhoneChange("01012345678")
        viewModel.requestCode()
        advanceUntilIdle()
        viewModel.onCodeChange("123456")

        viewModel.onPhoneChange("01012345679")

        assertFalse(viewModel.state.value.codeSent)
        assertEquals("", viewModel.state.value.code)
    }

    @Test
    fun `서버 오류 문장을 그대로 보여 준다`() = runTest(main.dispatcher) {
        repository.requestCodeResult = ApiResult.Failure(ApiError.Server(429, "AUTH_429_RESEND", "인증번호는 1분에 한 번만 받을 수 있습니다."))
        viewModel.onPhoneChange("01012345678")
        viewModel.requestCode()
        advanceUntilIdle()

        assertEquals("인증번호는 1분에 한 번만 받을 수 있습니다.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.codeSent)
    }

    @Test
    fun `가입된 차주면 phoneToken으로 로그인한다`() = runTest(main.dispatcher) {
        enterCode()
        viewModel.login()
        advanceUntilIdle()

        assertEquals("phone-token", repository.loggedInWith)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `가입된 계정이 없으면 로그인하지 않는다`() = runTest(main.dispatcher) {
        repository.verifyResult = ApiResult.Success(PhoneVerification("phone-token", existingAccount = null))
        enterCode()
        viewModel.login()
        advanceUntilIdle()

        assertNull(repository.loggedInWith)
        assertEquals(PhoneLoginViewModel.NO_ACCOUNT_MESSAGE, viewModel.state.value.errorMessage)
    }

    @Test
    fun `주선사 계정은 차량에서 로그인하지 않는다`() = runTest(main.dispatcher) {
        repository.verifyResult = ApiResult.Success(PhoneVerification("phone-token", ExistingAccount("김주선", UserRole.BROKER)))
        enterCode()
        viewModel.login()
        advanceUntilIdle()

        assertNull(repository.loggedInWith)
        assertEquals(PhoneLoginViewModel.NOT_DRIVER_MESSAGE, viewModel.state.value.errorMessage)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.enterCode() {
        viewModel.onPhoneChange("01012345678")
        viewModel.requestCode()
        advanceUntilIdle()
        viewModel.onCodeChange("123456")
    }
}
