package com.ieo.app.domain.wait

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.vehicle.GeoPoint
import java.time.Instant

/** 통신이 끊겨 서버에 닿지 못한 대기 요청 하나. [at]은 기사님이 확인하거나 누른 때로, 나중에 보내도 이 시각으로 남는다 */
data class PendingWait(
    val action: Action,
    val tripId: Long,
    val stop: StopType,
    val at: Instant,
    val location: GeoPoint?,
    val source: WaitSource,
) {
    enum class Action { ARRIVE, START_WORK, DEPART }
}

/** 보내지 못한 대기 요청을 앱이 꺼져도 남게 들고 있는다 */
interface PendingWaitStore {
    fun load(): List<PendingWait>
    fun save(pending: List<PendingWait>)
}

class MemoryPendingWaitStore : PendingWaitStore {
    private var pending: List<PendingWait> = emptyList()
    override fun load() = pending
    override fun save(pending: List<PendingWait>) {
        this.pending = pending
    }
}

/**
 * 통신이 끊긴 동안의 대기 요청을 쌓아 두었다가 순서대로 다시 보낸다 (WAIT-025).
 * 대기 시작 → 작업 시작 → 떠남은 순서가 있어, 앞의 것이 아직 못 갔으면 뒤의 것도 기다린다.
 * 서버가 거절하면(이미 끝난 운행 등) 버리고 그 까닭을 돌려준다.
 */
class WaitOutbox(private val repository: WaitRepository, private val store: PendingWaitStore) {

    val isEmpty: Boolean get() = store.load().isEmpty()

    fun add(pending: PendingWait) = store.save(store.load() + pending)

    /** 보낸 것과 서버가 거절한 까닭들. 통신이 아직 안 되면 남은 것은 그대로 둔다 */
    suspend fun flush(): FlushResult {
        var sent = 0
        val refused = mutableListOf<String>()
        for (pending in store.load()) {
            when (val result = send(pending)) {
                is ApiResult.Success -> sent++
                is ApiResult.Failure -> {
                    if (result.error is ApiError.Network) break
                    refused += result.error.message
                }
            }
            store.save(store.load().drop(1))
        }
        return FlushResult(sent, refused)
    }

    private suspend fun send(pending: PendingWait): ApiResult<WaitRecord> {
        val mark = WaitMark(pending.location, pending.source)
        return when (pending.action) {
            PendingWait.Action.ARRIVE -> repository.arrive(pending.tripId, pending.stop, pending.at, mark)
            PendingWait.Action.START_WORK -> repository.startWork(pending.tripId, pending.stop, pending.at, mark)
            PendingWait.Action.DEPART -> repository.depart(pending.tripId, pending.stop, pending.at, mark)
        }
    }

    data class FlushResult(val sent: Int, val refused: List<String>)
}
