package com.ieo.app.domain.recommendation

import com.ieo.app.testing.TripFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextOrderOffersTest {

    private val offers = NextOrderOffers()

    private fun next(id: Long) = NextOrder(TripFixtures.order(id = id), 1_000, 100, 300_000, false)

    private val currentId get() = offers.current.value?.order?.id

    @Test
    fun `수락한 뒤 후보 목록이 바뀌어도 응답을 기다리는 동안 다음 후보를 띄우지 않는다`() {
        offers.update(1, listOf(next(11), next(12)))
        assertEquals(11L, currentId)

        offers.accepted(11)
        offers.update(1, listOf(next(12)))

        assertNull(offers.current.value)
    }

    @Test
    fun `기다리던 요청이 마감되면 이어 가고 다른 요청의 응답은 무시한다`() {
        offers.update(1, listOf(next(11), next(12)))
        offers.accepted(11)

        assertFalse(offers.answered(99, isConfirmed = false))
        assertNull(offers.current.value)

        assertTrue(offers.answered(11, isConfirmed = false))
        assertEquals(12L, currentId)
    }

    @Test
    fun `확정되면 그치지만 카드를 누르면 그 후보는 연다`() {
        offers.update(1, listOf(next(11), next(12)))
        offers.accepted(11)
        assertFalse(offers.answered(11, isConfirmed = true))
        assertNull(offers.current.value)

        offers.open(12)
        assertEquals(12L, currentId)
    }

    @Test
    fun `운행이 바뀌면 처음부터 다시 띄운다`() {
        offers.update(1, listOf(next(11)))
        offers.finish(11)
        assertNull(offers.current.value)

        offers.update(2, listOf(next(11)))
        assertEquals(11L, currentId)
    }
}
