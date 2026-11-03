package com.ieo.app.domain.ledger

import com.ieo.app.core.network.ApiResult

interface LedgerRepository {

    /** 오늘(한국 날짜) 요약 */
    suspend fun today(): ApiResult<DailySummary>

    suspend fun receivables(): ApiResult<Receivables>
}
