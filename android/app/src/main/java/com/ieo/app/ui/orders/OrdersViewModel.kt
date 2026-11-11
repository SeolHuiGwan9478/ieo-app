package com.ieo.app.ui.orders

import com.ieo.app.domain.recommendation.ExcludedCargoStore
import com.ieo.app.domain.recommendation.PreferenceExcludedCargoStore
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.OrderProfit
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.order.OrderSearch
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
    ALL("전체"),
    TODAY("오늘"),
    TOMORROW("내일"),
}

/** 내 주변 거리. [km]가 null이면 위치 없이 전국에서 찾는다 */
enum class OrderRadius(val km: Int?, val label: String) {
    NEAR(20, "20km"),
    NORMAL(50, "50km"),
    FAR(100, "100km"),
    NATIONWIDE(null, "전국"),
}

enum class OrderMinFare(val won: Long?, val label: String) {
    ANY(null, "전체"),
    OVER_30(300_000, "30만 원 이상"),
    OVER_50(500_000, "50만 원 이상"),
}

/** 결제 방식. [serverName]은 서버 PaymentType 이름 */
enum class OrderPayment(val serverName: String?, val label: String) {
    ANY(null, "전체"),
    PREPAID("PREPAID", "선불"),
    RECEIPT("RECEIPT", "인수증"),
}

/**
 * 오더 탭 조건 (DSP-010). 거리·운임·결제는 서버가 거르고, 날짜·제외할 화물은 받은 목록에서 거른다.
 * 휴대폰 앱 오더 찾기와 같은 조건이다
 */
data class OrderConditions(
    val radius: OrderRadius = OrderRadius.NORMAL,
    val day: OrderDayFilter = OrderDayFilter.ALL,
    val minFare: OrderMinFare = OrderMinFare.ANY,
    val payment: OrderPayment = OrderPayment.ANY,
    val excluded: Set<CargoCategory> = emptySet(),
) {
    val search: OrderSearch get() = OrderSearch(radiusKm = radius.km ?: 50, minFare = minFare.won, paymentType = payment.serverName)

    /** 기본값에서 바꾼 조건 수. "조건" 버튼 옆 숫자 */
    val changedCount: Int get() = listOf(
        radius != OrderRadius.NORMAL,
        day != OrderDayFilter.ALL,
        minFare != OrderMinFare.ANY,
        payment != OrderPayment.ANY,
        excluded.isNotEmpty(),
    ).count { it }

    /** 목록 위에 남기는 조건. 거리는 늘 보여 주고, 나머지는 바꾼 것만 */
    val summary: List<String> get() = listOfNotNull(
        radius.km?.let { "내 주변 ${radius.label}" } ?: "전국",
        day.takeIf { it != OrderDayFilter.ALL }?.let { "${it.label} 상차" },
        minFare.takeIf { it != OrderMinFare.ANY }?.label,
        payment.takeIf { it != OrderPayment.ANY }?.label,
        excluded.takeIf { it.isNotEmpty() }?.let { set -> set.sortedBy { it.ordinal }.joinToString(", ") { it.label } + " 제외" },
    )
}

/** 목록 정렬. 휴대폰과 같다 */
enum class OrderSort(val label: String) { SOON("상차 시간순"), FARE("운임 높은 순") }

/** 오더 탭 화면 상태. 목록 한 줄과 상세는 [OrderUiMapper]가 만든다 */
data class OrdersUiState(
    val conditions: OrderConditions = OrderConditions(),
    val sort: OrderSort = OrderSort.SOON,
    /** 조건 창에서 고르는 중인 조건. 창이 닫혀 있으면 null */
    val draft: OrderConditions? = null,
    /** [draft]로 찾으면 나올 건수. 세는 중이면 null */
    val draftCount: Int? = null,
    /** 마지막으로 목록을 받은 시각. 아직 못 받았으면 null */
    val updatedAt: java.time.Instant? = null,
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
    /** 필수 서류가 모두 승인됐다. 모르면 막지 않는다 (서버가 한 번 더 막는다) */
    val verified: Boolean = true,
    /** 한 번 보여 주고 지운다 */
    val message: String? = null,
)

interface OrdersActions {
    fun openConditions()
    fun editDraft(change: (OrderConditions) -> OrderConditions)
    fun resetDraft()
    fun closeConditions()
    fun applyConditions()
    /** 화면이 보이는 동안 1분마다 부른다 */
    fun autoRefresh()
    /** 상차 시간순 ↔ 운임 높은 순 */
    fun toggleSort()
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
 * 차량 위치를 알면 상차지가 고른 거리(기본 50km) 안인 근처 오더만 보여 준다.
 * 목록은 화면이 보이는 동안 1분마다 다시 받는다. 상세나 조건 창이 열려 있으면 순서가 바뀌지 않게 닫은 뒤에 받는다.
 */
class OrdersViewModel(
    private val orderRepository: OrderRepository,
    private val sensors: VehicleSensors,
    private val clock: Clock = Clock.system(DayTimeFormatter.KOREA),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    /** 배차 확정·마감·만료 알림이 오면 내 요청 상태가 바뀌었으니 목록을 새로 받는다 */
    private val notifications: Flow<AppNotification> = emptyFlow(),
    /** 제외할 화물은 배차 추천 설정과 같이 쓴다 */
    private val excludedCargo: ExcludedCargoStore = ExcludedCargoStore.None,
) : ViewModel(), OrdersActions {

    private val mapper = OrderUiMapper(DayTimeFormatter(clock))

    private var orders: List<Order> = emptyList()
    private var selectedId: Long? = null
    private var profit: OrderProfit? = null
    /** 자동 새로고침으로 새로 들어온 오더 */
    private var newIds: Set<Long> = emptySet()
    private var refreshPending = false
    private var countJob: kotlinx.coroutines.Job? = null

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
            // 설정 탭(배차 추천)에서 바꾼 제외할 화물도 이때 다시 읽는다
            excludedCargo.load()?.let { saved -> _state.update { it.copy(conditions = it.conditions.copy(excluded = saved)) } }
            reload()
            _state.update { it.copy(isLoading = false) }
        }
    }

    override fun autoRefresh() {
        val now = _state.value
        if (selectedId != null || now.draft != null) {
            refreshPending = true
            return
        }
        viewModelScope.launch { reload(markNew = true) }
    }

    override fun toggleSort() {
        _state.update { it.copy(sort = if (it.sort == OrderSort.SOON) OrderSort.FARE else OrderSort.SOON) }
        publish()
    }

    override fun openConditions() {
        _state.update { it.copy(draft = it.conditions, draftCount = null) }
        countDraft()
    }

    override fun editDraft(change: (OrderConditions) -> OrderConditions) {
        val draft = _state.value.draft ?: return
        _state.update { it.copy(draft = change(draft), draftCount = null) }
        countDraft()
    }

    override fun resetDraft() = editDraft { OrderConditions() }

    override fun closeConditions() {
        countJob?.cancel()
        _state.update { it.copy(draft = null, draftCount = null) }
        runPendingRefresh()
    }

    /** 고른 조건으로 다시 받는다. 조건이 바뀌었으니 "새로 올라옴" 표시는 지운다 */
    override fun applyConditions() {
        val draft = _state.value.draft ?: return
        countJob?.cancel()
        refreshPending = false
        newIds = emptySet()
        val excludedChanged = draft.excluded != _state.value.conditions.excluded
        _state.update { it.copy(conditions = draft, draft = null, draftCount = null) }
        if (excludedChanged) {
            // 저장한 뒤에 다시 받아야 refresh가 옛 값으로 되돌리지 않는다
            viewModelScope.launch {
                excludedCargo.save(draft.excluded)
                refresh()
            }
        } else {
            refresh()
        }
    }

    private fun runPendingRefresh() {
        if (!refreshPending) return
        refreshPending = false
        autoRefresh()
    }

    /** 조건 창 아래 버튼에 넣을 건수 */
    private fun countDraft() {
        val draft = _state.value.draft ?: return
        countJob?.cancel()
        countJob = viewModelScope.launch {
            val near = draft.radius.km?.let { sensors.location.value }
            val list = (orderRepository.openOrders(limit = LIST_SIZE, near = near, search = draft.search) as? ApiResult.Success)?.data ?: return@launch
            val count = list.count { mapper.matches(it, draft) }
            _state.update { if (it.draft == draft) it.copy(draftCount = count) else it }
        }
    }

    override fun open(orderId: Long) {
        selectedId = orderId
        profit = null
        _state.update { it.copy(reporting = false) }
        publish()
        val from = sensors.location.value ?: return
        viewModelScope.launch {
            // 못 받으면 목록의 어림값을 그대로 둔다
            val loaded = (orderRepository.profit(orderId, from) as? ApiResult.Success)?.data ?: return@launch
            if (selectedId == orderId) {
                profit = loaded
                publish()
            }
        }
    }

    override fun close() {
        selectedId = null
        _state.update { it.copy(reporting = false) }
        publish()
        runPendingRefresh()
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
            // 신고는 오더마다 한 번이라 신고 버튼을 닫는다
            orders = orders.map { if (it.id == order.id) it.copy(reportedByMe = true) else it }
            show(REPORTED_MESSAGE)
            close()
        }
    }

    private suspend fun reload(markNew: Boolean = false) {
        val conditions = _state.value.conditions
        val near = conditions.radius.km?.let { sensors.location.value }
        val result = orderRepository.openOrders(limit = LIST_SIZE, near = near, search = conditions.search)
        // 자동 새로고침 실패는 조용히 넘긴다. 다음 1분에 다시 받는다
        val list = if (markNew) (result as? ApiResult.Success)?.data else result.orShow()
        list?.let {
            val before = orders.map { o -> o.id }.toSet()
            if (markNew && before.isNotEmpty()) newIds = newIds + it.map { o -> o.id }.filterNot { id -> id in before }
            newIds = newIds.filter { id -> it.any { o -> o.id == id } }.toSet()
            orders = it
            _state.update { s -> s.copy(nearby = near != null, updatedAt = clock.instant()) }
        }
        (orderRepository.documentsVerified() as? ApiResult.Success)?.let { result -> _state.update { it.copy(verified = result.data) } }
        publish()
    }

    /** 받아 둔 오더와 고른 날짜·제외할 화물로 화면 상태를 다시 만든다 */
    private fun publish() {
        val conditions = _state.value.conditions
        val visible = orders.filter { mapper.matches(it, conditions) }.let { list ->
            when (_state.value.sort) {
                OrderSort.SOON -> list.sortedBy { it.pickupFrom }
                OrderSort.FARE -> list.sortedByDescending { it.fare }
            }
        }
        _state.update { current ->
            current.copy(
                rows = visible.map { mapper.row(it, isNew = it.id in newIds) },
                pending = pendingOrder()?.let { mapper.pending(it, ::toElapsed) },
                detail = selected()?.let { mapper.detail(it, profit) },
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
        const val AUTO_REFRESH_MILLIS = 60_000L
        const val REQUESTED_MESSAGE = "배차를 요청했어요. 주선사가 확정하면 내 배차에 생겨요"
        const val WITHDRAWN_MESSAGE = "요청을 철회했어요"
        const val REPORTED_MESSAGE = "신고를 접수했어요"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                OrdersViewModel(
                    container.orderRepository,
                    container.vehicleSensors,
                    notifications = container.notifications.received,
                    excludedCargo = PreferenceExcludedCargoStore(container.recommendationRepository),
                )
            }
        }
    }
}
