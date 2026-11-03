package com.ieo.app.domain.ledger

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 하루 요약 (서버 /api/ledger/daily). 순수익 = 운임 + 대기비 − 경비 */
data class DailySummary(
    val date: LocalDate,
    val tripCount: Int,
    val netProfit: Long,
)

/** 아직 받지 못한 운임 (서버 /api/ledger/receivables) */
data class Receivables(val entries: List<Receivable>) {

    /**
     * 지급일이 지난 운임을 주선사별로 묶어, 가장 오래 밀린 주선사를 고른다. 밀린 게 없으면 null.
     * 홈 화면에는 경고 한 줄만 보여 준다.
     */
    fun mostOverdue(today: LocalDate): OverdueBroker? = entries
        .filter { it.overdue && it.expectedPaymentDate != null }
        .groupBy { it.brokerName ?: UNKNOWN_BROKER }
        .map { (broker, owed) ->
            val oldest = owed.minOf { it.expectedPaymentDate!! }
            OverdueBroker(broker, owed.sumOf { it.fare }, ChronoUnit.DAYS.between(oldest, today).toInt())
        }
        .maxByOrNull { it.overdueDays }

    private companion object {
        const val UNKNOWN_BROKER = "주선사 미상"
    }
}

data class Receivable(
    val brokerName: String?,
    val fare: Long,
    val expectedPaymentDate: LocalDate?,
    /** 지급 예정일이 지났는데 받지 못함 (서버가 판단) */
    val overdue: Boolean,
)

data class OverdueBroker(val brokerName: String, val amount: Long, val overdueDays: Int)
