package com.ieo.app.ui.home

import com.ieo.app.testing.TripFixtures
import java.time.Clock
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderCardMapperTest {

    private val mapper = OrderCardMapper(Clock.fixed(TripFixtures.NOW, ZoneOffset.UTC))

    @Test
    fun `상차 시각을 한국 날짜로, 차량은 톤수와 차종으로 보여 준다`() {
        val card = mapper.map(TripFixtures.order())

        assertEquals("내일", card.pickupDay)
        assertEquals("06:00", card.pickupTime)
        assertEquals("5톤 윙바디", card.vehicle)
        assertEquals(380_000L, card.fare)
    }

    @Test
    fun `실수익과 지급 소요일은 아직 비워 둔다`() {
        val card = mapper.map(TripFixtures.order())

        assertNull(card.netProfit)
        assertNull(card.brokerPaymentDays)
    }
}
