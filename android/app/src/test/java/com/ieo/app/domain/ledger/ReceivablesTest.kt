package com.ieo.app.domain.ledger

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceivablesTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `가장 오래 밀린 주선사를 고르고 그 주선사 밀린 운임을 모두 더한다`() {
        val receivables = Receivables(
            listOf(
                Receivable("○○물류", 300_000, LocalDate.of(2026, 10, 31), overdue = true),
                Receivable("○○물류", 150_000, LocalDate.of(2026, 11, 1), overdue = true),
                Receivable("△△운송", 500_000, LocalDate.of(2026, 11, 2), overdue = true),
            ),
        )

        assertEquals(OverdueBroker("○○물류", 450_000, overdueDays = 3), receivables.mostOverdue(today))
    }

    @Test
    fun `지급일이 안 지난 운임은 경고하지 않는다`() {
        val receivables = Receivables(listOf(Receivable("○○물류", 300_000, LocalDate.of(2026, 11, 10), overdue = false)))

        assertNull(receivables.mostOverdue(today))
    }
}
