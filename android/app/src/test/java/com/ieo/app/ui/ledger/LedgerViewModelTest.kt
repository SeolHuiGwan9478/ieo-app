package com.ieo.app.ui.ledger

import com.ieo.app.domain.expense.Expense
import com.ieo.app.domain.expense.ExpenseCategory
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.testing.FakeBooks
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val books = FakeBooks(
        entries = mutableListOf(
            TripFixtures.entry(id = 1, workDate = LocalDate.of(2026, 11, 3), fare = 450_000),
            TripFixtures.entry(
                id = 2,
                workDate = LocalDate.of(2026, 10, 31),
                fare = 450_000,
                brokerName = "△△운송",
                expectedPaymentDate = LocalDate.of(2026, 10, 31),
                overdue = true,
            ),
        ),
        expenses = mutableListOf(Expense(1, LocalDate.of(2026, 11, 3), ExpenseCategory.FUEL, 150_000, null)),
    )
    private val viewModel = LedgerViewModel(books, books, clock = Clock.fixed(NOW, ZoneOffset.UTC))

    @Test
    fun `이번 달 장부와 경비로 요약을 계산하고 날짜별로 묶는다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("11월", state.monthLabel)
        assertEquals(MonthSummary("45만 원", "15만 원", "30만 원"), state.summary)
        assertEquals("11월 3일 (화) · 1건", state.days.single().title)
        assertEquals(1, state.days.single().expenses.size)
        assertFalse(state.canGoNext)
    }

    @Test
    fun `지난달로 가면 그 달 장부를 받고, 이번 달보다 뒤로는 가지 않는다`() = runTest(main.dispatcher) {
        viewModel.previousMonth()
        advanceUntilIdle()

        assertEquals("entries 2026-10-01~2026-10-31", books.calls.last())
        val row = viewModel.state.value.days.single().entries.single()
        assertEquals(PayState.OVERDUE, row.payState)
        assertEquals("△△운송 · 지급일 3일 지남", row.status)

        viewModel.nextMonth()
        advanceUntilIdle()
        viewModel.nextMonth()
        advanceUntilIdle()
        assertEquals("11월", viewModel.state.value.monthLabel)
    }

    @Test
    fun `미수금에는 지급일이 지난 금액을 따로 보여 준다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()

        val owed = viewModel.state.value.receivable!!
        assertEquals("90만 원", owed.total)
        assertEquals("45만 원", owed.overdue)
        assertEquals(2, viewModel.state.value.receivableRows.size)
    }

    @Test
    fun `받음으로 표시하면 미수금에서 빠진다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openEntry(1)

        viewModel.setPaid(true)
        advanceUntilIdle()

        assertTrue("paid 1 true" in books.calls)
        assertEquals(listOf(2L), viewModel.state.value.receivableRows.map { it.id })
        assertTrue(viewModel.state.value.detail!!.paid)
    }

    @Test
    fun `경비를 추가하면 창을 닫고 요약에 더한다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openForm(LedgerForm.AddExpense)

        viewModel.addExpense(ExpenseCategory.TOLL, 23_000, null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.form)
        // 주유 15만 원 + 통행료 2만 3천 원
        assertEquals("173,000원", viewModel.state.value.summary!!.expense)
    }

    @Test
    fun `금액이 없으면 보내지 않는다`() = runTest(main.dispatcher) {
        viewModel.openForm(LedgerForm.AddExpense)

        viewModel.addExpense(ExpenseCategory.FUEL, 0, null)
        advanceUntilIdle()

        assertEquals(LedgerViewModel.AMOUNT_REQUIRED_MESSAGE, viewModel.state.value.message)
        assertTrue(books.calls.none { it.startsWith("expense") })
    }

    @Test
    fun `운임 고치기는 고른 줄의 운임과 결제 방식을 보낸다`() = runTest(main.dispatcher) {
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.openForm(LedgerForm.EditFare(1, 450_000, PaymentType.RECEIPT))

        viewModel.editFare(480_000, PaymentType.PREPAID)
        advanceUntilIdle()

        assertTrue("update 1 480000 PREPAID" in books.calls)
        assertNull(viewModel.state.value.form)
    }
}
