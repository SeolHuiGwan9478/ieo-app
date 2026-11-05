package com.ieo.app.domain.trip

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.navi.Navigator
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleSensors
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 진행 중인 운행의 위치를 서버로 보내는 쪽. 홈이 지금 운행을 알려 준다 */
interface LocationTracker {
    /** 진행 중인 운행이 바뀌면 부른다. null이면 보내기를 멈춘다 */
    fun track(tripId: Long?)
}

/** 서버로 보낼 위치 하나. 남은 거리·시간은 내비가 길 안내 중일 때만 */
data class LocationSample(
    val recordedAt: Instant,
    val point: GeoPoint,
    val remainMeters: Int?,
    val remainSeconds: Int?,
)

/**
 * 진행 중인 운행의 위치를 서버로 보낸다 (TRIP-013).
 * - 주행·정차와 상관없이 [INTERVAL](5분)마다 위치를 하나 잰다
 * - 잰 위치는 쌓아 두고 보낸다. 보내지 못하면(통신 끊김) 다음에 묶어서 다시 보낸다. 최대 [MAX_BUFFERED]개, 넘치면 오래된 것부터 버린다
 * - 서버가 거절하면(운행이 없음 등) 쌓인 것을 버린다. 끝난 운행이면 서버가 받기만 하고 버린다
 *
 * 화면이 다시 만들어져도 끊기지 않도록 앱 전체에서 하나를 쓴다. 홈이 진행 중인 운행을 [track]으로 알려 준다.
 */
class TripLocationReporter(
    private val scope: CoroutineScope,
    private val sensors: VehicleSensors,
    private val navigator: Navigator,
    private val repository: TripRepository,
    private val clock: Clock = Clock.systemUTC(),
) : LocationTracker {

    private var tripId: Long? = null
    private var job: Job? = null
    private val buffer = ArrayDeque<LocationSample>()

    /** 지금 쌓여 있는 위치 수. 테스트와 디버깅용 */
    val pending: Int get() = buffer.size

    /** 같은 운행이면 아무것도 하지 않는다. 운행이 바뀌면 쌓인 위치를 버린다 */
    override fun track(tripId: Long?) {
        if (tripId == this.tripId) return
        job?.cancel()
        this.tripId = tripId
        buffer.clear()
        if (tripId == null) return
        job = scope.launch {
            while (isActive) {
                tick(tripId)
                delay(INTERVAL.toMillis())
            }
        }
    }

    private suspend fun tick(tripId: Long) {
        sample()
        flush(tripId)
    }

    private fun sample() {
        val point = sensors.location.value ?: return
        val now = clock.instant()
        val navi = navigator.progress.value?.takeIf { !it.failed && !it.isWaitingForRoute }
        buffer.addLast(LocationSample(now, point, navi?.remainMeters, navi?.remainSeconds))
        while (buffer.size > MAX_BUFFERED) buffer.removeFirst()
    }

    private suspend fun flush(tripId: Long) {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        when (val result = repository.sendLocations(tripId, batch)) {
            is ApiResult.Success -> repeat(batch.size) { buffer.removeFirstOrNull() }
            is ApiResult.Failure -> if (result.error is ApiError.Server) buffer.clear()
        }
    }

    companion object {
        /** 위치를 재고 보내는 간격 */
        val INTERVAL: Duration = Duration.ofMinutes(5)

        /** 서버가 한 번에 받는 최대 개수. 5분 간격이면 20시간 분량 */
        const val MAX_BUFFERED = 240
    }
}
