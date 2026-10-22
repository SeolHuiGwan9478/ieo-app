package com.ieo.app.debug

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * 기술 검증용 화면. 속도·기어(CarPropertyManager)와 GPS 위치를 logcat 태그 "IeoProbe"로 찍는다.
 * 실행: adb shell am start -n com.ieo.app/.debug.VehicleProbeActivity
 */
class VehicleProbeActivity : ComponentActivity() {

    private var car: Car? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            Log.i(TAG, "event ${VehiclePropertyIds.toString(value.propertyId)} = ${value.value}")
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "error ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probeVehicle()
        probeLocation()
    }

    private fun probeVehicle() {
        val car = Car.createCar(this).also { car = it }
        val properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val ids = listOf(
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            VehiclePropertyIds.GEAR_SELECTION,
            VehiclePropertyIds.CURRENT_GEAR,
        )
        for (id in ids) {
            val name = VehiclePropertyIds.toString(id)
            runCatching { properties.getProperty<Any>(id, 0) }
                .onSuccess { Log.i(TAG, "get $name = ${it?.value}") }
                .onFailure { Log.w(TAG, "get $name failed: $it") }
            runCatching { properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                .onFailure { Log.w(TAG, "subscribe $name failed: $it") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun probeLocation() {
        val locations = getSystemService(LocationManager::class.java)
        runCatching {
            Log.i(TAG, "last gps = ${locations.getLastKnownLocation(LocationManager.GPS_PROVIDER)}")
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f) {
                Log.i(TAG, "gps = ${it.latitude},${it.longitude} speed=${it.speed}")
            }
        }.onFailure { Log.w(TAG, "location failed: $it") }
    }

    override fun onDestroy() {
        car?.disconnect()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "IeoProbe"
    }
}
