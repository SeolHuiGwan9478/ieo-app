package com.ieo.app.domain.trip

import com.ieo.app.core.network.ApiError
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.navi.NaviProgress
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.testing.FakeNavigator
import com.ieo.app.testing.FakeSensors
import com.ieo.app.testing.FakeTripServer
import com.ieo.app.testing.TripFixtures
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripLocationReporterTest {

    private val sensors = FakeSensors()
    private val navigator = FakeNavigator()
    private val server = FakeTripServer(TripFixtures.trip(TripStatus.IN_TRANSIT))
    private val here = GeoPoint(36.35, 127.38)

    private fun reporterTest(body: suspend TestScope.(TripLocationReporter) -> Unit) = runTest {
        val start = Instant.parse("2026-11-05T01:00:00Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)
        }
        body(TripLocationReporter(backgroundScope, sensors, navigator, server, clock))
    }

    /** 몇 초 흘려보낸다. 그 시각에 도는 일까지 끝낸다 */
    private fun TestScope.pass(seconds: Long) {
        advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    @Test
    fun `주행이든 정차든 5분마다 보낸다`() = reporterTest { reporter ->
        sensors.drive(here)
        reporter.track(7)
        runCurrent()
        pass(270)
        assertEquals(1, server.locationBatches.size)

        pass(30)
        assertEquals(2, server.locationBatches.size)

        sensors.parkAt(here)
        pass(270)
        assertEquals(2, server.locationBatches.size)

        pass(30)
        assertEquals(3, server.locationBatches.size)
        assertEquals(0, reporter.pending)
    }

    @Test
    fun `길 안내 중이면 남은 거리와 시간을 싣는다`() = reporterTest { reporter ->
        sensors.drive(here)
        navigator.progress.value = NaviProgress("대전 산업단지", totalMeters = 300_000, remainMeters = 140_000, remainSeconds = 6_000)
        reporter.track(7)
        runCurrent()

        val sample = server.locationBatches.single().single()
        assertEquals(here, sample.point)
        assertEquals(140_000, sample.remainMeters)
        assertEquals(6_000, sample.remainSeconds)
        assertEquals(Instant.parse("2026-11-05T01:00:00Z"), sample.recordedAt)
    }

    @Test
    fun `경로를 기다리는 중이면 남은 거리를 싣지 않는다`() = reporterTest { reporter ->
        sensors.drive(here)
        navigator.progress.value = NaviProgress("대전 산업단지")
        reporter.track(7)
        runCurrent()

        assertNull(server.locationBatches.single().single().remainMeters)
    }

    @Test
    fun `통신이 끊기면 쌓아 두었다가 묶어서 보낸다`() = reporterTest { reporter ->
        sensors.drive(here)
        server.locationResult = ApiResult.Failure(ApiError.Network)
        reporter.track(7)
        runCurrent()
        pass(300)
        pass(300)
        assertEquals(3, reporter.pending)

        server.locationResult = ApiResult.Success(Unit)
        pass(300)

        assertEquals(4, server.locationBatches.last().size)
        assertEquals(0, reporter.pending)
    }

    @Test
    fun `쌓인 위치는 240개를 넘지 않는다`() = reporterTest { reporter ->
        sensors.drive(here)
        server.locationResult = ApiResult.Failure(ApiError.Network)
        reporter.track(7)
        runCurrent()
        repeat(250) { pass(300) }

        assertEquals(TripLocationReporter.MAX_BUFFERED, reporter.pending)
    }

    @Test
    fun `서버가 거절하면 쌓인 위치를 버린다`() = reporterTest { reporter ->
        sensors.drive(here)
        server.locationResult = ApiResult.Failure(ApiError.Server(404, "TRIP_404", "운행을 찾을 수 없습니다."))
        reporter.track(7)
        runCurrent()

        assertEquals(0, reporter.pending)
    }

    @Test
    fun `운행이 끝나면 멈추고 위치를 모르면 보내지 않는다`() = reporterTest { reporter ->
        reporter.track(7)
        runCurrent()
        pass(300)
        assertEquals(0, server.locationBatches.size)

        sensors.drive(here)
        pass(300)
        reporter.track(null)
        pass(600)

        assertEquals(1, server.locationBatches.size)
    }
}
