package com.ieo.app.domain.recommendation

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.trip.Trip
import com.ieo.app.domain.trip.TripStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 운송 중 다음 배차를 언제 찾고 언제 안내할지 정한다 (REC-001).
 * - 하차지까지 50%를 지나면 후보를 찾는다
 * - 67%를 지나면 1등 후보를 [current]에 올리고 [present]로 알린다. 운행마다 한 번
 * 운송 중이 아니게 되면(하차지 도착, 운행 끝) 비운다. 찾기에 실패하면 다음 진행률에 다시 찾는다.
 */
class NextOrderAdvisor(
    private val repository: RecommendationRepository,
    /** 1등 후보를 처음 올릴 때 부른다. 음성 안내를 한다 */
    private val present: (Trip, NextOrder) -> Unit,
) {

    private val _current = MutableStateFlow<NextOrder?>(null)

    /** 지금 안내 중인 추천. 없으면 null */
    val current: StateFlow<NextOrder?> = _current.asStateFlow()

    private var tripId: Long? = null
    private var candidates: List<NextOrder>? = null
    private var presented = false

    /** 진행 중인 운행이나 내비 진행률(0~1)이 바뀔 때마다 부른다 */
    suspend fun onProgress(trip: Trip?, fraction: Float?) {
        val inTransit = trip?.takeIf { it.status == TripStatus.IN_TRANSIT }
        if (inTransit?.id != tripId) {
            tripId = inTransit?.id
            candidates = null
            presented = false
            _current.value = null
        }
        if (inTransit == null || fraction == null || presented) return
        if (candidates == null && fraction >= SEARCH_AT) {
            candidates = (repository.recommend(inTransit.id) as? ApiResult.Success)?.data?.items
        }
        if (fraction < PRESENT_AT) return
        val list = candidates ?: return
        presented = true
        val first = list.firstOrNull() ?: return
        _current.value = first
        present(inTransit, first)
        repository.answer(inTransit.id, first.order.id, answer = null)
    }

    companion object {
        /** 후보를 찾기 시작하는 진행률 (TripProgressed 50) */
        const val SEARCH_AT = 0.5f

        /** 첫 안내를 하는 진행률 (TripProgressed 67) */
        const val PRESENT_AT = 0.67f
    }
}
