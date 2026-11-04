package com.ieo.app.testing

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.ledger.DailySummary
import com.ieo.app.domain.ledger.LedgerEntry
import com.ieo.app.domain.ledger.LedgerRepository
import com.ieo.app.domain.ledger.ManualEntry
import com.ieo.app.domain.ledger.PaymentType
import com.ieo.app.domain.ledger.Receivables
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.order.DispatchRequest
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.order.ReportType
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.trip.NewTrip
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.delivery.DeliveryProof
import com.ieo.app.domain.delivery.DeliveryProofRepository
import com.ieo.app.domain.delivery.DeliveryProofStatus
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.site.SiteRepository
import com.ieo.app.domain.wait.WaitProofLink
import com.ieo.app.domain.wait.WaitRepository
import com.ieo.app.domain.wait.WaitStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 서버를 흉내 내는 저장소. 서버처럼 도착·출발에 따라 [trip]의 상태를 바꾼다.
 * 장부와 오더는 고정된 값을 준다.
 */
class FakeTripServer(var trip: Trip) :
    TripRepository, WaitRepository, LedgerRepository, OrderRepository, SiteRepository, DeliveryProofRepository {

    val calls = mutableListOf<String>()

    /** 정보 카드를 받은 상하차지. 운행 진행 단계([calls])와 따로 센다 */
    val siteRequests = mutableListOf<Long>()
    private val records = mutableMapOf<StopType, WaitRecord>()

    override suspend fun myTrips() = ApiResult.Success(listOf(trip))

    override suspend fun currentTrip(): ApiResult<Trip?> =
        ApiResult.Success(trip.takeIf { it.status.isActive && it.status != TripStatus.SCHEDULED })

    override suspend fun start(tripId: Long): ApiResult<Trip> {
        calls += "start"
        trip = trip.copy(status = TripStatus.TO_PICKUP)
        return ApiResult.Success(trip)
    }

    /** 직접 등록한 운행 */
    val registered = mutableListOf<NewTrip>()

    override suspend fun register(trip: NewTrip): ApiResult<Trip> {
        registered += trip
        return ApiResult.Success(TripFixtures.trip(TripStatus.SCHEDULED, id = 99))
    }

    override suspend fun waits(tripId: Long) = ApiResult.Success(records.values.toList())

    override suspend fun today() = ApiResult.Success(DailySummary(LocalDate.of(2026, 11, 3), tripCount = 1, netProfit = 320_000))

    override suspend fun receivables() = ApiResult.Success(Receivables(0, 0, 0, emptyList()))

    override suspend fun entries(from: LocalDate, to: LocalDate) = ApiResult.Success(emptyList<LedgerEntry>())

    override suspend fun setPaid(entryId: Long, paid: Boolean): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    override suspend fun setExtraIncomePaid(extraIncomeId: Long, paid: Boolean): ApiResult<Unit> = error("홈에서는 쓰지 않음")

    override suspend fun update(entry: LedgerEntry, fare: Long, paymentType: PaymentType): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    override suspend fun addManual(entry: ManualEntry): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    override suspend fun openOrders(limit: Int): ApiResult<List<Order>> = ApiResult.Success(listOf(TripFixtures.order()))

    override suspend fun request(orderId: Long): ApiResult<DispatchRequest> = error("홈에서는 쓰지 않음")

    override suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest> = error("홈에서는 쓰지 않음")

    override suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit> = error("홈에서는 쓰지 않음")

    /** 자동 도착이 보낸 시각 */
    val arrivedAt = mutableListOf<Instant?>()

    override suspend fun arrive(tripId: Long, stop: StopType, at: Instant?): ApiResult<WaitRecord> {
        calls += "arrive $stop"
        arrivedAt += at
        trip = trip.copy(status = if (stop == StopType.PICKUP) TripStatus.AT_PICKUP else TripStatus.AT_DROPOFF)
        return ApiResult.Success(records.getOrPut(stop) { TripFixtures.wait(stop) })
    }

    private var deliveryStatus = DeliveryProofStatus.PENDING

    /** 수하인이 서명한다. 서버처럼 운행을 끝낸다 (POD-008) */
    fun signDelivery() {
        deliveryStatus = DeliveryProofStatus.SIGNED
        trip = trip.copy(status = TripStatus.COMPLETED)
    }

    private fun deliveryProof() = DeliveryProof(
        url = "http://localhost/api/public/delivery-proofs/token",
        status = deliveryStatus,
        signerName = if (deliveryStatus == DeliveryProofStatus.SIGNED) "김수하" else null,
        signedAt = if (deliveryStatus == DeliveryProofStatus.SIGNED) TripFixtures.NOW else null,
    )

    override suspend fun issue(tripId: Long): ApiResult<DeliveryProof> {
        calls += "delivery proof"
        return ApiResult.Success(deliveryProof())
    }

    override suspend fun get(tripId: Long): ApiResult<DeliveryProof> = ApiResult.Success(deliveryProof())

    override suspend fun card(siteId: Long): ApiResult<SiteCard> {
        siteRequests += siteId
        return ApiResult.Success(TripFixtures.site(siteId))
    }

    override suspend fun createProof(tripId: Long, stop: StopType): ApiResult<WaitProofLink> {
        calls += "proof $stop"
        return ApiResult.Success(WaitProofLink("http://localhost/api/public/wait-proofs/token-$stop", TripFixtures.NOW.plusSeconds(86_400)))
    }

    override suspend fun startWork(tripId: Long, stop: StopType): ApiResult<WaitRecord> {
        calls += "work $stop"
        val record = records.getValue(stop).copy(status = WaitStatus.WORKING, workStartedAt = TripFixtures.NOW)
        records[stop] = record
        return ApiResult.Success(record)
    }

    val departedAt = mutableListOf<Instant?>()

    override suspend fun depart(tripId: Long, stop: StopType, at: Instant?): ApiResult<WaitRecord> {
        calls += "depart $stop"
        departedAt += at
        trip = trip.copy(status = if (stop == StopType.PICKUP) TripStatus.IN_TRANSIT else TripStatus.COMPLETED)
        val record = records.getValue(stop).copy(status = WaitStatus.DONE, departedAt = at ?: TripFixtures.NOW)
        records[stop] = record
        return ApiResult.Success(record)
    }
}

class FakeNavigator : Navigator {

    override val progress = MutableStateFlow<NaviProgress?>(null)
    val guidedTo = mutableListOf<String>()

    override fun startGuidance(destination: Destination): Boolean {
        guidedTo += destination.name
        progress.value = NaviProgress(destinationName = destination.name)
        return true
    }

    override fun refresh() = Unit

    override fun stopGuidance() {
        progress.value = null
    }

    /** 내비가 "목적지 도착"을 알린 것처럼 */
    fun arrive() {
        progress.value = progress.value?.copy(arrived = true, remainMeters = 0, remainSeconds = 0)
    }
}
