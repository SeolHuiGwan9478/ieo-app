package com.ieo.app.domain.vehicle

/**
 * 지금 앱이 깔린 트럭이 무엇인지. Pleos 트럭과 내 차량을 잇는 데 쓴다 (VEH-004).
 * 구현은 Pleos Vehicle SDK를 쓰는 data/vehicle/PleosCarIdentity.
 */
interface CarIdentity {
    /** 차대번호(VIN). 읽지 못하면 null. 서버에는 해시로만 남는다 */
    suspend fun vin(): String?

    /** 이 트럭의 누적 연비(L/100km, Pleos 값 그대로). 읽지 못하면 null (VEH-003) */
    suspend fun cumulativeFuelEconomy(): Float?
}

/** 이 트럭이 내 차량 중 어느 것인지 서버가 찾은 결과 */
sealed interface PleosMatch {
    /** 연결된 내 차량이 있어 기본 차량으로 정했다. [autoLinked]면 차량이 한 대뿐이라 방금 연결했다 */
    data class Linked(val vehicle: Vehicle, val autoLinked: Boolean) : PleosMatch

    /** 내 차량이 여러 대라 이 트럭이 어느 것인지 골라야 한다 */
    data class Select(val candidates: List<Vehicle>) : PleosMatch

    /** 등록한 차량이 없다. 차량 등록은 휴대폰 앱에서 한다 */
    data object Register : PleosMatch

    /** 이 트럭은 다른 차주 차량에 연결돼 있다. [myDefault]로 시작한다 */
    data class OtherOwner(val myDefault: Vehicle?) : PleosMatch
}
