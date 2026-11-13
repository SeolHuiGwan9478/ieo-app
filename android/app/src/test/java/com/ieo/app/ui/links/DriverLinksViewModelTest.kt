package com.ieo.app.ui.links

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.link.DriverLink
import com.ieo.app.domain.link.DriverLinkRepository
import com.ieo.app.domain.link.DriverLinkStatus
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 차량은 단골 요청과 연결을 보여 주기만 한다. 수락·거절·해제는 휴대폰에서 (BRK-002, 2026-11-13) */
class DriverLinksViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeLinks()
    private val viewModel = DriverLinksViewModel(server, Clock.fixed(NOW, ZoneOffset.UTC))

    @Test
    fun `받은 요청은 휴대폰에서 답하라고 기한과 함께 보여 준다`() = runTest(main.dispatcher) {
        server.invitations += DriverLink(1, "한빛물류", DriverLinkStatus.PENDING, expiresAt = NOW.plusSeconds(7 * 86_400), linkedAt = null)

        viewModel.refresh()
        advanceUntilIdle()

        val row = viewModel.state.value.invitations.single()
        assertEquals("한빛물류", row.brokerName)
        assertTrue(row.detail.endsWith("까지 휴대폰에서 답할 수 있어요"))
    }

    @Test
    fun `연결된 회사는 연결한 날과 함께 보여 준다`() = runTest(main.dispatcher) {
        server.links += DriverLink(2, "○○물류", DriverLinkStatus.LINKED, expiresAt = null, linkedAt = NOW)

        viewModel.refresh()
        advanceUntilIdle()

        val row = viewModel.state.value.links.single()
        assertEquals("○○물류", row.brokerName)
        assertTrue(row.detail.endsWith("부터 연결"))
    }

    private class FakeLinks : DriverLinkRepository {
        val invitations = mutableListOf<DriverLink>()
        val links = mutableListOf<DriverLink>()

        override suspend fun invitations(): ApiResult<List<DriverLink>> = ApiResult.Success(invitations.toList())

        override suspend fun links(): ApiResult<List<DriverLink>> = ApiResult.Success(links.toList())
    }
}
