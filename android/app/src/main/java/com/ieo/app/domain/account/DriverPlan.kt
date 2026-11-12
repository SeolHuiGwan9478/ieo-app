package com.ieo.app.domain.account

import java.time.LocalDate

/**
 * 기사님 지금 플랜 (12장 PLAN-007, 008). 차량 앱은 보기만 하고, 바꾸기는 휴대폰 앱에서 한다.
 * code는 BASIC·PRO. daysLeft·paidUntil은 유료일 때만 있다
 */
data class DriverPlan(
    val code: String,
    val label: String,
    val price: Int,
    val highlights: List<String>,
    val trial: Boolean,
    val renews: Boolean,
    val daysLeft: Int?,
    val paidUntil: LocalDate?,
) {
    val paid: Boolean get() = code != "BASIC"
}
