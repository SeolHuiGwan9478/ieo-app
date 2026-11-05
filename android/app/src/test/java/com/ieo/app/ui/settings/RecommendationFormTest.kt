package com.ieo.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationFormTest {

    @Test
    fun `반경은 5km 단위로 5에서 100km 안에서만 바뀐다`() {
        assertEquals(35, stepRadius(30, 5))
        assertEquals(25, stepRadius(30, -5))
        assertEquals(5, stepRadius(5, -5))
        assertEquals(100, stepRadius(100, 5))
        assertEquals(35, stepRadius(33, 5))
    }

    @Test
    fun `최소 실수익이 없으면 조건 없음으로 보여 준다`() {
        assertEquals("조건 없음", minNetProfitLabel(null))
        assertEquals("20만 원 이상", minNetProfitLabel(200_000))
    }
}
