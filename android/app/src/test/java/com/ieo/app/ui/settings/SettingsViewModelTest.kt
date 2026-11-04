package com.ieo.app.ui.settings

import com.ieo.app.core.network.ApiResult
import com.ieo.app.core.session.AuthTokens
import com.ieo.app.core.session.SessionManager
import com.ieo.app.domain.account.AccountRepository
import com.ieo.app.domain.account.Profile
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.PleosMatch
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleRepository
import com.ieo.app.testing.MainDispatcherRule
import com.ieo.app.testing.MemoryTokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccount()
    private val garage = FakeGarage()
    private val session = SessionManager(MemoryTokenStore(AuthTokens("access", "refresh")))
    private val vm = SettingsViewModel(account, garage, session, appVersion = "0.1.0")

    @Test
    fun `내 정보와 차량을 받고 기본 차량을 맨 앞에 둔다`() = runTest(main.dispatcher) {
        vm.refresh()
        runCurrent()

        val state = vm.state.value
        assertEquals("김기사", state.profile?.name)
        assertEquals(listOf("경기80바1234", "경기81사5678"), state.vehicles.map { it.plateNumber })
    }

    @Test
    fun `높이가 범위를 벗어나면 서버에 보내지 않는다`() = runTest(main.dispatcher) {
        vm.refresh()
        runCurrent()
        vm.openForm(SettingsForm.EditVehicle(garage.vehicles.first()))

        vm.saveVehicle(Tonnage.TON_5, BodyType.CARGO, heightMeters = 5.0)
        runCurrent()

        assertEquals(SettingsViewModel.HEIGHT_RANGE_MESSAGE, vm.state.value.message)
        assertTrue(garage.updates.isEmpty())
    }

    @Test
    fun `차량을 고치면 창을 닫고 목록을 새로 받는다`() = runTest(main.dispatcher) {
        vm.refresh()
        runCurrent()
        val truck = vm.state.value.vehicles.first { it.id == 2L }
        vm.openForm(SettingsForm.EditVehicle(truck))

        vm.saveVehicle(Tonnage.TON_2_5, BodyType.WING, heightMeters = 3.2)
        runCurrent()

        assertEquals(listOf(2L to 3.2), garage.updates)
        assertNull(vm.state.value.form)
        assertEquals(3.2, vm.state.value.vehicles.first { it.id == 2L }.heightMeters)
    }

    @Test
    fun `기본 차량을 바꾸면 그 차가 맨 앞으로 온다`() = runTest(main.dispatcher) {
        vm.refresh()
        runCurrent()

        vm.setDefault(2)
        runCurrent()

        assertEquals("경기81사5678", vm.state.value.vehicles.first().plateNumber)
    }

    @Test
    fun `빈 이름은 저장하지 않는다`() = runTest(main.dispatcher) {
        vm.saveName("  ")
        runCurrent()

        assertEquals(SettingsViewModel.NAME_REQUIRED_MESSAGE, vm.state.value.message)
        assertEquals("김기사", account.profile.name)
    }

    @Test
    fun `로그아웃하면 이 기기의 로그인을 지운다`() {
        vm.signOut()

        assertFalse(session.isLoggedIn.value)
        assertNull(session.tokens())
    }

    @Test
    fun `높이는 0점1m씩 바뀌고 범위 안에 머문다`() {
        assertEquals(3.5, step(null, HEIGHT_STEP_FOR_TEST), 0.0001)
        assertEquals(3.7, step(3.6, HEIGHT_STEP_FOR_TEST), 0.0001)
        assertEquals(4.2, step(4.2, HEIGHT_STEP_FOR_TEST), 0.0001)
        assertEquals(1.5, step(1.5, -HEIGHT_STEP_FOR_TEST), 0.0001)
    }

    @Test
    fun `휴대폰 번호는 가운데를 가린다`() {
        assertEquals("010-****-5678", maskPhone("01012345678"))
        assertEquals("010-****-5678", maskPhone("010-1234-5678"))
    }

    private class FakeAccount : AccountRepository {
        var profile = Profile(name = "김기사", phone = "01012345678")

        override suspend fun me() = ApiResult.Success(profile)

        override suspend fun updateName(name: String): ApiResult<Profile> {
            profile = profile.copy(name = name)
            return ApiResult.Success(profile)
        }
    }

    private class FakeGarage : VehicleRepository {
        var vehicles = listOf(
            Vehicle(2, "경기81사5678", Tonnage.TON_1, BodyType.BOX, null, null, pleosLinked = false, isDefault = false),
            Vehicle(1, "경기80바1234", Tonnage.TON_5, BodyType.CARGO, 3.6, 4.2, pleosLinked = true, isDefault = true),
        )
        val updates = mutableListOf<Pair<Long, Double?>>()

        override suspend fun vehicles() = ApiResult.Success(vehicles)

        override suspend fun update(vehicleId: Long, tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?): ApiResult<Vehicle> {
            updates += vehicleId to heightMeters
            vehicles = vehicles.map { if (it.id == vehicleId) it.copy(tonnage = tonnage, bodyType = bodyType, heightMeters = heightMeters) else it }
            return ApiResult.Success(vehicles.first { it.id == vehicleId })
        }

        override suspend fun setDefault(vehicleId: Long): ApiResult<Vehicle> {
            vehicles = vehicles.map { it.copy(isDefault = it.id == vehicleId) }
            return ApiResult.Success(vehicles.first { it.id == vehicleId })
        }

        override suspend fun resolvePleos(vin: String): ApiResult<PleosMatch> = error("설정에서는 쓰지 않음")

        override suspend fun linkPleos(vehicleId: Long, vin: String): ApiResult<Vehicle> = error("설정에서는 쓰지 않음")
    }

    private companion object {
        const val HEIGHT_STEP_FOR_TEST = 0.1
    }
}
