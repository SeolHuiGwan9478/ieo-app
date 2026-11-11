package com.ieo.app.domain.wait

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.vehicle.GeoPoint
import java.time.Instant

/** 기사님이 그 순간을 어떻게 알렸나. 대기 증빙에서 주선사가 판단하는 근거다 (WAIT-025) */
enum class WaitSource {
    /** 앱이 감지하고 물었을 때 창의 버튼으로 확인 */
    CONFIRM_TOUCH,

    /** 앱이 감지하고 물었을 때 말로 확인 */
    CONFIRM_VOICE,

    /** 버튼을 직접 누름 */
    BUTTON,
}

/** 그 순간 서버에 함께 남기는 위치와 방법 (WAIT-025). 이동 경로는 남기지 않고 이 한 점만 남긴다 */
data class WaitMark(val location: GeoPoint? = null, val source: WaitSource = WaitSource.BUTTON)

/**
 * 상하차지 대기 기록. 시각은 서버가 받은 때로 남긴다.
 * 들어오면 운행이 "대기 중"으로, 떠나면 다음 단계(운송 중 / 운행 완료)로 서버가 바꾼다.
 * 대기 시작·작업 시작·떠남은 기사님이 확인하거나 누른 순간이고, 그때 위치와 방법([WaitMark])을 같이 남긴다.
 */
interface WaitRepository {

    suspend fun waits(tripId: Long): ApiResult<List<WaitRecord>>

    /**
     * 대기 시작. 이미 시작했으면 있던 기록을 준다.
     * [at]: 도착 확인이면 감지한 때. 비우면 서버가 받은 때
     */
    suspend fun arrive(tripId: Long, stop: StopType, at: Instant? = null, mark: WaitMark = WaitMark()): ApiResult<WaitRecord>

    /** "상차 시작" · "하차 시작". [at]: 통신이 끊겨 나중에 보낼 때 누른 때. 비우면 서버가 받은 때 */
    suspend fun startWork(tripId: Long, stop: StopType, at: Instant? = null, mark: WaitMark = WaitMark()): ApiResult<WaitRecord>

    /**
     * 떠남. 하차지에서 떠나면 운행이 끝난다.
     * [at]: 떠남 확인이면 반경을 벗어나거나 달리기 시작한 때. 비우면 서버가 받은 때
     */
    suspend fun depart(tripId: Long, stop: StopType, at: Instant? = null, mark: WaitMark = WaitMark()): ApiResult<WaitRecord>

    /** 운행의 대기 증빙 링크 (상차지·하차지 함께, 운행에 하나). 이미 있으면 같은 링크 (WAIT-020) */
    suspend fun createProof(tripId: Long): ApiResult<WaitProofLink>

    /** 증빙 링크를 주선사에게 문자로 보낸다 (WAIT-022) */
    suspend fun sendProof(tripId: Long): ApiResult<WaitProofLink>
}
