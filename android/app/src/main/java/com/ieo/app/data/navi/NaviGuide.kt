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
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.navi.Navigator
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
class NaviGuide(context: Context) : Navigator {

    private val helper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 내비가 막 켜지는 중이면 요청을 답 없이 버린다. 이때는 안내 실패로 보고 "길 안내 다시"를 보여 준다.
     * 에뮬레이터에서 경로 탐색에 24초가 걸려 30초를 기다린다
     */
    private val routeTimeout = Runnable {
        _progress.update { if (it != null && it.isWaitingForRoute) it.copy(failed = true) else it }
    }

    private val _progress = MutableStateFlow<NaviProgress?>(null)
    override val progress: StateFlow<NaviProgress?> = _progress.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "onRouteStarted $routeStartInfo")
            handler.removeCallbacks(routeTimeout)
            _progress.update {
                it?.copy(
                    totalMeters = routeStartInfo.distance,
                    remainMeters = routeStartInfo.distance,
                    remainSeconds = routeStartInfo.duration,
                    // 기다리는 시간이 지난 뒤에 경로가 잡혀도 안내 중으로 되돌린다
                    failed = false,
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
            // 안내 중에 새 길 안내를 요청하면 내비가 앞 경로를 먼저 취소한다. 요청한 경로를 기다리는 중이면 무시한다
            _progress.update { if (it != null && it.isWaitingForRoute) it else null }
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

    /**
     * 앱이 살아 있는 동안 연결을 유지한다. 화면(ViewModel)마다 연결·해제하면
     * 화면이 다시 만들어질 때 앞 화면의 해제가 새 화면의 리스너까지 지워 안내 정보가 끊긴다 (에뮬레이터 확인)
     */
    init {
        helper.initialize(
            onConnected = {
                Log.d(TAG, "connected")
                helper.addListener(listener)
            },
            onFailed = { Log.w(TAG, "내비 연결 실패", it) },
        )
    }

    override fun startGuidance(destination: Destination): Boolean {
        _progress.value = NaviProgress(destinationName = destination.name)
        handler.removeCallbacks(routeTimeout)
        handler.postDelayed(routeTimeout, ROUTE_TIMEOUT_MS)
        return runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                    poiName = destination.name,
                    // TODO: 좌표만으로 안내가 되는지 확인 중. 장소 ID가 필요하면 주소 검색 API로 받는다
                    poiId = "",
                    poiSubId = "",
                    address = destination.address,
                ),
            )
            // 경로가 잡히면 남은 거리·시간을 한 번 받아 둔다. 이후에는 주행 정보 이벤트로 갱신된다
            helper.getDestinationInfo()
        }.onFailure {
            Log.w(TAG, "길 안내 요청 실패", it)
            handler.removeCallbacks(routeTimeout)
            _progress.value = null
        }.isSuccess
    }

    override fun refresh() {
        runCatching { helper.getDestinationInfo() }
    }

    /**
     * 안내 취소를 보내면 내비가 앞으로 나온다. 그래서 안내 중일 때만 보낸다.
     * 이미 안내가 꺼졌거나(내비에서 종료), 내비가 도착을 알려 안내를 스스로 끝낸 때는 보내지 않는다
     */
    override fun stopGuidance() {
        handler.removeCallbacks(routeTimeout)
        val guiding = _progress.value?.let { !it.arrived } ?: false
        if (guiding) runCatching { helper.cancelRoute() }
        _progress.value = null
    }

    private companion object {
        const val TAG = "NaviGuide"
        const val ROUTE_TIMEOUT_MS = 30_000L
    }
}
