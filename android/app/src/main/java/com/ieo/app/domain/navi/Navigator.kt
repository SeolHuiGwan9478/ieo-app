package com.ieo.app.domain.navi

import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 내비게이션. Ieo는 직접 길 안내를 하지 않고 차량 내비에 맡긴다 (요구사항 TRIP-015).
 * 구현은 Pleos NaviHelper를 쓰는 data/navi/NaviGuide. 앱 전체에서 하나를 쓰고, 만들 때 내비에 연결한다.
 */
interface Navigator {

    /** 안내 중인 경로의 진행 상황. 안내 중이 아니면 null */
    val progress: StateFlow<NaviProgress?>

    /** 길 안내 요청. 내비가 없거나 권한이 없으면 false */
    fun startGuidance(destination: Destination): Boolean

    /** 남은 거리·시간을 다시 받는다. 결과는 [progress]로 온다 */
    fun refresh()

    fun stopGuidance()
}

data class Destination(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

data class NaviProgress(
    val destinationName: String,
    val totalMeters: Int? = null,
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
    /** 내비가 "목적지 도착"을 알림 */
    val arrived: Boolean = false,
    /** 내비가 오류를 알렸거나, 요청 뒤 경로를 받지 못함 */
    val failed: Boolean = false,
) {
    /** 길 안내를 요청하고 아직 경로를 받지 못함 */
    val isWaitingForRoute: Boolean
        get() = totalMeters == null && !arrived && !failed

    /** 0.0 ~ 1.0. 전체 거리를 모르면 null */
    val fraction: Float?
        get() {
            val total = totalMeters ?: return null
            val remain = remainMeters ?: return null
            if (total <= 0) return null
            return ((total - remain).toFloat() / total).coerceIn(0f, 1f)
        }
}
