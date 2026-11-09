package com.ieo.app.domain.vehicle

/**
 * 내 차량 (VEH-001). 번호는 바꿀 수 없고, 톤수·차종·높이는 바꿀 수 있다.
 * 높이는 상하차지 높이 제한과 비교하는 데 쓴다 (VEH-002).
 */
data class Vehicle(
    val id: Long,
    val plateNumber: String,
    val tonnage: Tonnage,
    val bodyType: BodyType,
    /** 모르면 null */
    val heightMeters: Double?,
    /** Vehicle SDK 누적 연비로 잰 값 (VEH-003). 아직 없으면 null */
    val measuredKmPerLiter: Double?,
    /** 이 차량이 Pleos 차량과 연결됐는지 (VEH-004) */
    val pleosLinked: Boolean,
    val isDefault: Boolean,
    val dimensions: VehicleDimensions = VehicleDimensions(),
) {
    companion object {
        const val MIN_HEIGHT_METERS = 1.5
        const val MAX_HEIGHT_METERS = 4.2
    }
}

/** 서버 Tonnage와 같은 값 */
enum class Tonnage(val label: String) {
    TON_1("1톤"),
    TON_1_4("1.4톤"),
    TON_2_5("2.5톤"),
    TON_3_5("3.5톤"),
    TON_5("5톤"),
    TON_5_AXLE("5톤 축"),
    TON_8("8톤"),
    TON_11("11톤"),
    TON_14("14톤"),
    TON_18("18톤"),
    TON_25("25톤"),
}

/** 서버 BodyType과 같은 값 */
enum class BodyType(val label: String) {
    CARGO("카고"),
    WING("윙바디"),
    BOX("탑차"),
    REFRIGERATED("냉동·냉장"),
    LIFT("리프트"),
    ETC("기타"),
}
