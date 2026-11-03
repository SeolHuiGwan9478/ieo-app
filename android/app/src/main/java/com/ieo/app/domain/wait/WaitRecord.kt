package com.ieo.app.domain.wait

import com.ieo.app.domain.trip.StopType
import java.time.Instant

/**
 * 상하차지 한 곳의 대기 기록 (서버 WaitRecord). 상태와 금액은 서버가 시각으로 계산해 준다.
 * 화면은 진행 중인 타이머를 그리려고 [chargeableFrom]과 [policy]로 금액을 직접 다시 계산한다.
 */
data class WaitRecord(
    val stopType: StopType,
    val status: WaitStatus,
    /** 상하차지에 들어온 시각 */
    val arrivedAt: Instant,
    val workStartedAt: Instant?,
    val departedAt: Instant?,
    /** 대기비를 세기 시작하는 시각 = 진입과 예정 시각 중 늦은 쪽 */
    val chargeableFrom: Instant,
    /** 작업을 시작해 대기가 끝났을 때만 서버가 준다 */
    val chargeableAmount: Long?,
    /** 주선사 대기비 정책. 직접 등록한 운행이면 null */
    val policy: WaitPolicy?,
)

enum class WaitStatus {
    /** 들어왔고 작업 전 */
    WAITING,

    /** 작업 중 */
    WORKING,

    /** 떠남 */
    DONE,
}
