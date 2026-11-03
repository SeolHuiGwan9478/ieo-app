package com.ieo.app.data.ledger

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.network.mapResponse
import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.ledger.Receivables

class DefaultLedgerRepository(
    private val api: LedgerApi,
    private val caller: ApiCaller,
) : LedgerRepository {

    override suspend fun today(): ApiResult<DailySummary> =
        caller.call { api.daily() }.mapResponse { it.toDomain() }

    override suspend fun receivables(): ApiResult<Receivables> =
        caller.call { api.receivables() }.mapResponse { it.toDomain() }
}
