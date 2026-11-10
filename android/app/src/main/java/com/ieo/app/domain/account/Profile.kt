package com.ieo.app.domain.account

/** 내 정보 (ACC-003). 휴대폰 번호는 가입할 때 인증한 번호라 바꿀 수 없다 */
data class Profile(val name: String?, val phone: String)

/**
 * 서류 인증과 주선사에게 보이는 기록 (서버 /api/drivers/me/record, /api/drivers/me/documents).
 * 서류는 휴대폰 앱에서만 올리고, 차량 앱은 보여 주기만 한다
 */
data class DriverStanding(
    val verified: Boolean,
    val approvedRequired: Int,
    val requiredTotal: Int,
    val completedTripCount: Long,
    val lateCancelCount: Long,
)
