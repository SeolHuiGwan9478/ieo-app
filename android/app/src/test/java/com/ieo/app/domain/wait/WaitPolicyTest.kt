package com.ieo.app.domain.wait

import org.junit.Assert.assertEquals
import org.junit.Test

class WaitPolicyTest {

    private val policy = WaitPolicy(freeMinutes = 120, unitMinutes = 30, unitAmount = 10_000, maxAmount = 50_000)

    @Test
    fun `무료 시간 안이면 0원`() {
        assertEquals(0L, policy.chargeableAmount(120))
    }

    @Test
    fun `단위에 못 미치면 올림`() {
        assertEquals(10_000L, policy.chargeableAmount(121))
        assertEquals(20_000L, policy.chargeableAmount(151))
    }

    @Test
    fun `내림 정책이면 채운 단위만 센다`() {
        val down = policy.copy(roundUp = false)

        assertEquals(0L, down.chargeableAmount(149))
        assertEquals(10_000L, down.chargeableAmount(150))
    }

    @Test
    fun `상한을 넘지 않는다`() {
        assertEquals(50_000L, policy.chargeableAmount(600))
    }

    @Test
    fun `대기비가 붙기까지 남은 분`() {
        assertEquals(20L, policy.minutesUntilCharged(100))
        assertEquals(0L, policy.minutesUntilCharged(130))
    }
}
