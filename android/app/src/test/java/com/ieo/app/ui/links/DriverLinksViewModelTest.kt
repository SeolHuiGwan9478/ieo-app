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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DriverLinksViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeLinks()
    private val viewModel = DriverLinksViewModel(server, Clock.fixed(NOW, ZoneOffset.UTC))

    @Test
    fun `받은 요청이 있으면 수락 창을 띄우고 기한을 보여 준다`() = runTest(main.dispatcher) {
        server.invitations += invitation(1, "한빛물류")

        viewModel.refresh()
        advanceUntilIdle()

        val prompt = viewModel.state.value.prompt!!
        assertEquals("한빛물류", prompt.brokerName)
        assertTrue(prompt.detail.endsWith("까지 수락할 수 있어요"))
    }

    @Test
    fun `수락하면 연결된 회사로 옮기고 창을 닫는다`() = runTest(main.dispatcher) {
        server.invitations += invitation(1, "한빛물류")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        assertEquals(listOf("accept 1"), server.calls)
        assertNull(viewModel.state.value.prompt)
        assertEquals(listOf("한빛물류"), viewModel.state.value.links.map { it.brokerName })
        assertTrue(viewModel.state.value.invitations.isEmpty())
        assertEquals(DriverLinksViewModel.acceptedMessage("한빛물류"), viewModel.state.value.message)
    }

    @Test
    fun `거절하면 다음 요청을 묻는다`() = runTest(main.dispatcher) {
        server.invitations += listOf(invitation(1, "한빛물류"), invitation(2, "대한운송"))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.decline()
        advanceUntilIdle()

        assertEquals(listOf("decline 1"), server.calls)
        assertEquals("대한운송", viewModel.state.value.prompt?.brokerName)
    }

    @Test
    fun `나중에를 누르면 다시 받아도 띄우지 않지만 설정에서 다시 열 수 있다`() = runTest(main.dispatcher) {
        server.invitations += invitation(1, "한빛물류")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.later()
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.state.value.prompt)
        assertEquals(1, viewModel.state.value.invitations.size)

        viewModel.open(1)
        assertEquals("한빛물류", viewModel.state.value.prompt?.brokerName)
    }

    @Test
    fun `연결된 회사를 확인 뒤 해제한다`() = runTest(main.dispatcher) {
        server.links += DriverLink(5, "한빛물류", DriverLinkStatus.LINKED, expiresAt = null, linkedAt = NOW)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("오늘부터 연결", viewModel.state.value.links.single().detail)

        viewModel.confirmUnlink(5)
        assertEquals("한빛물류", viewModel.state.value.unlinking?.brokerName)
        viewModel.unlink()
        advanceUntilIdle()

        assertEquals(listOf("unlink 5"), server.calls)
        assertNull(viewModel.state.value.unlinking)
        assertTrue(viewModel.state.value.links.isEmpty())
    }

    @Test
    fun `만료된 요청을 수락하면 서버 오류 문장을 보여 주고 창을 그대로 둔다`() = runTest(main.dispatcher) {
        server.invitations += invitation(1, "한빛물류")
        server.acceptError = com.ieo.app.core.network.ApiError.Server(409, "DRIVER_LINK_409_CLOSED", "만료되었거나 이미 처리된 요청입니다.")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        assertEquals("만료되었거나 이미 처리된 요청입니다.", viewModel.state.value.message)
        assertEquals("한빛물류", viewModel.state.value.prompt?.brokerName)
    }

    private fun invitation(id: Long, broker: String) =
        DriverLink(id, broker, DriverLinkStatus.PENDING, expiresAt = NOW.plusSeconds(7 * 86_400), linkedAt = null)

    private class FakeLinks : DriverLinkRepository {
        val invitations = mutableListOf<DriverLink>()
        val links = mutableListOf<DriverLink>()
        val calls = mutableListOf<String>()
        var acceptError: com.ieo.app.core.network.ApiError? = null

        override suspend fun invitations(): ApiResult<List<DriverLink>> = ApiResult.Success(invitations.toList())

        override suspend fun links(): ApiResult<List<DriverLink>> = ApiResult.Success(links.toList())

        override suspend fun accept(linkId: Long): ApiResult<DriverLink> {
            acceptError?.let { return ApiResult.Failure(it) }
            calls += "accept $linkId"
            val accepted = invitations.first { it.id == linkId }.copy(status = DriverLinkStatus.LINKED, linkedAt = NOW)
            invitations.removeAll { it.id == linkId }
            links += accepted
            return ApiResult.Success(accepted)
        }

        override suspend fun decline(linkId: Long): ApiResult<Unit> {
            calls += "decline $linkId"
            invitations.removeAll { it.id == linkId }
            return ApiResult.Success(Unit)
        }

        override suspend fun unlink(linkId: Long): ApiResult<Unit> {
            calls += "unlink $linkId"
            links.removeAll { it.id == linkId }
            return ApiResult.Success(Unit)
        }
    }
}
