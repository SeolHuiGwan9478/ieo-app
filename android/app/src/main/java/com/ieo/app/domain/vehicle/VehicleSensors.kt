package com.ieo.app.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 신호: 속도·기어·주차 브레이크와 위치. 도착 감지(WAIT-001)와 터치 허용(정차)에 쓴다.
 * 구현은 Android 차량 API(CarPropertyManager)와 GPS를 쓰는 data/vehicle/CarSensors.
 * 허락을 받지 못했거나 아직 값이 오지 않았으면 null.
 */
interface VehicleSensors {
    val motion: StateFlow<VehicleMotion?>
    val location: StateFlow<GeoPoint?>

    /** 속도·위치 허락을 받은 뒤 부른다. 여러 번 불러도 한 번만 시작한다 */
    fun start()
}

data class VehicleMotion(
    val speedKph: Double,
    val gear: Gear,
    /** 주차 브레이크가 채워져 있다. P가 없는 화물차(수동·자동화 수동 변속기)는 이것으로 세워 둔 것을 안다 */
    val parkingBrake: Boolean = false,
) {
    /** 기어 P이거나 거의 멈춰 있다. 길 안내를 이어 갈 때(차가 움직이기 시작했나)만 쓴다 */
    val isStopped: Boolean
        get() = gear == Gear.PARK || speedKph < STOPPED_BELOW_KPH

    /**
     * 세워 둔 정차: 기어 P 또는 주차 브레이크 (2026-11-11 결정, WAIT-001).
     * 도착 감지와 화면 터치(도착 확인·추천 수락·대기 시작)는 이때만 한다. 발 브레이크로 잠깐 선 것은 아니다
     */
    val isParked: Boolean
        get() = gear == Gear.PARK || parkingBrake

    companion object {
        /** 이보다 느리면 정차로 본다. 신호 대기 뒤 천천히 굴러가는 것은 정차가 아니다 */
        const val STOPPED_BELOW_KPH = 3.0
    }
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

data class GeoPoint(val latitude: Double, val longitude: Double) {

    /** 두 점 사이 거리(m). 상하차지 반경(수백 m)을 재는 데는 구면 근사로 충분하다 */
    fun distanceTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(other.longitude - longitude)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
