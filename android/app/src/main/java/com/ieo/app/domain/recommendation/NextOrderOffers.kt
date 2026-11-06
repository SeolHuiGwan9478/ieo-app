package com.ieo.app.domain.recommendation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 수락 창에 어느 후보를 띄울지 정한다 (REC-021~024). 서버와 화면을 모르고 순서만 안다.
 * - 후보를 하나씩 보여 준다. 넘기거나 시간이 끝난 후보는 이 운행에서 다시 띄우지 않는다 (카드로는 남는다)
 * - 수락하면 주선사 응답을 기다리는 동안 다음 후보를 띄우지 않는다. 마감·만료되면 이어 가고, 확정되면 그친다 (REC-023)
 * - 카드를 누르면 그 후보를 바로 띄운다
 */
class NextOrderOffers {

    private val _current = MutableStateFlow<NextOrder?>(null)

    /** 지금 수락 창에 띄울 후보. 없으면 null. 차가 움직이는지는 화면 쪽에서 따로 본다 */
    val current: StateFlow<NextOrder?> = _current.asStateFlow()

    private var tripId: Long? = null
    private var candidates: List<NextOrder> = emptyList()
    private val finished = mutableSetOf<Long>()
    private var opened: Long? = null
    private var awaiting: Long? = null
    private var confirmed = false

    /** 지금 운행의 후보가 바뀌면 부른다. 운행이 바뀌면 처음부터 */
    fun update(tripId: Long?, items: List<NextOrder>) {
        if (tripId != this.tripId) {
            this.tripId = tripId
            finished.clear()
            opened = null
            awaiting = null
            confirmed = false
        }
        candidates = items
        publish()
    }

    /** 카드를 눌렀다 */
    fun open(orderId: Long) {
        opened = orderId
        publish()
    }

    /** 넘겼거나 시간이 끝났다 */
    fun finish(orderId: Long) {
        finished += orderId
        if (opened == orderId) opened = null
        publish()
    }

    fun accepted(orderId: Long) {
        awaiting = orderId
        finish(orderId)
    }

    /** 주선사 응답. 기다리던 요청이 마감·만료되어 다음 후보로 이어 가야 하면 true */
    fun answered(orderId: Long, isConfirmed: Boolean): Boolean {
        if (awaiting != orderId) return false
        awaiting = null
        confirmed = isConfirmed
        publish()
        return !isConfirmed
    }

    private fun publish() {
        val manual = opened?.let { id -> candidates.firstOrNull { it.order.id == id } }
        _current.value = manual
            ?: candidates.firstOrNull { it.order.id !in finished }.takeIf { awaiting == null && !confirmed }
    }
}
