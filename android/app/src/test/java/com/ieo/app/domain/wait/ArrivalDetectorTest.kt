package com.ieo.app.domain.wait

import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalDetectorTest {

    private val site = GeoPoint(36.9690, 126.8410)
    private val fence = GeoFence(site, radiusMeters = 200)
    // 상하차지에서 약 110m 북쪽 (반경 안), 약 1.1km 북쪽 (반경 밖)
    private val inside = GeoPoint(36.9700, 126.8410)
    private val outside = GeoPoint(36.9790, 126.8410)
    private val parked = VehicleMotion(0.0, Gear.PARK)
    private val rolling = VehicleMotion(30.0, Gear.DRIVE)
    private val t0 = Instant.parse("2026-11-04T01:00:00Z")
    private val detector = ArrivalDetector()

    @Test
    fun `반경 안에서 3분 서 있으면 멈추기 시작한 때로 도착한다`() {
        assertEquals(ArrivalCheck.Stopping(Duration.ofMinutes(3)), detector.check(t0, fence, inside, parked))
        assertEquals(ArrivalCheck.Stopping(Duration.ofSeconds(60)), detector.check(t0.plusSeconds(120), fence, inside, parked))

        assertEquals(ArrivalCheck.Arrived(t0), detector.check(t0.plusSeconds(180), fence, inside, parked))
    }

    @Test
    fun `기어가 D여도 속도가 0이면 정차로 본다`() {
        val stopped = VehicleMotion(0.0, Gear.DRIVE)
        detector.check(t0, fence, inside, stopped)

        assertEquals(ArrivalCheck.Arrived(t0), detector.check(t0.plusSeconds(180), fence, inside, stopped))
    }

    @Test
    fun `반경 밖에서는 서 있어도 도착이 아니다`() {
        detector.check(t0, fence, outside, parked)

        assertEquals(ArrivalCheck.NotYet, detector.check(t0.plusSeconds(600), fence, outside, parked))
    }

    @Test
    fun `서 있다가 다시 달리면 처음부터 센다`() {
        detector.check(t0, fence, inside, parked)
        detector.check(t0.plusSeconds(120), fence, inside, rolling)
        val restart = t0.plusSeconds(150)
        detector.check(restart, fence, inside, parked)

        assertEquals(ArrivalCheck.Stopping(Duration.ofSeconds(150)), detector.check(t0.plusSeconds(180), fence, inside, parked))
        assertEquals(ArrivalCheck.Arrived(restart), detector.check(restart.plusSeconds(180), fence, inside, parked))
    }

    @Test
    fun `신호가 잠깐 끊겨도 세던 시간은 이어진다`() {
        detector.check(t0, fence, inside, parked)
        detector.check(t0.plusSeconds(60), fence, location = null, motion = null)

        assertEquals(ArrivalCheck.Arrived(t0), detector.check(t0.plusSeconds(180), fence, inside, parked))
    }

    @Test
    fun `두 지점 사이 거리를 미터로 잰다`() {
        assertEquals(111.0, site.distanceTo(inside), 1.0)
    }
}
