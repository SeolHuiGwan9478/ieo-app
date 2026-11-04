package com.ieo.app.domain.navi

/**
 * 차량 내비에 저장된 곳. 운행을 직접 등록할 때 상하차지를 키보드 없이 고르는 데 쓴다.
 * 구현은 Pleos NaviHelper를 쓰는 data/navi/NaviGuide.
 */
interface NaviPlaceSource {

    /** 내비 즐겨찾기와 최근 목적지. 내비가 답하지 않으면 빈 목록 */
    suspend fun savedPlaces(): List<NaviPlace>

    /** 지금 차가 있는 곳. 모르면 null */
    suspend fun currentPlace(): NaviPlace?
}

data class NaviPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val kind: Kind,
) {
    enum class Kind { BOOKMARK, RECENT, CURRENT }
}
