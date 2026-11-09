package com.ieo.app.ui.carlink

import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.vehicle.BodyType
import com.ieo.app.domain.vehicle.CarIdentity
import com.ieo.app.domain.vehicle.PleosMatch
import com.ieo.app.domain.vehicle.Tonnage
import com.ieo.app.domain.vehicle.Vehicle
import com.ieo.app.domain.vehicle.VehicleRepository
import com.ieo.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CarLinkViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val cargo = Vehicle(1, "경기80바1234", Tonnage.TON_5, BodyType.CARGO, 3.6, null, pleosLinked = false, isDefault = true)
    private val box = Vehicle(2, "경기81사5678", Tonnage.TON_1, BodyType.BOX, null, null, pleosLinked = false, isDefault = false)
    private val identity = FakeIdentity("KMHXX00XXXX000001")
    private val server = FakeVehicles()
    private val viewModel = CarLinkViewModel(identity, server)

    @Test
    fun `차량이 한 대라 바로 연결했으면 알려 준다`() = runTest(main.dispatcher) {
        server.match = PleosMatch.Linked(cargo, autoLinked = true)

        viewModel.resolve()
        advanceUntilIdle()

        assertEquals(listOf("resolve KMHXX00XXXX000001"), server.calls)
        assertEquals(CarLinkViewModel.linkedMessage(cargo), viewModel.state.value.message)
    }

    @Test
    fun `이미 연결된 트럭이면 조용히 넘어간다`() = runTest(main.dispatcher) {
        server.match = PleosMatch.Linked(cargo, autoLinked = false)

        viewModel.resolve()
        advanceUntilIdle()

        assertNull(viewModel.state.value.message)
        assertNull(viewModel.state.value.choices)
    }

    @Test
    fun `차량이 여러 대면 골라서 연결한다`() = runTest(main.dispatcher) {
        server.match = PleosMatch.Select(listOf(cargo, box))
        viewModel.resolve()
        advanceUntilIdle()
        assertEquals(listOf("경기80바1234", "경기81사5678"), viewModel.state.value.choices?.map { it.plateNumber })
        assertEquals("1톤 탑차", viewModel.state.value.choices?.last()?.spec)

        viewModel.choose(2)
        advanceUntilIdle()

        assertEquals(listOf("resolve KMHXX00XXXX000001", "link 2 KMHXX00XXXX000001"), server.calls)
        assertNull(viewModel.state.value.choices)
        assertEquals(CarLinkViewModel.linkedMessage(box), viewModel.state.value.message)
    }

    @Test
    fun `등록한 차량이 없으면 휴대폰 앱에서 등록하라고 알려 준다`() = runTest(main.dispatcher) {
        server.match = PleosMatch.Register

        viewModel.resolve()
        advanceUntilIdle()

        assertEquals(CarLinkViewModel.REGISTER_MESSAGE, viewModel.state.value.message)
    }

    @Test
    fun `다른 차주의 트럭이면 내 기본 차량으로 시작한다고 알려 준다`() = runTest(main.dispatcher) {
        server.match = PleosMatch.OtherOwner(cargo)

        viewModel.resolve()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.message!!.endsWith("기본 차량 경기80바1234로 시작해요"))
    }

    @Test
    fun `차대번호를 못 읽으면 서버에 묻지 않는다`() = runTest(main.dispatcher) {
        identity.vin = null

        viewModel.resolve()
        advanceUntilIdle()

        assertTrue(server.calls.isEmpty())
        assertNull(viewModel.state.value.message)
    }

    private class FakeIdentity(var vin: String?) : CarIdentity {
        override suspend fun vin(): String? = vin
    }

    private inner class FakeVehicles : VehicleRepository {
        var match: PleosMatch = PleosMatch.Register
        val calls = mutableListOf<String>()

        override suspend fun resolvePleos(vin: String): ApiResult<PleosMatch> {
            calls += "resolve $vin"
            return ApiResult.Success(match)
        }

        override suspend fun linkPleos(vehicleId: Long, vin: String): ApiResult<Vehicle> {
            calls += "link $vehicleId $vin"
            return ApiResult.Success(listOf(cargo, box).first { it.id == vehicleId }.copy(pleosLinked = true, isDefault = true))
        }

        override suspend fun vehicles(): ApiResult<List<Vehicle>> = ApiResult.Success(listOf(cargo, box))

        override suspend fun update(vehicleId: Long, tonnage: Tonnage, bodyType: BodyType, heightMeters: Double?, dimensions: com.ieo.app.domain.vehicle.VehicleDimensions): ApiResult<Vehicle> =
            error("쓰지 않음")

        override suspend fun setDefault(vehicleId: Long): ApiResult<Vehicle> = error("쓰지 않음")
    }
}
