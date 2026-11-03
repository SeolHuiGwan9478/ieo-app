package com.ieo.app.domain.wait

/**
 * 대기비 정책: 무료 시간이 지나면 단위 시간마다 금액이 붙고, 상한을 넘지 않는다.
 * 서버 WaitPolicy.calculate와 같은 계산이다.
 */
data class WaitPolicy(
    val freeMinutes: Int,
    val unitMinutes: Int,
    val unitAmount: Long,
    val maxAmount: Long?,
    /** 단위에 못 미치는 시간을 올림(true)할지 버릴지(false) */
    val roundUp: Boolean = true,
) {

    /** [chargeableMinutes]: 대기비를 세기 시작한 뒤 흐른 분 */
    fun chargeableAmount(chargeableMinutes: Long): Long {
        val over = chargeableMinutes - freeMinutes
        if (over <= 0) return 0
        val units = if (roundUp) (over + unitMinutes - 1) / unitMinutes else over / unitMinutes
        val amount = units * unitAmount
        return maxAmount?.let { minOf(amount, it) } ?: amount
    }

    /** 대기비가 붙기까지 남은 분. 이미 붙었으면 0 */
    fun minutesUntilCharged(chargeableMinutes: Long): Long = (freeMinutes - chargeableMinutes).coerceAtLeast(0)
}
