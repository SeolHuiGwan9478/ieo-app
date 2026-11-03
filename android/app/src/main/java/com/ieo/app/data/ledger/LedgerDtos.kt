package com.ieo.app.data.ledger

import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.Receivable
import com.ieo.app.domain.ledger.Receivables
import java.time.LocalDate
import kotlinx.serialization.Serializable

// 서버 domain/ledger/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class DailySummaryResponse(val date: String, val tripCount: Int, val netProfit: Long) {
    fun toDomain() = DailySummary(date = LocalDate.parse(date), tripCount = tripCount, netProfit = netProfit)
}

@Serializable
data class ReceivablesResponse(val entries: List<LedgerEntryResponse>) {
    fun toDomain() = Receivables(entries.map(LedgerEntryResponse::toReceivable))
}

@Serializable
data class LedgerEntryResponse(
    val brokerName: String? = null,
    val fare: Long,
    val expectedPaymentDate: String? = null,
    val overdue: Boolean,
) {
    fun toReceivable() = Receivable(
        brokerName = brokerName,
        fare = fare,
        expectedPaymentDate = expectedPaymentDate?.let(LocalDate::parse),
        overdue = overdue,
    )
}
