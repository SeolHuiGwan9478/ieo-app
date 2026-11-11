package com.ieo.app.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ieo.app.domain.navi.Destination
import com.ieo.app.domain.navi.NaviPlace
import com.ieo.app.domain.navi.NaviPlaceSource
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.navi.Navigator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 차량 내비(Pleos Navigation)에 길 안내를 맡기고 진행 상황을 받는다.
 * Ieo는 직접 길 안내를 하지 않는다 (Pleos 단일 경로 원칙, 요구사항 TRIP-015).
 *
 * 거리는 미터, 시간은 초다 (에뮬레이터 확인: 75km → 75000, 1시간 42분 → 6120).
 * 경로가 정해지기 전에는 내비가 0을 보내므로, 0은 "아직 모름"으로 보고 버린다.
 */
class NaviGuide(context: Context) : Navigator, NaviPlaceSource {

    private val appContext = context.applicationContext
    private val helper = NaviHelper(appContext)
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

        override fun onBookmarkInfo(bookmarkInfos: List<BookmarkInfo>) {
            bookmarks?.complete(bookmarkInfos.map { place(it.name, it.address, it.latitude, it.longitude, NaviPlace.Kind.BOOKMARK) })
        }

        override fun onRecentDestinationInfo(recentDestinationInfos: List<RecentDestinationInfo>) {
            recents?.complete(recentDestinationInfos.map { place(it.name, it.address, it.latitude, it.longitude, NaviPlace.Kind.RECENT) })
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val info = currentLocationInfo
            current?.complete(place(info.address, info.address, info.latitude, info.longitude, NaviPlace.Kind.CURRENT))
        }

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
                    // 장소 ID 없이 좌표만으로 안내된다 (에뮬레이터에서 확인)
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

    // 내비 정보는 요청하고 리스너로 받는다. 요청마다 기다릴 자리를 하나 만든다
    @Volatile private var bookmarks: CompletableDeferred<List<NaviPlace>>? = null
    @Volatile private var recents: CompletableDeferred<List<NaviPlace>>? = null
    @Volatile private var current: CompletableDeferred<NaviPlace?>? = null

    override suspend fun savedPlaces(): List<NaviPlace> {
        val saved = CompletableDeferred<List<NaviPlace>>().also { bookmarks = it }
        val recent = CompletableDeferred<List<NaviPlace>>().also { recents = it }
        runCatching {
            helper.getBookmarkInfo()
            helper.getRecentDestinationInfo()
        }.onFailure { Log.w(TAG, "내비 저장 장소 요청 실패", it) }
        val all = withTimeoutOrNull(PLACES_TIMEOUT_MS) { saved.await() }.orEmpty() +
            withTimeoutOrNull(PLACES_TIMEOUT_MS) { recent.await() }.orEmpty()
        return all.filter { it.address.isNotBlank() && it.latitude != 0.0 }
    }

    override suspend fun currentPlace(): NaviPlace? {
        val here = CompletableDeferred<NaviPlace?>().also { current = it }
        runCatching { helper.getCurrentLocationInfo() }.onFailure { Log.w(TAG, "현재 위치 요청 실패", it) }
        return withTimeoutOrNull(PLACES_TIMEOUT_MS) { here.await() }?.takeIf { it.address.isNotBlank() && it.latitude != 0.0 }
    }

    /** 이름이 비어 있으면 주소를 이름으로 쓴다 (서버는 상하차지 이름을 꼭 받는다) */
    private fun place(name: String?, address: String?, lat: Double, lng: Double, kind: NaviPlace.Kind) =
        NaviPlace(name = name?.takeIf { it.isNotBlank() } ?: address.orEmpty(), address = address.orEmpty(), latitude = lat, longitude = lng, kind = kind)

    override fun addWaypoint(place: Destination): Boolean = runCatching {
        helper.addWaypoint(
            RequestWaypointInfo(
                latitude = place.latitude,
                longitude = place.longitude,
                poiName = place.name,
                waypointIndex = WaypointIndex.FIRST,
                poiId = "",
                address = place.address,
                poiSubId = "",
                poiProvider = "",
            ),
        )
    }.onFailure { Log.w(TAG, "경유지 추가 실패", it) }.isSuccess

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
        if (guiding) {
            runCatching { helper.cancelRoute() }
            // 자동 도착으로 안내를 끝내면 내비가 대기 화면을 덮는다. 잠시 뒤 이에오를 다시 앞으로 (에뮬레이터 확인)
            handler.postDelayed(::bringAppToFront, BRING_BACK_DELAY_MS)
        }
        _progress.value = null
    }

    private fun bringAppToFront() {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { appContext.startActivity(launch) }.onFailure { Log.w(TAG, "이에오를 앞으로 가져오지 못했습니다", it) }
    }

    private companion object {
        const val TAG = "NaviGuide"
        const val ROUTE_TIMEOUT_MS = 30_000L
        const val PLACES_TIMEOUT_MS = 3_000L
        const val BRING_BACK_DELAY_MS = 800L
    }
}
