package com.ieo.app.domain.account

/** 내 정보 (ACC-003). 휴대폰 번호는 가입할 때 인증한 번호라 바꿀 수 없다 */
data class Profile(val name: String?, val phone: String)
