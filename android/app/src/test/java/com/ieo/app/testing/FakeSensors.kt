package com.ieo.app.testing

import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import com.ieo.app.domain.vehicle.VehicleSensors
import kotlinx.coroutines.flow.MutableStateFlow

/** 차량 신호를 손으로 바꾼다 */
class FakeSensors : VehicleSensors {
    override val motion = MutableStateFlow<VehicleMotion?>(null)
    override val location = MutableStateFlow<GeoPoint?>(null)

    override fun start() = Unit

    fun parkAt(point: GeoPoint) {
        location.value = point
        motion.value = VehicleMotion(speedKph = 0.0, gear = Gear.PARK)
    }

    fun drive(point: GeoPoint, speedKph: Double = 60.0) {
        location.value = point
        motion.value = VehicleMotion(speedKph, Gear.DRIVE)
    }
}
