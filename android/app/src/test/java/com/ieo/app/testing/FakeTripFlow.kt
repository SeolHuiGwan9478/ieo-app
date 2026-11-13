package com.ieo.app.testing

import com.ieo.app.core.network.ApiError
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
import com.ieo.app.domain.order.DispatchRequestStatus
import com.ieo.app.domain.order.Order
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.order.ReportType
import com.ieo.app.domain.order.OrderRepository
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.recommendation.Recommendation
import com.ieo.app.domain.recommendation.RecommendationPreference
import com.ieo.app.domain.recommendation.RecommendationRepository
import com.ieo.app.domain.trip.LocationSample
import com.ieo.app.domain.trip.LocationTracker
import com.ieo.app.domain.trip.NewTrip
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripRepository
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.domain.wait.WaitMark
import com.ieo.app.domain.wait.WaitRecord
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
    TripRepository, WaitRepository, LedgerRepository, OrderRepository, SiteRepository,
    RecommendationRepository {

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

    /** 받은 위치 묶음 */
    val locationBatches = mutableListOf<List<LocationSample>>()

    /** 위치 보내기 결과. 통신 끊김 등을 흉내 낸다 */
    var locationResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun sendLocations(tripId: Long, samples: List<LocationSample>): ApiResult<Unit> {
        locationBatches += samples
        return locationResult
    }

    /** 추천 결과. 기본은 후보 없음 */
    var nextOrders: ApiResult<List<NextOrder>> = ApiResult.Success(emptyList())

    /** 추천을 물은 자리 */
    val recommendRequests = mutableListOf<GeoPoint>()

    /** false면 서버가 같은 곳에서 이미 준 추천을 다시 돌려준 것 */
    var recommendationFresh = true

    override suspend fun recommend(near: GeoPoint): ApiResult<Recommendation> {
        recommendRequests += near
        return when (val result = nextOrders) {
            is ApiResult.Success -> ApiResult.Success(Recommendation(result.data, recommendationFresh))
            is ApiResult.Failure -> result
        }
    }

    /** 추천 설정. 바꾸면 그대로 돌려준다 */
    var recommendationPreference = RecommendationPreference()

    override suspend fun preference() = ApiResult.Success(recommendationPreference)

    override suspend fun changePreference(preference: RecommendationPreference): ApiResult<RecommendationPreference> {
        recommendationPreference = preference
        return ApiResult.Success(preference)
    }

    override suspend fun waits(tripId: Long) = ApiResult.Success(records.values.toList())

    override suspend fun today() = ApiResult.Success(DailySummary(LocalDate.of(2026, 11, 3), tripCount = 1, netProfit = 320_000))

    override suspend fun receivables() = ApiResult.Success(Receivables(0, 0, 0, emptyList()))

    override suspend fun entries(from: LocalDate, to: LocalDate) = ApiResult.Success(emptyList<LedgerEntry>())

    override suspend fun setPaid(entryId: Long, paid: Boolean): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    override suspend fun setExtraIncomePaid(extraIncomeId: Long, paid: Boolean): ApiResult<Unit> = error("홈에서는 쓰지 않음")

    override suspend fun update(entry: LedgerEntry, fare: Long, paymentType: PaymentType, distanceKm: Int?): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    override suspend fun deleteEntry(entryId: Long): ApiResult<Unit> = error("홈에서는 쓰지 않음")

    /** 운행을 마칠 때 보낸 거리 (운행 id → km) */
    val distances = mutableMapOf<Long, Int>()

    override suspend fun setDistance(tripId: Long, distanceKm: Int): ApiResult<Unit> {
        distances[tripId] = distanceKm
        return ApiResult.Success(Unit)
    }

    override suspend fun addManual(entry: ManualEntry): ApiResult<LedgerEntry> = error("홈에서는 쓰지 않음")

    val ordersNear = mutableListOf<GeoPoint?>()

    override suspend fun profit(orderId: Long, from: GeoPoint): ApiResult<com.ieo.app.domain.order.OrderProfit> =
        ApiResult.Failure(com.ieo.app.core.network.ApiError.Network)

    override suspend fun openOrders(limit: Int, near: GeoPoint?, search: com.ieo.app.domain.order.OrderSearch): ApiResult<List<Order>> {
        ordersNear += near
        return ApiResult.Success(listOf(TripFixtures.order(distanceMeters = near?.let { 12_300L })))
    }

    /** 홈의 배차 추천에서 보낸 요청: (오더, 추천에서 눌렀나) */
    val requests = mutableListOf<Pair<Long, Boolean>>()

    override suspend fun request(orderId: Long, isRecommendation: Boolean): ApiResult<DispatchRequest> {
        requests += orderId to isRecommendation
        return ApiResult.Success(DispatchRequest(99, orderId, DispatchRequestStatus.PENDING, TripFixtures.NOW.plusSeconds(600)))
    }

    override suspend fun withdraw(requestId: Long): ApiResult<DispatchRequest> = error("홈에서는 쓰지 않음")

    override suspend fun report(orderId: Long, type: ReportType): ApiResult<Unit> = error("홈에서는 쓰지 않음")

    /** 자동 도착이 보낸 시각 */
    val arrivedAt = mutableListOf<Instant?>()

    /** 통신이 끊긴 것처럼 대기 요청에 Network 실패를 돌려준다 */
    var offline = false

    /** 통신 실패를 돌려주기 전에 부른다. 실패를 기다리는 동안 시간이 흐르는 것을 흉내 낸다 */
    var whileFailing: () -> Unit = {}

    /** 대기 요청마다 함께 보낸 위치와 방법 (WAIT-025) */
    val marks = mutableListOf<WaitMark>()

    override suspend fun arrive(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> {
        if (offline) return ApiResult.Failure(ApiError.Network).also { whileFailing() }
        calls += "arrive $stop"
        marks += mark
        arrivedAt += at
        trip = trip.copy(status = if (stop == StopType.PICKUP) TripStatus.AT_PICKUP else TripStatus.AT_DROPOFF)
        return ApiResult.Success(records.getOrPut(stop) { TripFixtures.wait(stop) })
    }

    override suspend fun card(siteId: Long): ApiResult<SiteCard> {
        siteRequests += siteId
        return ApiResult.Success(TripFixtures.site(siteId))
    }

    override suspend fun sendProof(tripId: Long): ApiResult<WaitProofLink> =
        createProof(tripId)

    override suspend fun createProof(tripId: Long): ApiResult<WaitProofLink> {
        calls += "proof $tripId"
        return ApiResult.Success(WaitProofLink("http://localhost/api/public/wait-proofs/token-$tripId", TripFixtures.NOW.plusSeconds(86_400)))
    }

    override suspend fun startWork(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> {
        if (offline) return ApiResult.Failure(ApiError.Network).also { whileFailing() }
        calls += "work $stop"
        marks += mark
        val record = records.getValue(stop).copy(status = WaitStatus.WORKING, workStartedAt = TripFixtures.NOW)
        records[stop] = record
        return ApiResult.Success(record)
    }

    val departedAt = mutableListOf<Instant?>()

    override suspend fun depart(tripId: Long, stop: StopType, at: Instant?, mark: WaitMark): ApiResult<WaitRecord> {
        if (offline) return ApiResult.Failure(ApiError.Network).also { whileFailing() }
        calls += "depart $stop"
        marks += mark
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

/** 홈이 알려 준 진행 중 운행을 기록한다 */
class FakeLocationTracker : LocationTracker {
    val tracked = mutableListOf<Long?>()

    override fun track(tripId: Long?) {
        tracked += tripId
    }
}
