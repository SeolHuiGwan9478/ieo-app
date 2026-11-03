package com.ieo.app.ui.login

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.auth.CarLoginStatus
import com.ieo.app.testing.FakeAuthRepository
import com.ieo.app.testing.MainDispatcherRule
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrLoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = QrLoginViewModel(
        authRepository = repository,
        clock = Clock.fixed(FakeAuthRepository.NOW, ZoneOffset.UTC),
        elapsedRealtime = { main.dispatcher.scheduler.currentTime },
    )

    @Test
    fun `화면이 보이면 서버 코드를 받아 QR로 보여 준다`() = runTest(main.dispatcher) {
        viewModel.onVisible()
        runCurrent()

        val state = viewModel.state.value as QrLoginUiState.Waiting
        assertEquals("KX7P-M4QZ", state.code)
        assertEquals("ieo://car-login?code=KX7P-M4QZ", state.qrContent)
        // 서버 만료 5분을 기기 시계 기준으로 바꾼 값
        assertEquals(300_000L, state.expiresAt)
        viewModel.onHidden()
    }

    @Test
    fun `2초마다 묻고 거절되면 멈춘다`() = runTest(main.dispatcher) {
        repository.pollResults += ApiResult.Success(CarLoginStatus.PENDING)
        repository.pollResults += ApiResult.Success(CarLoginStatus.REJECTED)
        viewModel.onVisible()

        advanceTimeBy(4_001)

        assertEquals(2, repository.pollCount)
        assertEquals(QrLoginUiState.Rejected, viewModel.state.value)
        advanceTimeBy(10_000)
        assertEquals(2, repository.pollCount)
    }

    @Test
    fun `연결이 끊겨도 계속 묻는다`() = runTest(main.dispatcher) {
        repository.pollResults += ApiResult.Failure(ApiError.Network)
        repository.pollResults += ApiResult.Failure(ApiError.Network)
        repository.pollResults += ApiResult.Success(CarLoginStatus.PENDING)
        viewModel.onVisible()

        advanceTimeBy(6_001)

        assertEquals(3, repository.pollCount)
        assertTrue(viewModel.state.value is QrLoginUiState.Waiting)
        viewModel.onHidden()
    }

    @Test
    fun `화면을 떠났다 오면 같은 코드로 이어서 묻는다`() = runTest(main.dispatcher) {
        viewModel.onVisible()
        runCurrent()
        viewModel.onHidden()

        viewModel.onVisible()
        runCurrent()

        assertEquals(1, repository.startCarLoginCount)
        viewModel.onHidden()
    }

    @Test
    fun `만료된 뒤 새 QR을 누르면 새 코드를 받는다`() = runTest(main.dispatcher) {
        repository.pollResults += ApiResult.Success(CarLoginStatus.EXPIRED)
        viewModel.onVisible()
        advanceTimeBy(2_001)
        assertEquals(QrLoginUiState.Expired, viewModel.state.value)

        viewModel.refresh()
        runCurrent()

        assertEquals(2, repository.startCarLoginCount)
        viewModel.onHidden()
    }
}
