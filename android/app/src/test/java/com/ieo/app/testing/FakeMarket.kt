package com.ieo.app.testing

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.expense.Expense
import com.ieo.app.domain.expense.ExpenseCategory
import com.ieo.app.domain.expense.ExpenseRepository
import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.LedgerEntry
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.ledger.ManualEntry
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.order.DispatchRequestStatus
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.order.ReportType
import java.time.LocalDate

/** 오더 마켓을 흉내 낸다. 요청하면 그 오더에 내 요청이 붙는다 */
class FakeOrderMarket(var orders: MutableList<Order>) : OrderRepository {

    val calls = mutableListOf<String>()
    var requestError: ApiError? = null

    val searchedNear = mutableListOf<GeoPoint?>()

    override suspend fun openOrders(limit: Int, near: GeoPoint?): ApiResult<List<Order>> {
        searchedNear += near
        return ApiResult.Success(orders.take(limit))
    }

    override suspend fun request(orderId: Long): ApiResult<DispatchRequest> {
        calls += "request $orderId"
        requestError?.let { return ApiResult.Failure(it) }
        val request = DispatchRequest(99, orderId, DispatchRequestStatus.PENDING, TripFixtures.NOW.plusSeconds(600))
        orders = orders.map { if (it.id == orderId) it.copy(myRequest = request) else it }.toMutableList()
        return ApiResult.Success(request)
    }

    override suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest> {
        calls += "withdraw $requestId"
        val order = orders.first { it.myRequest?.id == requestId }
        orders = orders.map { if (it.myRequest?.id == requestId) it.copy(myRequest = null) else it }.toMutableList()
        return ApiResult.Success(order.myRequest!!.copy(status = DispatchRequestStatus.WITHDRAWN))
    }

    override suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit> {
        calls += "report $orderId $type"
        return ApiResult.Success(Unit)
    }
}

/** 장부·경비를 흉내 낸다 */
class FakeBooks(var entries: MutableList<LedgerEntry>, var expenses: MutableList<Expense> = mutableListOf()) :
    LedgerRepository, ExpenseRepository {

    val calls = mutableListOf<String>()

    override suspend fun today() = ApiResult.Success(DailySummary(LocalDate.of(2026, 11, 3), 0, 0))

    override suspend fun receivables(): ApiResult<Receivables> {
        val unpaid = entries.filterNot { it.paid }
        return ApiResult.Success(Receivables(unpaid.sumOf { it.fare }, unpaid.filter { it.overdue }.sumOf { it.fare }, 0, unpaid))
    }

    override suspend fun entries(from: LocalDate, to: LocalDate): ApiResult<List<LedgerEntry>> {
        calls += "entries $from~$to"
        return ApiResult.Success(entries.filter { it.workDate in from..to })
    }

    override suspend fun setPaid(entryId: Long, paid: Boolean): ApiResult<LedgerEntry> {
        calls += "paid $entryId $paid"
        entries = entries.map { if (it.id == entryId) it.copy(paid = paid) else it }.toMutableList()
        return ApiResult.Success(entries.first { it.id == entryId })
    }

    override suspend fun setExtraIncomePaid(extraIncomeId: Long, paid: Boolean) = ApiResult.Success(Unit)

    override suspend fun update(entry: LedgerEntry, fare: Long, paymentType: PaymentType): ApiResult<LedgerEntry> {
        calls += "update ${entry.id} $fare $paymentType"
        return ApiResult.Success(entry.copy(fare = fare, paymentType = paymentType))
    }

    override suspend fun addManual(entry: ManualEntry): ApiResult<LedgerEntry> {
        calls += "manual ${entry.description} ${entry.fare}"
        return ApiResult.Success(TripFixtures.entry(id = 77))
    }

    override suspend fun expenses(from: LocalDate, to: LocalDate) =
        ApiResult.Success(expenses.filter { it.spentOn in from..to })

    override suspend fun add(category: ExpenseCategory, amount: Long, memo: String?): ApiResult<Expense> {
        calls += "expense $category $amount"
        val expense = Expense(expenses.size + 1L, LocalDate.of(2026, 11, 3), category, amount, memo)
        expenses += expense
        return ApiResult.Success(expense)
    }
}
