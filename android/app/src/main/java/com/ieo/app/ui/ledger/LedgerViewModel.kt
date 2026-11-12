package com.ieo.app.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ieo.app.core.network.ApiResult
import com.ieo.app.di.appContainer
import com.ieo.app.domain.delivery.DeliveryProofRepository
import com.ieo.app.domain.expense.Expense
import com.ieo.app.domain.expense.ExpenseCategory
import com.ieo.app.domain.expense.ExpenseRepository
import com.ieo.app.domain.ledger.LedgerEntry
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.ledger.ManualEntry
import com.ieo.app.domain.ledger.MonthlyLedger
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.common.formatWon
import java.time.Clock
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRepository

enum class LedgerTab(val label: String) {
    ENTRIES("장부"),
    RECEIVABLES("미수금"),
    EXPENSES("경비"),
}

/** 아래에서 올라오는 입력 창 */
sealed interface LedgerForm {
    data object AddExpense : LedgerForm
    data object AddTrip : LedgerForm
    data class EditFare(val entryId: Long, val fare: Long, val paymentType: PaymentType, val distanceKm: Int? = null) : LedgerForm

    /** "지울까요?" 확인. [detail]은 무엇을 지우는지 한 줄 */
    data class ConfirmDelete(val target: DeleteTarget, val title: String, val detail: String) : LedgerForm
}

/** 지울 것: 경비 한 건 또는 직접 추가한 운행 줄 */
sealed interface DeleteTarget {
    data class OfExpense(val expenseId: Long) : DeleteTarget
    data class OfEntry(val entryId: Long) : DeleteTarget
}

data class LedgerUiState(
    val tab: LedgerTab = LedgerTab.ENTRIES,
    /** "11월", 올해가 아니면 "2025년 11월" */
    val monthLabel: String = "",
    /** 지금 보는 달이 이번 달이면 다음 달로 넘어가지 않는다 */
    val canGoNext: Boolean = false,
    val summary: MonthSummary? = null,
    val receivable: ReceivableSummary? = null,
    /** 장부: 날짜별 묶음 */
    val days: List<DayGroup> = emptyList(),
    /** 미수금: 아직 받지 못한 줄 */
    val receivableRows: List<EntryRow> = emptyList(),
    /** 경비: 이번 달 경비 */
    val expenseRows: List<ExpenseRow> = emptyList(),
    val detail: EntryDetail? = null,
    val form: LedgerForm? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    /** 운행 기록에서 만든 대기 증빙 링크 창. 닫으면 null */
    val proof: WaitProofLink? = null,
    /** 연 줄의 종이 인수증 사진 (04 인수증). 받는 중이거나 없으면 비어 있다 */
    val receiptPhotos: List<ByteArray> = emptyList(),
)

interface LedgerActions {
    fun selectTab(tab: LedgerTab)
    fun previousMonth()
    fun nextMonth()
    fun openEntry(entryId: Long)
    fun close()
    fun setPaid(paid: Boolean)
    fun setWaitFeePaid(extraIncomeId: Long, paid: Boolean)
    fun openForm(form: LedgerForm)
    fun addExpense(category: ExpenseCategory, amount: Long, memo: String?)
    fun addTrip(description: String, fare: Long, paymentType: PaymentType, brokerName: String?, distanceKm: Int?)
    fun editFare(fare: Long, paymentType: PaymentType, distanceKm: Int?)

    /** 경비 줄을 누르면 지울지 묻는다 */
    fun askDeleteExpense(expenseId: Long)

    /** 열어 둔 운행 줄을 지울지 묻는다. 직접 추가한 줄만 */
    fun askDeleteEntry()
    fun confirmDelete()

    /** 고른 운행의 대기 증빙 링크를 만들어 보여 준다. 운행 완료 때 만들지 않았어도 여기서 만든다 (WAIT-020) */
    fun makeWaitProof()
    fun refresh()
}

/**
 * 장부 탭 (LEDG-002·003·004·010·021·022): 한 달 장부와 경비, 아직 받지 못한 돈.
 * 받음 표시·운임 고치기·경비 추가를 하면 서버에서 다시 받아 요약을 새로 계산한다.
 */
class LedgerViewModel(
    private val ledgerRepository: LedgerRepository,
    private val expenseRepository: ExpenseRepository,
    private val waitRepository: WaitRepository? = null,
    private val deliveryProofRepository: DeliveryProofRepository? = null,
    clock: Clock = Clock.system(DayTimeFormatter.KOREA),
) : ViewModel(), LedgerActions {

    private val labels = DayTimeFormatter(clock)
    private val mapper = LedgerUiMapper(labels)
    private val thisMonth = YearMonth.from(labels.today())

    private var month: YearMonth = thisMonth
    private var entries: List<LedgerEntry> = emptyList()
    private var expenses: List<Expense> = emptyList()
    private var receivables: Receivables? = null
    private var selectedId: Long? = null

    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    init {
        publish()
    }

    override fun refresh() {
        viewModelScope.launch { load() }
    }

    override fun selectTab(tab: LedgerTab) {
        _state.update { it.copy(tab = tab) }
    }

    override fun previousMonth() = moveMonth(month.minusMonths(1))

    override fun nextMonth() {
        if (month < thisMonth) moveMonth(month.plusMonths(1))
    }

    private var receiptLoad: Job? = null

    override fun openEntry(entryId: Long) {
        selectedId = entryId
        _state.update { it.copy(receiptPhotos = emptyList()) }
        publish()
        loadReceiptPhotos(selected())
    }

    /** 인수증 결제 운행이면 휴대폰으로 올린 종이 인수증 사진을 받는다. 못 받으면 사진 없이 보여 준다 */
    private fun loadReceiptPhotos(entry: LedgerEntry?) {
        receiptLoad?.cancel()
        val repository = deliveryProofRepository ?: return
        val tripId = entry?.takeIf { it.needsReceipt }?.tripId ?: return
        receiptLoad = viewModelScope.launch {
            val proof = (repository.get(tripId) as? ApiResult.Success)?.data ?: return@launch
            val photos = proof.photoIds.mapNotNull { id -> (repository.photo(tripId, id) as? ApiResult.Success)?.data }
            if (selectedId == entry.id) _state.update { it.copy(receiptPhotos = photos) }
        }
    }

    override fun close() {
        selectedId = null
        receiptLoad?.cancel()
        _state.update { it.copy(form = null) }
        publish()
    }

    override fun setPaid(paid: Boolean) {
        val entry = selected() ?: return
        runAction {
            ledgerRepository.setPaid(entry.id, paid).orShow() ?: return@runAction
            load()
        }
    }

    override fun setWaitFeePaid(extraIncomeId: Long, paid: Boolean) = runAction {
        ledgerRepository.setExtraIncomePaid(extraIncomeId, paid).orShow() ?: return@runAction
        load()
    }

    override fun openForm(form: LedgerForm) {
        selectedId = null
        _state.update { it.copy(form = form) }
        publish()
    }

    override fun addExpense(category: ExpenseCategory, amount: Long, memo: String?) {
        if (amount <= 0) return show(AMOUNT_REQUIRED_MESSAGE)
        runAction {
            expenseRepository.add(category, amount, memo).orShow() ?: return@runAction
            finishForm("${category.label} 경비를 기록했어요")
        }
    }

    override fun addTrip(description: String, fare: Long, paymentType: PaymentType, brokerName: String?, distanceKm: Int?) {
        if (description.isBlank()) return show(DESCRIPTION_REQUIRED_MESSAGE)
        if (fare <= 0) return show(AMOUNT_REQUIRED_MESSAGE)
        runAction {
            val entry = ManualEntry(labels.today(), description.trim(), fare, paymentType, brokerName?.trim()?.ifEmpty { null }, distanceKm)
            ledgerRepository.addManual(entry).orShow() ?: return@runAction
            finishForm("장부에 운행을 추가했어요")
        }
    }

    override fun editFare(fare: Long, paymentType: PaymentType, distanceKm: Int?) {
        val form = _state.value.form as? LedgerForm.EditFare ?: return
        val entry = entries.firstOrNull { it.id == form.entryId } ?: receivables?.entries?.firstOrNull { it.id == form.entryId } ?: return
        if (fare <= 0) return show(AMOUNT_REQUIRED_MESSAGE)
        runAction {
            ledgerRepository.update(entry, fare, paymentType, distanceKm).orShow() ?: return@runAction
            finishForm("장부를 고쳤어요")
        }
    }

    override fun askDeleteExpense(expenseId: Long) {
        val expense = expenses.firstOrNull { it.id == expenseId } ?: return
        val detail = labels.date(expense.spentOn) + (expense.memo?.let { " · $it" } ?: "")
        _state.update {
            it.copy(form = LedgerForm.ConfirmDelete(DeleteTarget.OfExpense(expenseId), "${expense.category.label} ${formatWon(expense.amount)}을 지울까요?", detail))
        }
    }

    override fun askDeleteEntry() {
        val entry = selected()?.takeIf { !it.auto } ?: return
        val detail = "${labels.date(entry.workDate)} · ${entry.description} · ${formatWon(entry.fare)}"
        _state.update { it.copy(form = LedgerForm.ConfirmDelete(DeleteTarget.OfEntry(entry.id), "이 운행을 장부에서 지울까요?", detail)) }
    }

    override fun confirmDelete() {
        val form = _state.value.form as? LedgerForm.ConfirmDelete ?: return
        runAction {
            when (val target = form.target) {
                is DeleteTarget.OfExpense -> {
                    expenseRepository.delete(target.expenseId).orShow() ?: return@runAction
                    finishForm("경비를 지웠어요")
                }
                is DeleteTarget.OfEntry -> {
                    ledgerRepository.deleteEntry(target.entryId).orShow() ?: return@runAction
                    selectedId = null
                    finishForm("운행을 지웠어요")
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** 증빙 링크를 만든 운행. 주선사에게 보낼 때 쓴다 */
    private var proofTripId: Long? = null

    override fun makeWaitProof() = runAction {
        val tripId = selected()?.tripId ?: return@runAction
        val link = waitRepository?.createProof(tripId)?.orShow() ?: return@runAction
        proofTripId = tripId
        _state.update { it.copy(detail = null, proof = link) }
    }

    fun sendWaitProof() = runAction {
        val tripId = proofTripId ?: return@runAction
        val link = waitRepository?.sendProof(tripId)?.orShow() ?: return@runAction
        _state.update { it.copy(proof = link) }
    }

    fun proofShown() = _state.update { it.copy(proof = null) }

    private fun moveMonth(target: YearMonth) {
        month = target
        selectedId = null
        publish()
        refresh()
    }

    private suspend fun finishForm(message: String) {
        _state.update { it.copy(form = null) }
        show(message)
        load()
    }

    /** 이번 달 장부·경비와 받을 돈을 함께 받는다 */
    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val monthEntries = viewModelScope.async { ledgerRepository.entries(from, to) }
        val monthExpenses = viewModelScope.async { expenseRepository.expenses(from, to) }
        val owed = viewModelScope.async { ledgerRepository.receivables() }
        monthEntries.await().orShow()?.let { entries = it }
        monthExpenses.await().orShow()?.let { expenses = it }
        owed.await().orShow()?.let { receivables = it }
        _state.update { it.copy(isLoading = false) }
        publish()
    }

    private fun publish() {
        val monthly = MonthlyLedger(month, entries, expenses)
        val owed = receivables
        _state.update { current ->
            current.copy(
                monthLabel = if (month.year == thisMonth.year) "${month.monthValue}월" else "${month.year}년 ${month.monthValue}월",
                canGoNext = month < thisMonth,
                summary = mapper.summary(monthly),
                receivable = owed?.let(mapper::receivableSummary),
                days = monthly.days().map(mapper::day),
                receivableRows = owed?.entries.orEmpty().map(mapper::entry),
                expenseRows = expenses.sortedByDescending { it.spentOn }.map(mapper::expense),
                detail = selected()?.let(mapper::detail),
            )
        }
    }

    /** 장부 탭과 미수금 탭 어디서 눌렀든 같은 줄을 찾는다 */
    private fun selected(): LedgerEntry? = selectedId?.let { id ->
        entries.firstOrNull { it.id == id } ?: receivables?.entries?.firstOrNull { it.id == id }
    }

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
        const val AMOUNT_REQUIRED_MESSAGE = "금액을 입력해 주세요"
        const val DESCRIPTION_REQUIRED_MESSAGE = "구간을 입력해 주세요"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                LedgerViewModel(container.ledgerRepository, container.expenseRepository, container.waitRepository, container.deliveryProofRepository)
            }
        }
    }
}
