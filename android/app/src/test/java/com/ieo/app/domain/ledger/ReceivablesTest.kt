package com.ieo.app.domain.ledger

import com.ieo.app.testing.TripFixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceivablesTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `가장 오래 밀린 주선사를 고르고 그 주선사 밀린 운임을 모두 더한다`() {
        val receivables = owed(
            TripFixtures.entry(fare = 300_000, expectedPaymentDate = LocalDate.of(2026, 10, 31), overdue = true),
            TripFixtures.entry(fare = 150_000, expectedPaymentDate = LocalDate.of(2026, 11, 1), overdue = true),
            TripFixtures.entry(fare = 500_000, brokerName = "△△운송", expectedPaymentDate = LocalDate.of(2026, 11, 2), overdue = true),
        )

        assertEquals(OverdueBroker("○○물류", 450_000, overdueDays = 3), receivables.mostOverdue(today))
    }

    @Test
    fun `지급일이 안 지난 운임은 경고하지 않는다`() {
        val receivables = owed(TripFixtures.entry(expectedPaymentDate = LocalDate.of(2026, 11, 10), overdue = false))

        assertNull(receivables.mostOverdue(today))
    }

    private fun owed(vararg entries: LedgerEntry) = Receivables(entries.sumOf { it.fare }, 0, 0, entries.toList())
}
