package com.ieo.app.data.ledger

import retrofit2.Response
import retrofit2.http.GET

interface LedgerApi {

    /** 날짜를 비우면 서버의 오늘(한국 날짜) */
    @GET("api/ledger/daily")
    suspend fun daily(): Response<DailySummaryResponse>

    @GET("api/ledger/receivables")
    suspend fun receivables(): Response<ReceivablesResponse>
}
