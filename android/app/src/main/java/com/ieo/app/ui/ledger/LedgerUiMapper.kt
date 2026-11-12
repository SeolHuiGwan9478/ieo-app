package com.ieo.app.ui.ledger

import com.ieo.app.domain.expense.Expense
import com.ieo.app.domain.ledger.LedgerDay
import com.ieo.app.domain.ledger.LedgerEntry
import com.ieo.app.domain.ledger.MonthlyLedger
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.trip.StopType
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.common.formatMinutesKorean
import com.ieo.app.ui.common.formatWon
import java.time.temporal.ChronoUnit

/** 받을 돈의 상태. 색과 함께 글자로도 구분한다 */
enum class PayState { PAID, WAITING, OVERDUE }

/** 장부 한 줄 (운행) */
data class EntryRow(
    val id: Long,
    val title: String,
    val paymentType: String,
    /** "○○물류 · 12월 3일 지급 예정" */
    val status: String,
    val payState: PayState,
    val amount: String,
    /** "+ 대기비 1만 원 청구 예정". 대기비가 없으면 null */
    val waitFeeNote: String?,
    /** 인수증 결제 운행이면 종이 인수증을 보냈는지. 아니면 null */
    val receiptSent: Boolean? = null,
)

/** 경비 한 줄 */
data class ExpenseRow(val id: Long, val title: String, val date: String, val amount: String)

/** 하루 묶음 */
data class DayGroup(val title: String, val entries: List<EntryRow>, val expenses: List<ExpenseRow>)

/** 이번 달 요약 */
data class MonthSummary(val revenue: String, val expense: String, val netProfit: String)

/** 받을 돈 요약 */
data class ReceivableSummary(val total: String, val overdue: String?, val waitFee: String?)

/** 장부 줄을 눌렀을 때 올라오는 상세 */
data class EntryDetail(
    val id: Long,
    val title: String,
    val lines: List<Pair<String, String>>,
    val paid: Boolean,
    val waitFees: List<WaitFeeRow>,
    /** 운임 고치기의 처음 값 */
    val fare: Long,
    val paymentType: PaymentType,
    /** 운행이 끝나 생긴 줄이라 대기 증빙을 만들 수 있다 (WAIT-020) */
    val canMakeWaitProof: Boolean = false,
    /** 인수증 결제 운행이라 종이 인수증 사진을 보여 준다 */
    val showsReceipt: Boolean = false,
)

data class WaitFeeRow(val id: Long, val label: String, val amount: String, val paid: Boolean)

/** 장부·경비·미수금을 장부 탭에 그릴 모양으로 바꾼다. 날짜 표기는 [labels]가 정한다 */
class LedgerUiMapper(private val labels: DayTimeFormatter) {

    fun summary(month: MonthlyLedger) = MonthSummary(
        revenue = formatWon(month.revenue),
        expense = formatWon(month.expenseTotal),
        netProfit = formatWon(month.netProfit),
    )

    fun receivableSummary(receivables: Receivables) = ReceivableSummary(
        total = formatWon(receivables.fareTotal),
        overdue = receivables.overdueFareTotal.takeIf { it > 0 }?.let(::formatWon),
        waitFee = receivables.waitFeeTotal.takeIf { it > 0 }?.let(::formatWon),
    )

    fun day(day: LedgerDay) = DayGroup(
        title = "${labels.date(day.date)} · ${day.entries.size}건",
        entries = day.entries.map(::entry),
        expenses = day.expenses.map(::expense),
    )

    fun entry(entry: LedgerEntry): EntryRow {
        val state = payState(entry)
        val broker = entry.brokerName ?: "직접 등록"
        val status = when (state) {
            PayState.PAID -> "$broker · 받음"
            PayState.OVERDUE -> "$broker · 지급일 ${overdueDays(entry)}일 지남"
            PayState.WAITING -> entry.expectedPaymentDate?.let { "$broker · ${labels.shortDate(it)} 지급 예정" } ?: broker
        }
        return EntryRow(
            id = entry.id,
            title = entry.description,
            paymentType = entry.paymentType.label,
            status = status,
            payState = state,
            amount = formatWon(entry.fare),
            waitFeeNote = waitFeeNote(entry),
            receiptSent = if (entry.needsReceipt) entry.receiptSentAt != null else null,
        )
    }

    fun expense(expense: Expense) = ExpenseRow(
        id = expense.id,
        title = listOfNotNull(expense.category.label, expense.memo).joinToString(" · "),
        date = labels.date(expense.spentOn),
        amount = "−" + formatWon(expense.amount),
    )

    fun detail(entry: LedgerEntry) = EntryDetail(
        id = entry.id,
        canMakeWaitProof = entry.tripId != null,
        title = "${entry.description} · ${formatWon(entry.fare)}",
        lines = listOfNotNull(
            "운행일" to labels.date(entry.workDate),
            "주선사" to (entry.brokerName ?: "직접 등록"),
            "결제" to entry.paymentType.label,
            "지급" to when (payState(entry)) {
                PayState.PAID -> "받음"
                PayState.OVERDUE -> "${entry.expectedPaymentDate?.let(labels::shortDate)} 예정 · ${overdueDays(entry)}일 지남"
                PayState.WAITING -> entry.expectedPaymentDate?.let { "${labels.shortDate(it)} 예정" } ?: "예정일 없음"
            },
            entry.distanceKm?.let { "거리" to "${it}km" },
            entry.waitMinutes?.takeIf { it > 0 }?.let { "대기" to formatMinutesKorean(it) },
            if (entry.needsReceipt) "인수증" to (entry.receiptSentAt?.let { "${labels.dayTime(it)} 보냄" } ?: "아직 안 보냈어요 · 휴대폰 증빙에서 보내요") else null,
        ),
        showsReceipt = entry.needsReceipt,
        paid = entry.paid,
        waitFees = entry.extraIncomes.map {
            WaitFeeRow(
                id = it.id,
                label = (if (it.stopType == StopType.PICKUP) "상차지" else "하차지") + " ${it.siteName} 대기비",
                amount = formatWon(it.amount),
                paid = it.paid,
            )
        },
        fare = entry.fare,
        paymentType = entry.paymentType,
    )

    private fun payState(entry: LedgerEntry) = when {
        entry.paid -> PayState.PAID
        entry.overdue -> PayState.OVERDUE
        else -> PayState.WAITING
    }

    private fun overdueDays(entry: LedgerEntry): Long =
        entry.expectedPaymentDate?.let { ChronoUnit.DAYS.between(it, labels.today()) } ?: 0

    private fun waitFeeNote(entry: LedgerEntry): String? {
        if (entry.extraIncomes.isEmpty()) return null
        val unpaid = entry.extraIncomes.filterNot { it.paid }.sumOf { it.amount }
        return if (unpaid > 0) {
            "+ 대기비 ${formatWon(unpaid)} 청구 예정"
        } else {
            "+ 대기비 ${formatWon(entry.extraIncomes.sumOf { it.amount })} 받음"
        }
    }
}
