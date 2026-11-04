package com.ieo.app.domain.site

/**
 * 정차 중 화면의 상하차지 정보 카드 (SITE-007). 다른 차주들이 남긴 정보를 모은 것이다.
 * 아무도 남기지 않은 항목은 null.
 */
data class SiteCard(
    val id: Long,
    val name: String,
    val heightLimitMeters: Double?,
    /** 내 기본 차량 높이와 높이 제한을 비교한 결과 (VEH-002) */
    val heightWarning: HeightWarning,
    val forklift: Boolean?,
    val parking: Boolean?,
    /** 대기 기록이 3건 미만이면 null */
    val averageWaitMinutes: Int?,
    val averageWorkMinutes: Int?,
    /** 최근 메모. 작성자는 익명 */
    val notes: List<String>,
)

enum class HeightWarning {
    /** 차가 높이 제한 이상. 들어갈 수 없다 */
    BLOCKED,

    /** 여유가 0.2m 미만 */
    CAUTION,
    OK,

    /** 차 높이나 높이 제한을 모른다 */
    UNKNOWN,
}
