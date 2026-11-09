package com.ieo.app.domain.trip

import java.time.Instant

/** 운행 한 건 (서버 TripResponse) */
data class Trip(
    val id: Long,
    val status: TripStatus,
    val fare: Long,
    /** "인수증", "선불" 등. 서버가 준 표기 */
    val paymentTypeLabel: String,
    /** Ieo 주선사면 상호, 직접 등록한 운행이면 차주가 적은 이름. 없을 수 있다 */
    val brokerName: String?,
    val plateNumber: String,
    val pickup: TripStop,
    val dropoff: TripStop,
    /** 서버가 주선사에게 도착 예정 시각을 알렸다 */
    val etaShared: Boolean = false,
) {
    fun stop(type: StopType): TripStop = when (type) {
        StopType.PICKUP -> pickup
        StopType.DROPOFF -> dropoff
    }

    /** 지금 향하고 있거나 머무는 곳. 출발 전이거나 끝난 운행이면 null */
    val currentStop: StopType? get() = status.currentStop
}

enum class StopType { PICKUP, DROPOFF }

/** 상차지 또는 하차지 */
data class TripStop(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    /** 이 반경 안에 들어오면 도착으로 본다 */
    val geofenceRadiusMeters: Int,
    /** 예정 시각. 정하지 않았으면 null */
    val plannedAt: Instant?,
    /** 상하차지 정보 카드를 받을 때 쓴다 */
    val siteId: Long,
)

/** 운행 상태. SCHEDULED → TO_PICKUP → AT_PICKUP → IN_TRANSIT → AT_DROPOFF → COMPLETED */
enum class TripStatus {
    SCHEDULED,
    TO_PICKUP,
    AT_PICKUP,
    IN_TRANSIT,
    AT_DROPOFF,
    COMPLETED,
    CANCELLED;

    /** 아직 끝나지 않은 운행 */
    val isActive: Boolean get() = this != COMPLETED && this != CANCELLED

    /** 상하차지로 가는 중 */
    val isDriving: Boolean get() = this == TO_PICKUP || this == IN_TRANSIT

    /** 상하차지에서 대기·작업 중 */
    val isAtStop: Boolean get() = this == AT_PICKUP || this == AT_DROPOFF

    val currentStop: StopType?
        get() = when (this) {
            TO_PICKUP, AT_PICKUP -> StopType.PICKUP
            IN_TRANSIT, AT_DROPOFF -> StopType.DROPOFF
            SCHEDULED, COMPLETED, CANCELLED -> null
        }
}
