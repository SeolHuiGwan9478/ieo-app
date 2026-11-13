package com.ieo.app.domain.link

import com.ieo.app.core.network.ApiResult
import java.time.Instant

/**
 * 단골 주선사 연결 (BRK-002). 주선사가 내 번호로 요청하고, 내가 휴대폰에서 수락하면 연결된다.
 * 차량 앱은 받은 요청과 연결된 회사를 보여 주기만 한다 (2026-11-13, 관리는 휴대폰에서만).
 * 수락은 "그 회사 일을 하는 동안 위치·도착 예정 시각을 그 회사에 공유"하는 동의다.
 * 연결된 회사는 나에게만 보이는 지정 배차를 보낼 수 있다.
 */
data class DriverLink(
    val id: Long,
    val brokerName: String,
    val status: DriverLinkStatus,
    /** 받은 요청(PENDING)이면 이때까지 수락할 수 있다 */
    val expiresAt: Instant?,
    /** 연결됨(LINKED)이면 수락한 때 */
    val linkedAt: Instant?,
)

/** 서버 DriverLinkStatus와 같은 값 */
enum class DriverLinkStatus { PENDING, LINKED, DECLINED, CANCELED, UNLINKED, EXPIRED }

interface DriverLinkRepository {

    /** 내 번호로 온 요청 (대기 중인 것만). 차량에서는 보여 주기만 하고 수락·거절은 휴대폰에서 한다 */
    suspend fun invitations(): ApiResult<List<DriverLink>>

    /** 연결된 회사 */
    suspend fun links(): ApiResult<List<DriverLink>>
}
