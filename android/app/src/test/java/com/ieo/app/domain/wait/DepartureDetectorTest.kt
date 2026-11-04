package com.ieo.app.domain.wait

import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureDetectorTest {

    private val site = GeoPoint(36.9690, 126.8410)
    private val fence = GeoFence(site, radiusMeters = 200)
    // 상하차지에서 약 110m 북쪽 (반경 안), 약 1.1km 북쪽 (반경 밖)
    private val inside = GeoPoint(36.9700, 126.8410)
    private val outside = GeoPoint(36.9790, 126.8410)
    private val parked = VehicleMotion(0.0, Gear.PARK)
    private val slow = VehicleMotion(10.0, Gear.DRIVE)
    private val fast = VehicleMotion(30.0, Gear.DRIVE)
    private val t0 = Instant.parse("2026-11-04T01:00:00Z")
    private val detector = DepartureDetector()

    @Test
    fun `반경 안에 서 있으면 머무는 중이다`() {
        assertEquals(DepartureCheck.Staying, detector.check(t0, fence, inside, parked))
        assertEquals(DepartureCheck.Staying, detector.check(t0.plusSeconds(3_600), fence, inside, slow))
    }

    @Test
    fun `반경을 30초 벗어나 있으면 벗어난 때로 떠난다`() {
        detector.check(t0, fence, inside, parked)
        assertEquals(DepartureCheck.Leaving(Duration.ofSeconds(30)), detector.check(t0.plusSeconds(10), fence, outside, slow))

        assertEquals(DepartureCheck.Departed(t0.plusSeconds(10)), detector.check(t0.plusSeconds(40), fence, outside, slow))
    }

    @Test
    fun `GPS가 잠깐 튀어 반경 밖으로 나갔다 돌아오면 처음부터 센다`() {
        detector.check(t0, fence, inside, parked)
        detector.check(t0.plusSeconds(10), fence, outside, parked)
        detector.check(t0.plusSeconds(20), fence, inside, parked)

        assertEquals(DepartureCheck.Staying, detector.check(t0.plusSeconds(60), fence, inside, parked))
    }

    @Test
    fun `반경 안에 한 번도 들어오지 않았으면 반경 밖이어도 떠남이 아니다`() {
        detector.check(t0, fence, outside, parked)

        assertEquals(DepartureCheck.Staying, detector.check(t0.plusSeconds(600), fence, outside, parked))
    }

    @Test
    fun `시속 20km 이상으로 1분 달리면 달리기 시작한 때로 떠난다`() {
        detector.check(t0, fence, outside, fast)
        assertEquals(DepartureCheck.Leaving(Duration.ofSeconds(30)), detector.check(t0.plusSeconds(30), fence, outside, fast))

        assertEquals(DepartureCheck.Departed(t0), detector.check(t0.plusSeconds(60), fence, outside, fast))
    }

    @Test
    fun `빨리 달리다 느려지면 처음부터 센다`() {
        detector.check(t0, fence, inside, fast)
        detector.check(t0.plusSeconds(50), fence, inside, slow)

        assertEquals(DepartureCheck.Leaving(Duration.ofSeconds(60)), detector.check(t0.plusSeconds(70), fence, inside, fast))
    }

    @Test
    fun `신호가 끊겨도 세던 것을 이어 간다`() {
        detector.check(t0, fence, inside, parked)
        detector.check(t0.plusSeconds(10), fence, outside, slow)

        assertEquals(DepartureCheck.Departed(t0.plusSeconds(10)), detector.check(t0.plusSeconds(40), fence, null, null))
    }

    @Test
    fun `다른 상하차지로 바뀌면 reset으로 처음부터 센다`() {
        detector.check(t0, fence, inside, parked)
        detector.check(t0.plusSeconds(10), fence, outside, slow)
        detector.reset()

        assertEquals(DepartureCheck.Staying, detector.check(t0.plusSeconds(60), fence, outside, slow))
    }
}
