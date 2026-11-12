package com.ieo.app.data.account

import com.ieo.app.domain.account.Profile
import kotlinx.serialization.Serializable

// 서버 domain/user/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class UserResponse(val phone: String, val name: String? = null) {
    fun toDomain() = Profile(name = name, phone = phone)
}

@Serializable
data class UpdateUserRequest(val name: String)

/** 서버 /api/plans/me 중 차량 앱이 쓰는 칸 */
@Serializable
data class DriverPlanResponse(
    val plan: String,
    val planLabel: String,
    val price: Int,
    val trial: Boolean,
    val renews: Boolean,
    val paidUntil: String? = null,
    val daysLeft: Int? = null,
    val plans: List<Option> = emptyList(),
) {
    @Serializable
    data class Option(val code: String, val highlights: List<String>)

    fun toDomain() = com.ieo.app.domain.account.DriverPlan(
        code = plan,
        label = planLabel,
        price = price,
        highlights = plans.firstOrNull { it.code == plan }?.highlights.orEmpty(),
        trial = trial,
        renews = renews,
        daysLeft = daysLeft,
        paidUntil = paidUntil?.let(java.time.LocalDate::parse),
    )
}
