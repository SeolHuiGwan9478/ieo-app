package com.ieo.app.domain.ledger

import kotlin.math.roundToInt

/** 운행마다 하차지로 처음 안내받은 경로 거리. 앱을 다시 켜도 남아야 해서 기기에 적어 둔다 */
interface RouteMarks {
    /** 이미 적어 둔 운행이면 그대로 둔다 (다시 안내하거나 경로를 바꿔도 처음 값을 쓴다) */
    fun saveFirst(tripId: Long, meters: Int)

    /** 꺼내고 지운다 */
    fun take(tripId: Long): Int?
}

/**
 * 운행 거리 (LEDG-001): 상차지를 떠나 하차지로 처음 안내받은 내비 경로 거리를 운행을 마칠 때 장부에 넣는다.
 * 차량 주행거리계(Pleos Odometer)는 CAR_MILEAGE 권한이 시스템 앱 전용이라 쓸 수 없다 (2026-11-12 에뮬레이터 확인).
 * 실제로 달린 거리와 다를 수 있어 기사님이 장부에서 고친다. 경로를 모르면 보내지 않는다
 */
class TripDistanceRecorder(
    private val marks: RouteMarks,
    private val ledger: LedgerRepository,
) {

    /** 하차지로 가는 길 안내의 전체 거리를 받았다 */
    fun routeToDropoff(tripId: Long, meters: Int) {
        if (meters > 0) marks.saveFirst(tripId, meters)
    }

    /** 운행을 마쳤다. 보낸 거리(km)를 돌려준다 */
    suspend fun completed(tripId: Long): Int? {
        val km = marks.take(tripId)?.let { (it / 1000.0).roundToInt() }?.takeIf { it in 1..MAX_KM } ?: return null
        ledger.setDistance(tripId, km)
        return km
    }

    private companion object {
        /** 서버가 받는 가장 긴 거리 */
        const val MAX_KM = 3_000
    }
}
