package com.ieo.app.domain.recommendation

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import com.ieo.app.testing.FakeTripServer
import com.ieo.app.testing.TripFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextOrderAdvisorTest {

    private val server = FakeTripServer(TripFixtures.trip(TripStatus.IN_TRANSIT))
    private val presented = mutableListOf<Pair<Long, Long>>()
    private val advisor = NextOrderAdvisor(server) { trip, next -> presented += trip.id to next.order.id }
    private val inTransit = TripFixtures.trip(TripStatus.IN_TRANSIT)
    private val best = next(orderId = 11, netProfit = 360_000)

    private fun next(orderId: Long, netProfit: Long) =
        NextOrder(TripFixtures.order(id = orderId), emptyMeters = 17_000, fuelCost = 20_000, netProfit = netProfit, longWait = false)

    private fun candidates(vararg items: NextOrder) {
        server.nextOrders = ApiResult.Success(NextOrders(freeAt = TripFixtures.NOW, items = items.toList()))
    }

    @Test
    fun `50%에서 찾고 67%에서 1등을 한 번만 안내한다`() = runTest {
        candidates(best, next(orderId = 12, netProfit = 300_000))

        advisor.onProgress(inTransit, 0.3f)
        assertEquals(emptyList<Long>(), server.recommendRequests)

        advisor.onProgress(inTransit, 0.55f)
        advisor.onProgress(inTransit, 0.6f)
        assertEquals(listOf(1L), server.recommendRequests)
        assertNull(advisor.current.value)

        advisor.onProgress(inTransit, 0.7f)
        advisor.onProgress(inTransit, 0.8f)
        assertEquals(best, advisor.current.value)
        assertEquals(listOf(1L to 11L), presented)
        // 서버에 "안내했다"고 적는다. 대답은 아직 없다
        assertEquals(listOf(Triple(1L, 11L, null as RecommendationAnswer?)), server.answers)
    }

    @Test
    fun `67%를 넘은 뒤 앱을 켜면 찾자마자 안내한다`() = runTest {
        candidates(best)

        advisor.onProgress(inTransit, 0.9f)

        assertEquals(listOf(1L), server.recommendRequests)
        assertEquals(best, advisor.current.value)
    }

    @Test
    fun `후보가 없으면 안내하지 않고 다시 찾지도 않는다`() = runTest {
        advisor.onProgress(inTransit, 0.7f)
        advisor.onProgress(inTransit, 0.8f)

        assertEquals(listOf(1L), server.recommendRequests)
        assertNull(advisor.current.value)
        assertEquals(emptyList<Pair<Long, Long>>(), presented)
    }

    @Test
    fun `찾기에 실패하면 다음 진행률에 다시 찾는다`() = runTest {
        server.nextOrders = ApiResult.Failure(ApiError.Network)
        advisor.onProgress(inTransit, 0.7f)
        assertNull(advisor.current.value)

        candidates(best)
        advisor.onProgress(inTransit, 0.72f)

        assertEquals(listOf(1L, 1L), server.recommendRequests)
        assertEquals(best, advisor.current.value)
    }

    @Test
    fun `하차지에 도착하거나 다른 운행이 되면 비우고 새로 센다`() = runTest {
        candidates(best)
        advisor.onProgress(inTransit, 0.7f)

        advisor.onProgress(TripFixtures.trip(TripStatus.AT_DROPOFF), 1f)
        assertNull(advisor.current.value)

        val another: Trip = TripFixtures.trip(TripStatus.IN_TRANSIT, id = 2)
        advisor.onProgress(another, 0.7f)
        assertEquals(listOf(1L to 11L, 2L to 11L), presented)
    }

    @Test
    fun `운송 전에는 찾지 않는다`() = runTest {
        advisor.onProgress(TripFixtures.trip(TripStatus.TO_PICKUP), 0.9f)
        advisor.onProgress(inTransit, null)

        assertEquals(emptyList<Long>(), server.recommendRequests)
    }
}
