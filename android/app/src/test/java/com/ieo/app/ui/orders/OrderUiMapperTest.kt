package com.ieo.app.ui.orders

import com.ieo.app.data.order.OrderResponse
import com.ieo.app.data.order.OrderSiteResponse
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.testing.TripFixtures
import com.ieo.app.testing.TripFixtures.NOW
import com.ieo.app.ui.common.DayTimeFormatter
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
        assertEquals("냉동·냉장 · 냉동 닭 · 4.2톤 · 지게차", mapper.detail(cold).lines.toMap()["화물"])
    }

    @Test
    fun `일반이나 모르는 종류는 붙이지 않는다`() {
        val general = TripFixtures.order().copy(cargoCategory = CargoCategory.GENERAL)

        assertNull(mapper.row(general).cargo)
        assertEquals("파렛트 · 4.2톤 · 지게차", mapper.detail(general).lines.toMap()["화물"])
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
}
