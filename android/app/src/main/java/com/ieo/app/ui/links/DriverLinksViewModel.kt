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
import com.ieo.app.ui.common.josa
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 단골 주선사 한 줄. [detail]: "11월 12일까지 수락할 수 있어요", "11월 5일부터 연결" */
data class LinkRow(val id: Long, val brokerName: String, val detail: String)

data class DriverLinksUiState(
    /** 받은 요청 */
    val invitations: List<LinkRow> = emptyList(),
    /** 연결된 회사 */
    val links: List<LinkRow> = emptyList(),
    /** 수락·거절을 묻는 창에 띄운 요청. 없으면 null */
    val prompt: LinkRow? = null,
    /** 해제를 확인하는 중인 회사. 없으면 null */
    val unlinking: LinkRow? = null,
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 */
    val message: String? = null,
)

interface DriverLinksActions {
    /** 설정에서 받은 요청을 눌러 다시 연다 */
    fun open(linkId: Long)
    fun accept()
    fun decline()

    /** "나중에": 이번에 켜 둔 동안은 다시 띄우지 않는다. 설정에서 다시 열 수 있다 */
    fun later()
    fun confirmUnlink(linkId: Long)
    fun unlink()
    fun closeUnlink()
}

/**
 * 단골 주선사 (BRK-002): 주선사가 보낸 연결 요청을 수락·거절하고, 연결된 회사를 해제한다.
 * 홈의 요청 창과 설정 탭의 목록이 이 ViewModel 하나를 같이 쓴다 (화면이 Activity 범위로 받는다).
 * 요청 창은 정차 중에만 띄운다. 주행 중인지는 화면(MainScreen)이 보고 [refresh]를 부르거나 창을 감춘다.
 */
class DriverLinksViewModel(
    private val repository: DriverLinkRepository,
    clock: Clock = Clock.system(DayTimeFormatter.KOREA),
) : ViewModel(), DriverLinksActions {

    private val labels = DayTimeFormatter(clock)

    private val _state = MutableStateFlow(DriverLinksUiState())
    val state: StateFlow<DriverLinksUiState> = _state.asStateFlow()

    /** "나중에"를 누른 요청. 앱을 다시 켜면 비워져 다시 묻는다 */
    private val postponed = mutableSetOf<Long>()

    /** 요청과 연결을 새로 받는다. 못 받으면 있던 것을 그대로 둔다 (홈에서 조용히 부르므로 오류를 띄우지 않는다) */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    override fun open(linkId: Long) = _state.update { state ->
        state.copy(prompt = state.invitations.firstOrNull { it.id == linkId })
    }

    override fun accept() {
        val invitation = _state.value.prompt ?: return
        runAction {
            repository.accept(invitation.id).orShow() ?: return@runAction
            show(acceptedMessage(invitation.brokerName))
            _state.update { it.copy(prompt = null) }
            load()
        }
    }

    override fun decline() {
        val invitation = _state.value.prompt ?: return
        runAction {
            repository.decline(invitation.id).orShow() ?: return@runAction
            show(DECLINED_MESSAGE)
            _state.update { it.copy(prompt = null) }
            load()
        }
    }

    override fun later() {
        _state.value.prompt?.let { postponed += it.id }
        _state.update { it.copy(prompt = null) }
    }

    override fun confirmUnlink(linkId: Long) = _state.update { state ->
        state.copy(unlinking = state.links.firstOrNull { it.id == linkId })
    }

    override fun unlink() {
        val link = _state.value.unlinking ?: return
        runAction {
            repository.unlink(link.id).orShow() ?: return@runAction
            show(unlinkedMessage(link.brokerName))
            _state.update { it.copy(unlinking = null) }
            load()
        }
    }

    override fun closeUnlink() = _state.update { it.copy(unlinking = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private suspend fun load() {
        val invitations = viewModelScope.async { repository.invitations() }
        val links = viewModelScope.async { repository.links() }
        (invitations.await() as? ApiResult.Success)?.data?.let { list ->
            val rows = list.map(::invitationRow)
            _state.update { state ->
                // 이미 띄운 창은 그대로 두고, 없으면 아직 묻지 않은 첫 요청을 띄운다
                val prompt = state.prompt?.takeIf { shown -> rows.any { it.id == shown.id } }
                    ?: rows.firstOrNull { it.id !in postponed }
                state.copy(invitations = rows, prompt = prompt)
            }
        }
        (links.await() as? ApiResult.Success)?.data?.let { list ->
            _state.update { it.copy(links = list.map(::linkRow)) }
        }
    }

    private fun invitationRow(link: DriverLink) = LinkRow(
        id = link.id,
        brokerName = link.brokerName,
        detail = link.expiresAt?.let { "${labels.day(it)}까지 수락할 수 있어요" } ?: "수락을 기다리고 있어요",
    )

    private fun linkRow(link: DriverLink) = LinkRow(
        id = link.id,
        brokerName = link.brokerName,
        detail = link.linkedAt?.let { "${labels.day(it)}부터 연결" } ?: "연결됨",
    )

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
        const val DECLINED_MESSAGE = "요청을 거절했어요"

        fun acceptedMessage(brokerName: String) = "${josa(brokerName, "과", "와")} 단골로 연결했어요"

        fun unlinkedMessage(brokerName: String) = "${josa(brokerName, "과", "와")} 연결을 해제했어요"

        /** 수락은 위치 공유 동의다. 수락 창에 꼭 보여 준다 (BRK-002) */
        fun consentText(brokerName: String) =
            "${brokerName} 일을 하는 동안 내 위치와 도착 예정 시각이 ${brokerName}에 공유돼요. " +
                "연결되면 ${josa(brokerName, "이", "가")} 나에게만 보이는 배차를 보낼 수 있어요. 설정에서 언제든 해제할 수 있어요."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DriverLinksViewModel(appContainer().driverLinkRepository) }
        }
    }
}
