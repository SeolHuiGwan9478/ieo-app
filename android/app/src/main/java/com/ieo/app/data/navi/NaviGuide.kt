package com.ieo.app.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.ieo.app.ui.home.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차량 내비(Pleos Navigation)에 길 안내를 맡기고 진행 상황을 받는다.
 * Ieo는 직접 길 안내를 하지 않는다 (Pleos 단일 경로 원칙, 요구사항 TRIP-015).
 *
 * 거리는 미터, 시간은 초다 (에뮬레이터 확인: 75km → 75000, 1시간 42분 → 6120).
 * 경로가 정해지기 전에는 내비가 0을 보내므로, 0은 "아직 모름"으로 보고 버린다.
 */
class NaviGuide(context: Context) {

    private val helper = NaviHelper(context.applicationContext)

    private val _progress = MutableStateFlow<NaviProgress?>(null)
    /** 안내 중인 경로의 진행 상황. 안내 중이 아니면 null */
    val progress: StateFlow<NaviProgress?> = _progress.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "onRouteStarted $routeStartInfo")
            _progress.update {
                it?.copy(
                    totalMeters = routeStartInfo.distance,
                    remainMeters = routeStartInfo.distance,
                    remainSeconds = routeStartInfo.duration,
                )
            }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val destination = drivingInfo.destination
            if (destination.distance <= 0) return
            _progress.update { it?.copy(remainMeters = destination.distance, remainSeconds = destination.duration) }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.d(TAG, "onDestinationInfo $destinationInfo")
            if (destinationInfo.remainDistance <= 0) return
            _progress.update {
                it?.copy(remainMeters = destinationInfo.remainDistance, remainSeconds = destinationInfo.remainTime)
            }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.d(TAG, "onDestinationArrived $destinationArrivedInfo")
            _progress.update { it?.copy(arrived = true, remainMeters = 0, remainSeconds = 0) }
        }

        override fun onRouteCancelled() {
            Log.d(TAG, "onRouteCancelled")
            _progress.value = null
        }

        // onRouteEnded는 경로 탐색이 끝날 때(안내 시작 직후)에도 와서 안내 종료 신호로 쓰지 않는다

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.d(TAG, "onNavigationStatus $status")
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "onError $errorCode")
            _progress.update { it?.copy(failed = true) }
        }
    }

    fun connect() {
        helper.initialize(
            onConnected = {
                Log.d(TAG, "connected")
                helper.addListener(listener)
            },
            onFailed = { Log.w(TAG, "내비 연결 실패", it) },
        )
    }

    fun release() {
        helper.removeListener(listener)
        helper.release()
    }

    /** 장소까지 길 안내를 요청한다. 내비가 없거나 권한이 없으면 false */
    fun startGuidance(place: Place): Boolean {
        _progress.value = NaviProgress(destinationName = place.name)
        return runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    poiName = place.name,
                    // TODO: 좌표만으로 안내가 되는지 확인 중. 장소 ID가 필요하면 주소 검색 API로 받는다
                    poiId = "",
                    poiSubId = "",
                    address = place.address,
                ),
            )
            // 경로가 잡히면 남은 거리·시간을 한 번 받아 둔다. 이후에는 주행 정보 이벤트로 갱신된다
            helper.getDestinationInfo()
        }.onFailure {
            Log.w(TAG, "길 안내 요청 실패", it)
            _progress.value = null
        }.isSuccess
    }

    /** 남은 거리·시간을 다시 요청한다. 결과는 [progress]로 온다 */
    fun refresh() {
        runCatching { helper.getDestinationInfo() }
    }

    fun stopGuidance() {
        runCatching { helper.cancelRoute() }
        _progress.value = null
    }

    private companion object {
        const val TAG = "NaviGuide"
    }
}

data class NaviProgress(
    val destinationName: String,
    val totalMeters: Int? = null,
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
    val arrived: Boolean = false,
    val failed: Boolean = false,
) {
    /** 0.0 ~ 1.0. 전체 거리를 모르면 null */
    val fraction: Float?
        get() {
            val total = totalMeters ?: return null
            val remain = remainMeters ?: return null
            if (total <= 0) return null
            return ((total - remain).toFloat() / total).coerceIn(0f, 1f)
        }
}
