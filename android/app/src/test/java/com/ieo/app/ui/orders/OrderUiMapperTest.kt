package com.ieo.app.ui.orders

import com.ieo.app.data.order.OrderResponse
import com.ieo.app.data.order.OrderSiteResponse
import com.ieo.app.data.order.BrokerMetricsResponse
import com.ieo.app.domain.order.BrokerMetrics
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.order.OrderProfit
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.component.Keyword
import java.time.Clock
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderUiMapperTest {

    private val mapper = OrderUiMapper(DayTimeFormatter(Clock.fixed(NOW, ZoneOffset.UTC)))

    @Test
    fun `일반이 아닌 화물은 목록 태그와 상세 앞에 종류를 붙인다`() {
        val cold = TripFixtures.order().copy(cargoCategory = CargoCategory.COLD, cargoType = "냉동 닭")

        assertEquals(CargoCategory.COLD, mapper.row(cold).cargo)
        val detail = mapper.detail(cold)
        assertEquals("냉동 닭 · 4.2톤", detail.cargo)
        assertEquals(listOf("5톤 윙바디", "냉동·냉장", "인수증"), detail.tags.map { it.text })
        assertEquals("지게차", detail.loadingTag?.text)
    }

    @Test
    fun `상차지까지 거리에 상하차지 이름이나 현 위치를 붙인다`() {
        val order = TripFixtures.order()

        val atStop = mapper.detail(order, OrderProfit(300_000, 50_000, 6_000, 2_400, originName = "부산 신항"))
        assertEquals("부산 신항에서 2km", atStop.profitLines.toMap()["상차지까지"])
        val here = mapper.detail(order, OrderProfit(300_000, 50_000, 6_000, 2_400))
        assertEquals("현 위치에서 2km", here.profitLines.toMap()["상차지까지"])
    }

    @Test
    fun `일반이나 모르는 종류는 붙이지 않는다`() {
        val general = TripFixtures.order().copy(cargoCategory = CargoCategory.GENERAL)

        assertNull(mapper.row(general).cargo)
        assertEquals("파렛트 · 4.2톤", mapper.detail(general).cargo)
        assertEquals(2, mapper.detail(general).tags.size)
        assertNull(mapper.row(TripFixtures.order()).cargo)
    }

    @Test
    fun `서버 화물 종류를 읽고 처음 보는 값은 버린다`() {
        fun response(category: String?) = OrderResponse(
            id = 1,
            brokerName = "한빛",
            pickup = OrderSiteResponse("칠곡", "경북 칠곡군", 35.9, 128.5),
            dropoff = OrderSiteResponse("부산", "부산 강서구", 35.1, 128.8),
            pickupFrom = "2026-11-03T21:00:00Z",
            pickupUntil = "2026-11-03T23:00:00Z",
            dropoffBy = "2026-11-04T09:00:00Z",
            tonnageLabel = "5톤",
            bodyTypeLabel = "윙바디",
            cargoCategory = category,
            fare = 300_000,
            paymentTypeLabel = "인수증",
        )

        assertEquals(CargoCategory.MOVING, response("MOVING").toDomain().cargoCategory)
        assertNull(response("LIVESTOCK").toDomain().cargoCategory)
        assertNull(response(null).toDomain().cargoCategory)
    }

    @Test
    fun `주선사 지표는 상세에 이어 붙이고 없는 값은 뺀다`() {
        val metrics = BrokerMetrics(published = true, avgPaymentDays = 12, latePaymentPercent = 8, avgWaitMinutes = 35)
        val order = TripFixtures.order().copy(brokerMetrics = metrics)

        assertEquals(metrics, mapper.row(order).brokerMetrics)
        assertEquals(
            "평균 지급 12일 · 지급 지연 8% · 이 회사 운행 평균 대기 35분",
            mapper.detail(order).brokerNote,
        )
    }

    @Test
    fun `운행이 모자란 주선사는 거래 기록 적음이고 지표가 없으면 줄을 뺀다`() {
        val building = TripFixtures.order().copy(brokerMetrics = BrokerMetrics(published = false))

        // 기록이 적으면 이름 옆 태그만 두고 설명 줄은 뺀다
        assertEquals("거래 기록 적음", mapper.detail(building).brokerTag?.text)
        assertNull(mapper.detail(building).brokerNote)
        assertNull(mapper.detail(TripFixtures.order()).brokerTag)
    }

    @Test
    fun `지급 태그는 공개 여부와 평균 일수로 정한다`() {
        assertEquals("거래 기록 적음", Keyword.brokerPayment(BrokerMetrics(published = false)).text)
        assertEquals("지급 이력 없음", Keyword.brokerPayment(BrokerMetrics(published = true)).text)
        assertEquals("지급 평균 12일", Keyword.brokerPayment(BrokerMetrics(published = true, avgPaymentDays = 12)).text)
    }

    @Test
    fun `서버 지표를 읽는다`() {
        val metrics = BrokerMetricsResponse(published = true, avgPaymentDays = 27, waitFeePaidPercent = 50).toDomain()

        assertEquals(27, metrics.avgPaymentDays)
        assertEquals(50, metrics.waitFeePaidPercent)
        assertNull(metrics.latePaymentPercent)
    }
}
