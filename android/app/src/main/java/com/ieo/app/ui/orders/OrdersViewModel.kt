package com.ieo.app.ui.orders

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.order.ReportType
import com.ieo.app.domain.vehicle.VehicleSensors
import com.ieo.app.ui.common.DayTimeFormatter
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrderDayFilter(val label: String) {
    ALL("전체 날짜"),
    TODAY("오늘"),
    TOMORROW("내일"),
}

/** 오더 탭 화면 상태. 목록 한 줄과 상세는 [OrderUiMapper]가 만든다 */
data class OrdersUiState(
    val filter: OrderDayFilter = OrderDayFilter.ALL,
    /** 차량 위치로 근처 오더를 찾았다. 위치를 몰라 전체에서 찾았으면 false */
    val nearby: Boolean = false,
    val rows: List<OrderRow> = emptyList(),
    /** 확정을 기다리는 내 요청. 여러 개면 만료가 가까운 것 */
    val pending: PendingRequest? = null,
    /** 열린 상세. 없으면 null */
    val detail: OrderDetail? = null,
    /** 상세에서 "신고"를 눌러 사유를 고르는 중 */
    val reporting: Boolean = false,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    /** 한 번 보여 주고 지운다 */
    val message: String? = null,
)

interface OrdersActions {
    fun selectFilter(filter: OrderDayFilter)
    fun open(orderId: Long)
    fun close()
    fun request()
    fun withdraw()
    fun startReport()
    fun report(type: ReportType)
    fun refresh()
}

/**
 * 오더 탭 (DSP-010·020·042): 모집 중 오더를 보고, 배차를 요청하거나 철회하고, 신고한다.
 * 요청이 확정되면 서버가 운행을 만들고, 홈 "내 배차"에 나타난다.
 * 차량 위치를 알면 상차지가 50km 안인 근처 오더만 보여 준다.
 */
class OrdersViewModel(
    private val orderRepository: OrderRepository,
    private val sensors: VehicleSensors,
    private val clock: Clock = Clock.system(DayTimeFormatter.KOREA),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** 배차 확정·마감·만료 알림이 오면 내 요청 상태가 바뀌었으니 목록을 새로 받는다 */
    private val notifications: Flow<AppNotification> = emptyFlow(),
) : ViewModel(), OrdersActions {

    private val mapper = OrderUiMapper(DayTimeFormatter(clock))

    private var orders: List<Order> = emptyList()
    private var selectedId: Long? = null

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private var listening = false

    override fun refresh() {
        // 탭을 처음 열 때부터 알림을 듣는다
        if (!listening) {
            listening = true
            viewModelScope.launch { notifications.collect { reload() } }
        }
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            reload()
            _state.update { it.copy(isLoading = false) }
        }
    }

    override fun selectFilter(filter: OrderDayFilter) {
        _state.update { it.copy(filter = filter) }
        publish()
    }

    override fun open(orderId: Long) {
        selectedId = orderId
        _state.update { it.copy(reporting = false) }
        publish()
    }

    override fun close() {
        selectedId = null
        _state.update { it.copy(reporting = false) }
        publish()
    }

    override fun request() {
        val order = selected() ?: return
        runAction {
            orderRepository.request(order.id).orShow() ?: return@runAction
            show(REQUESTED_MESSAGE)
            reload()
        }
    }

    override fun withdraw() {
        val request = (selected() ?: pendingOrder())?.myRequest ?: return
        runAction {
            orderRepository.withdraw(request.id).orShow() ?: return@runAction
            show(WITHDRAWN_MESSAGE)
            reload()
        }
    }

    override fun startReport() = _state.update { it.copy(reporting = true) }

    override fun report(type: ReportType) {
        val order = selected() ?: return
        runAction {
            orderRepository.report(order.id, type).orShow() ?: return@runAction
            show(REPORTED_MESSAGE)
            close()
        }
    }

    private suspend fun reload() {
        val near = sensors.location.value
        orderRepository.openOrders(limit = LIST_SIZE, near = near).orShow()?.let { list ->
            orders = list
            _state.update { it.copy(nearby = near != null) }
        }
        publish()
    }

    /** 받아 둔 오더와 고른 날짜로 화면 상태를 다시 만든다 */
    private fun publish() {
        val filter = _state.value.filter
        val visible = orders.filter { mapper.matches(it, filter) }
        _state.update { current ->
            current.copy(
                rows = visible.map(mapper::row),
                pending = pendingOrder()?.let { mapper.pending(it, ::toElapsed) },
                detail = selected()?.let(mapper::detail),
            )
        }
    }

    private fun selected(): Order? = selectedId?.let { id -> orders.firstOrNull { it.id == id } }

    private fun pendingOrder(): Order? = orders.filter { it.isRequestedByMe }.minByOrNull { it.myRequest!!.expiresAt }

    private fun toElapsed(at: java.time.Instant): Long = elapsedRealtime() + Duration.between(clock.instant(), at).toMillis()

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

    fun messageShown() = _state.update { it.copy(message = null) }

    companion object {
        private const val LIST_SIZE = 50
        const val REQUESTED_MESSAGE = "배차를 요청했어요. 주선사가 확정하면 내 배차에 생겨요"
        const val WITHDRAWN_MESSAGE = "요청을 철회했어요"
        const val REPORTED_MESSAGE = "신고를 접수했어요"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                OrdersViewModel(container.orderRepository, container.vehicleSensors, notifications = container.notifications.received)
            }
        }
    }
}
