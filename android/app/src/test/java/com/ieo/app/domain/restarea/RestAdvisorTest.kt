package com.ieo.app.domain.restarea

import com.ieo.app.domain.trip.RestLevel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestAdvisorTest {

    private fun lounge(name: String, km: Int) =
        RestStop(RestStopKind.LOUNGE, name, "부산", km * 1000, listOf("수면실", "샤워실", "세탁실"), null, 36.0, 127.0)

    private fun shelter(name: String, km: Int) =
        RestStop(RestStopKind.DROWSY_SHELTER, name, null, km * 1000, emptyList(), null, 36.0, 127.0)

    @Test
    fun 십오분_안이면_20km_안의_라운지를_먼저() {
        val advice = RestAdvisor.advise(RestLevel.SOON, listOf(shelter("아포졸음쉼터", 6), lounge("성주휴게소", 18)))
        assertEquals(RestAdvice.Lounge(lounge("성주휴게소", 18)), advice)
    }

    @Test
    fun 라운지가_멀면_졸음쉼터와_다음_라운지() {
        val advice = RestAdvisor.advise(RestLevel.SOON, listOf(shelter("아포졸음쉼터", 12), lounge("영산휴게소", 46)))
        assertEquals(RestAdvice.Shelter(shelter("아포졸음쉼터", 12), lounge("영산휴게소", 46)), advice)
    }

    @Test
    fun 이십km_안에_아무것도_없으면_다음_라운지만() {
        val advice = RestAdvisor.advise(RestLevel.EXTENDED_SOON, listOf(lounge("영산휴게소", 34)))
        assertEquals(RestAdvice.NextLounge(lounge("영산휴게소", 34)), advice)
    }

    @Test
    fun 지금_쉬어야_하면_종류와_상관없이_가장_가까운_곳() {
        val advice = RestAdvisor.advise(RestLevel.NOW, listOf(shelter("창녕졸음쉼터", 6), lounge("영산휴게소", 9)))
        assertEquals(RestAdvice.Shelter(shelter("창녕졸음쉼터", 6), lounge("영산휴게소", 9)), advice)
        assertNull(RestAdvisor.advise(RestLevel.EXTENDED_NOW, emptyList()))
    }

    @Test
    fun 받은_뒤_달린_만큼_내비_남은_거리로_빼고_2km_안은_뺀다() {
        val held = RouteRestStopsAt(1, listOf(lounge("가까운휴게소", 5), lounge("성주휴게소", 18)), "출처", Instant.EPOCH, 100_000)

        val now = held.current(remainMetersNow = 96_000)

        assertEquals(listOf("성주휴게소"), now.map { it.name })
        assertEquals(14_000, now.single().aheadMeters)
        assertEquals(2, held.current(remainMetersNow = null).size)
    }
}
