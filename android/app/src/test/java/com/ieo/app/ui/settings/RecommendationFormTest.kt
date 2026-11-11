package com.ieo.app.ui.settings

import com.ieo.app.data.recommendation.RecommendationPreferenceBody
import com.ieo.app.domain.order.CargoCategory
import com.ieo.app.domain.recommendation.RecommendationPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `제외할 화물은 눌러 켜고 다시 눌러 끈다`() {
        val once = toggle(emptySet(), CargoCategory.MOVING)
        assertEquals(setOf(CargoCategory.MOVING), once)
        assertEquals(emptySet<CargoCategory>(), toggle(once, CargoCategory.MOVING))
        assertFalse(CargoCategory.GENERAL in EXCLUDABLE_CARGO)
    }

    @Test
    fun `제외할 화물은 목록 순서로 보여 주고 없으면 없음`() {
        assertEquals("없음", excludedCargoLabel(emptySet()))
        assertEquals("냉동·냉장, 이사", excludedCargoLabel(setOf(CargoCategory.MOVING, CargoCategory.COLD)))
    }

    @Test
    fun `제외할 화물을 다 지워도 빈 목록을 보내고 모르는 종류는 버린다`() {
        val body = RecommendationPreferenceBody.of(RecommendationPreference(excludedCargo = emptySet()))
        assertEquals(emptyList<String>(), body.excludedCargoCategories)

        val received = RecommendationPreferenceBody(true, 30, null, listOf("COLD", "FROZEN")).toDomain()
        assertEquals(setOf(CargoCategory.COLD), received.excludedCargo)
    }
}
