package com.ieo.app.data.vehicle

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import com.ieo.app.domain.vehicle.VehicleSensors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 속도·기어는 Android 차량 API(CarPropertyManager), 위치는 GPS에서 받는다.
 * 에뮬레이터 확인: 속도는 m/s로 오고(60km/h → 16.67), 기어는 VehicleGear 값(P 4, D 8),
 * GPS는 `adb emu geo fix`를 따른다. Pleos 내비 위치는 geo fix를 따르지 않아 쓰지 않는다.
 */
class CarSensors(context: Context) : VehicleSensors {

    private val appContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private var car: Car? = null

    private val _motion = MutableStateFlow<VehicleMotion?>(null)
    override val motion: StateFlow<VehicleMotion?> = _motion.asStateFlow()

    private val _location = MutableStateFlow<GeoPoint?>(null)
    override val location: StateFlow<GeoPoint?> = _location.asStateFlow()

    // 속도와 기어는 따로 와서, 마지막 값을 들고 있다가 합친다
    @Volatile private var speedKph: Double? = null
    @Volatile private var gear: Gear = Gear.UNKNOWN

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedKph = ((value.value as? Float) ?: return) * MPS_TO_KPH
                VehiclePropertyIds.GEAR_SELECTION -> gear = gearOf((value.value as? Int) ?: return)
            }
            speedKph?.let { _motion.value = VehicleMotion(it, gear) }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "차량 신호 오류 ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    private val locationListener = LocationListener { _location.value = GeoPoint(it.latitude, it.longitude) }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        startVehicle()
        startLocation()
    }

    private fun startVehicle() {
        runCatching {
            val connected = Car.createCar(appContext).also { car = it }
            val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            for (id in listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)) {
                properties.registerCallback(propertyCallback, id, CarPropertyManager.SENSOR_RATE_NORMAL)
            }
        }.onFailure { Log.w(TAG, "차량 신호를 받을 수 없습니다", it) }
    }

    @SuppressLint("MissingPermission") // start()는 허락을 받은 뒤에만 부른다
    private fun startLocation() {
        runCatching {
            val locations = appContext.getSystemService(LocationManager::class.java)
            locations.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { _location.value = GeoPoint(it.latitude, it.longitude) }
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
        }.onFailure { Log.w(TAG, "위치를 받을 수 없습니다", it) }
    }

    private fun gearOf(value: Int): Gear = when (value) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        // GEAR_DRIVE와 수동 1~9단
        else -> Gear.DRIVE
    }

    private companion object {
        const val TAG = "CarSensors"
        const val MPS_TO_KPH = 3.6
        const val LOCATION_INTERVAL_MS = 2_000L
    }
}
