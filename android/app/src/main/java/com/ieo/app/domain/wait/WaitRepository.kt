package com.ieo.app.domain.wait

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.trip.StopType

/**
 * 상하차지 대기 기록. 시각은 서버가 받은 때로 남긴다.
 * 들어오면 운행이 "대기 중"으로, 떠나면 다음 단계(운송 중 / 운행 완료)로 서버가 바꾼다.
 */
interface WaitRepository {

    suspend fun waits(tripId: Long): ApiResult<List<WaitRecord>>

    /** 상하차지 도착. 이미 도착했으면 있던 기록을 준다 */
    suspend fun arrive(tripId: Long, stop: StopType): ApiResult<WaitRecord>

    /** "상차 시작" · "하차 시작" */
    suspend fun startWork(tripId: Long, stop: StopType): ApiResult<WaitRecord>

    /** 떠남. 하차지에서 떠나면 운행이 끝난다 */
    suspend fun depart(tripId: Long, stop: StopType): ApiResult<WaitRecord>

    /** 대기 증빙 링크. 주선사에게 바로 보내기는 알림(09)을 만든 뒤 붙인다 (WAIT-022) */
    suspend fun createProof(tripId: Long, stop: StopType): ApiResult<WaitProofLink>
}
