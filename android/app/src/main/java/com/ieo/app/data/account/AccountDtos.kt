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
