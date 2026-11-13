package com.ieo.app.ui.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.link.DriverLink
import com.ieo.app.domain.link.DriverLinkRepository
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 단골 주선사 한 줄. [detail]: "11월 12일까지 휴대폰에서 답할 수 있어요", "11월 5일부터 연결" */
data class LinkRow(val id: Long, val brokerName: String, val detail: String)

data class DriverLinksUiState(
    /** 받은 요청 */
    val invitations: List<LinkRow> = emptyList(),
    /** 연결된 회사 */
    val links: List<LinkRow> = emptyList(),
)

/**
 * 단골 주선사 (BRK-002). 차량에서는 받은 요청과 연결된 회사를 보여 주기만 한다.
 * 수락은 위치 공유 동의라 차분히 읽고 고르도록 수락·거절·해제는 휴대폰에서만 한다 (2026-11-13).
 * 요청이 오면 서버 알림이 홈 배너로 알려 준다
 */
class DriverLinksViewModel(
    private val repository: DriverLinkRepository,
    clock: Clock = Clock.system(DayTimeFormatter.KOREA),
) : ViewModel() {

    private val labels = DayTimeFormatter(clock)

    private val _state = MutableStateFlow(DriverLinksUiState())
    val state: StateFlow<DriverLinksUiState> = _state.asStateFlow()

    /** 요청과 연결을 새로 받는다. 못 받으면 있던 것을 그대로 둔다 */
    fun refresh() {
        viewModelScope.launch {
            val invitations = async { repository.invitations() }
            val links = async { repository.links() }
            (invitations.await() as? ApiResult.Success)?.data?.let { list -> _state.update { it.copy(invitations = list.map(::invitationRow)) } }
            (links.await() as? ApiResult.Success)?.data?.let { list -> _state.update { it.copy(links = list.map(::linkRow)) } }
        }
    }

    private fun invitationRow(link: DriverLink) = LinkRow(
        id = link.id,
        brokerName = link.brokerName,
        detail = link.expiresAt?.let { "${labels.day(it)}까지 휴대폰에서 답할 수 있어요" } ?: "휴대폰에서 답할 수 있어요",
    )

    private fun linkRow(link: DriverLink) = LinkRow(
        id = link.id,
        brokerName = link.brokerName,
        detail = link.linkedAt?.let { "${labels.day(it)}부터 연결" } ?: "연결됨",
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DriverLinksViewModel(appContainer().driverLinkRepository) }
        }
    }
}
