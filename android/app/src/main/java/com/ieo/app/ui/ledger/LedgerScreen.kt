package com.ieo.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.expense.ExpenseCategory
import com.ieo.app.ui.component.FilterChip
import com.ieo.app.ui.component.IeoBottomSheet
import com.ieo.app.ui.component.IeoPrimaryButton
import com.ieo.app.ui.component.IeoSecondaryButton
import com.ieo.app.ui.component.IeoTonalButton
import com.ieo.app.ui.component.Keyword
import com.ieo.app.ui.component.ListRow
import com.ieo.app.ui.component.SectionTitle
import com.ieo.app.ui.component.SheetLine
import com.ieo.app.ui.component.SheetTitle
import com.ieo.app.ui.component.Tnum
import com.ieo.app.ui.component.WhiteSection
import com.ieo.app.ui.theme.CanvasGray
import com.ieo.app.ui.theme.DisabledGray
import com.ieo.app.ui.theme.IeoStatus
import com.ieo.app.ui.theme.IeoTheme
import com.ieo.app.ui.theme.TextStrong
import com.ieo.app.ui.theme.TextSub
import com.ieo.app.ui.theme.CardSurface

private val ScreenPadding = 28.dp

/**
 * 장부 탭: 장부 / 미수금 / 경비. 줄을 누르면 상세가, 아래 버튼을 누르면 입력 창이 올라온다.
 * 상태는 [LedgerViewModel]이 정하고, 이 화면은 그리기만 한다.
 */
@Composable
fun LedgerScreen(state: LedgerUiState, actions: LedgerActions) {
    Box(modifier = Modifier.fillMaxSize().background(CanvasGray)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        ) {
            TopBar(state, actions)
            when (state.tab) {
                LedgerTab.ENTRIES -> EntriesTab(state, actions)
                LedgerTab.RECEIVABLES -> ReceivablesTab(state, actions)
                LedgerTab.EXPENSES -> ExpensesTab(state)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IeoTonalButton(text = "경비 추가", onClick = { actions.openForm(LedgerForm.AddExpense) }, modifier = Modifier.weight(1f))
                IeoTonalButton(text = "운행 직접 추가", onClick = { actions.openForm(LedgerForm.AddTrip) }, modifier = Modifier.weight(1f))
            }
        }
        IeoBottomSheet(item = state.detail, onDismiss = actions::close) { detail ->
            EntrySheet(detail, actions)
        }
        IeoBottomSheet(item = state.form, onDismiss = actions::close) { form ->
            when (form) {
                LedgerForm.AddExpense -> ExpenseForm(busy = state.isBusy, onSubmit = actions::addExpense, onCancel = actions::close)
                LedgerForm.AddTrip -> TripForm(busy = state.isBusy, onSubmit = actions::addTrip, onCancel = actions::close)
                is LedgerForm.EditFare -> FareForm(form, busy = state.isBusy, onSubmit = actions::editFare, onCancel = actions::close)
            }
        }
    }
}

@Composable
private fun TopBar(state: LedgerUiState, actions: LedgerActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LedgerTab.entries.forEach { tab ->
            FilterChip(tab.label, selected = state.tab == tab, onClick = { actions.selectTab(tab) })
        }
        Spacer(Modifier.weight(1f))
        // 미수금은 달과 상관없이 전부 보여 주므로 달 이동을 감춘다
        if (state.tab != LedgerTab.RECEIVABLES) {
            MonthPicker(state.monthLabel, canGoNext = state.canGoNext, onPrevious = actions::previousMonth, onNext = actions::nextMonth)
        }
    }
}

@Composable
private fun MonthPicker(label: String, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(CardSurface, RoundedCornerShape(24.dp))) {
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = "이전 달",
            tint = TextStrong,
            modifier = Modifier.clickable(onClick = onPrevious).padding(12.dp),
        )
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "다음 달",
            tint = if (canGoNext) TextStrong else DisabledGray,
            modifier = Modifier.clickable(enabled = canGoNext, onClick = onNext).padding(12.dp),
        )
    }
}

// ───────────────────────── 장부 ─────────────────────────

@Composable
private fun EntriesTab(state: LedgerUiState, actions: LedgerActions) {
    state.summary?.let { summary ->
        WhiteSection(contentPadding = 24.dp) {
            Row {
                Metric("운임·대기비", summary.revenue, TextStrong)
                Metric("경비", summary.expense, TextStrong)
                Metric("실수익", summary.netProfit, IeoStatus.colors.done)
            }
            state.receivable?.let { owed ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "받을 돈 ${owed.total}" + (owed.overdue?.let { " · 그중 ${it}은 지급일이 지났어요" } ?: ""),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (owed.overdue != null) IeoStatus.colors.warning else TextSub,
                    modifier = Modifier.clickable { actions.selectTab(LedgerTab.RECEIVABLES) },
                )
            }
        }
    }
    if (state.days.isEmpty()) {
        EmptySection(if (state.isLoading) "장부를 불러오는 중이에요" else "${state.monthLabel}에는 기록이 없어요")
    }
    state.days.forEach { day ->
        WhiteSection {
            Text(day.title, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = TextSub, modifier = Modifier.padding(horizontal = 12.dp))
            day.entries.forEach { EntryListRow(it, onClick = { actions.openEntry(it.id) }) }
            day.expenses.forEach { ExpenseListRow(it, showDate = false) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Metric(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, fontSize = 24.sp, color = TextSub)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color, style = Tnum, maxLines = 1)
    }
}

@Composable
private fun EntryListRow(row: EntryRow, onClick: () -> Unit) {
    val status = IeoStatus.colors
    ListRow(
        time = null,
        day = null,
        title = row.title,
        tags = listOf(Keyword.payment(row.paymentType)),
        subtitle = row.status,
        subtitleColor = when (row.payState) {
            PayState.PAID -> status.done
            PayState.OVERDUE -> status.warning
            PayState.WAITING -> TextSub
        },
        amount = row.amount,
        amountColor = if (row.payState == PayState.OVERDUE) status.warning else TextStrong,
        amountNote = row.waitFeeNote,
        amountNoteColor = status.waiting,
        onClick = onClick,
    )
}

@Composable
private fun ExpenseListRow(row: ExpenseRow, showDate: Boolean) {
    ListRow(
        time = null,
        day = null,
        title = row.title,
        subtitle = if (showDate) row.date else "경비",
        amount = row.amount,
        amountColor = TextSub,
        onClick = {},
    )
}

// ───────────────────────── 미수금 ─────────────────────────

@Composable
private fun ReceivablesTab(state: LedgerUiState, actions: LedgerActions) {
    state.receivable?.let { owed ->
        WhiteSection(contentPadding = 24.dp) {
            Row {
                Metric("받을 운임", owed.total, TextStrong)
                Metric("지급일 지남", owed.overdue ?: "없음", if (owed.overdue != null) IeoStatus.colors.warning else TextSub)
                Metric("받을 대기비", owed.waitFee ?: "없음", TextStrong)
            }
        }
    }
    WhiteSection {
        SectionTitle(title = "아직 받지 못한 운임", count = state.receivableRows.size)
        if (state.receivableRows.isEmpty()) {
            Text("받을 돈이 모두 들어왔어요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
        }
        state.receivableRows.forEach { EntryListRow(it, onClick = { actions.openEntry(it.id) }) }
    }
}

// ───────────────────────── 경비 ─────────────────────────

@Composable
private fun ExpensesTab(state: LedgerUiState) {
    WhiteSection {
        SectionTitle(title = "${state.monthLabel} 경비", count = state.expenseRows.size, note = state.summary?.expense)
        if (state.expenseRows.isEmpty()) {
            Text("기록한 경비가 없어요", fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
        }
        state.expenseRows.forEach { ExpenseListRow(it, showDate = true) }
    }
}

@Composable
private fun EmptySection(text: String) {
    WhiteSection {
        Text(text, fontSize = 24.sp, color = TextSub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
    }
}

// ───────────────────────── 상세 ─────────────────────────

@Composable
private fun ColumnScope.EntrySheet(detail: EntryDetail, actions: LedgerActions) {
    SheetTitle(detail.title)
    Spacer(Modifier.height(24.dp))
    detail.lines.forEach { (label, value) -> SheetLine(label, value) }
    detail.waitFees.forEach { fee ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(fee.label, fontSize = 24.sp, color = TextSub, modifier = Modifier.weight(1f))
            Text(fee.amount, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextStrong)
            Spacer(Modifier.width(16.dp))
            IeoSecondaryButton(
                text = if (fee.paid) "받음 취소" else "받음",
                onClick = { actions.setWaitFeePaid(fee.id, !fee.paid) },
                modifier = Modifier.width(160.dp),
            )
        }
    }
    if (detail.canMakeWaitProof) {
        Spacer(Modifier.height(16.dp))
        IeoSecondaryButton(text = "대기 증빙 만들기", onClick = actions::makeWaitProof, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IeoTonalButton(
            text = "운임 고치기",
            onClick = { actions.openForm(LedgerForm.EditFare(detail.id, detail.fare, detail.paymentType)) },
            modifier = Modifier.weight(1f),
        )
        if (detail.paid) {
            IeoSecondaryButton(text = "받지 않음으로 되돌리기", onClick = { actions.setPaid(false) }, modifier = Modifier.weight(2f))
        } else {
            IeoPrimaryButton(text = "받음으로 표시", onClick = { actions.setPaid(true) }, modifier = Modifier.weight(2f))
        }
    }
}

@Preview(name = "내비 옆 반 화면", widthDp = 728, heightDp = 1200)
@Composable
private fun LedgerScreenPreview() {
    IeoTheme {
        LedgerScreen(
            state = LedgerUiState(
                monthLabel = "11월",
                summary = MonthSummary("182만 원", "41만 원", "141만 원"),
                receivable = ReceivableSummary("95만 원", "45만 원", "1만 원"),
                days = listOf(
                    DayGroup(
                        "11월 3일 (화) · 1건",
                        listOf(EntryRow(1, "평택 포승 → 대구 성서", "인수증", "○○물류 · 12월 3일 지급 예정", PayState.WAITING, "45만 원", "+ 대기비 1만 원 청구 예정")),
                        listOf(ExpenseRow(2, "주유", "11월 3일 (화)", "−15만 원")),
                    ),
                ),
            ),
            actions = PreviewLedgerActions,
        )
    }
}

private object PreviewLedgerActions : LedgerActions {
    override fun selectTab(tab: LedgerTab) = Unit
    override fun previousMonth() = Unit
    override fun nextMonth() = Unit
    override fun openEntry(entryId: Long) = Unit
    override fun close() = Unit
    override fun setPaid(paid: Boolean) = Unit
    override fun setWaitFeePaid(extraIncomeId: Long, paid: Boolean) = Unit
    override fun openForm(form: LedgerForm) = Unit
    override fun addExpense(category: ExpenseCategory, amount: Long, memo: String?) = Unit
    override fun addTrip(description: String, fare: Long, paymentType: PaymentType, brokerName: String?) = Unit
    override fun editFare(fare: Long, paymentType: PaymentType) = Unit
    override fun makeWaitProof() = Unit
    override fun refresh() = Unit
}
