package com.ieo.app.domain.auth

import java.time.Duration
import java.time.Instant

enum class UserRole {
    /** 화물차 기사. 차량 앱은 차주만 쓴다 */
    DRIVER,

    /** 주선사 담당자 (웹 콘솔 사용자) */
    BROKER,
}

/** 인증번호 확인 결과 */
data class PhoneVerification(
    /** 로그인에 한 번 쓰는 값 (10분, 이 기기에서만) */
    val phoneToken: String,
    /** 가입된 계정. 없으면 null */
    val existingAccount: ExistingAccount?,
)

data class ExistingAccount(val name: String, val role: UserRole)

/** 차량 QR 로그인 세션. [pollSecret]은 화면·QR에 넣지 않는다 */
data class CarLoginSession(
    val id: String,
    /** 화면에 보여 줄 코드 (예: KX7P-M4QZ) */
    val code: String,
    /** QR에 담을 주소. 코드만 들어 있다 */
    val qrContent: String,
    val pollSecret: String,
    val expiresAt: Instant,
    val pollInterval: Duration,
)

enum class CarLoginStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CONSUMED,
    EXPIRED;

    /** 더 기다려도 승인될 일이 없음 */
    val isFinished: Boolean get() = this != PENDING
}
