package com.ieo.app.domain.trip

import com.ieo.app.domain.vehicle.Gear
import com.ieo.app.domain.vehicle.VehicleMotion
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {

    private val start = Instant.parse("2026-11-11T00:00:00Z")
    private val moving = VehicleMotion(speedKph = 80.0, gear = Gear.DRIVE)
    private val stopped = VehicleMotion(speedKph = 0.0, gear = Gear.DRIVE)
    private val timer = RestTimer()

    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))

    @Test
    fun 달리기_전에는_재지_않는다() {
        assertNull(timer.check(at(0), stopped))
        assertNull(timer.check(at(1), null))
    }

    @Test
    fun 한_시간_45분에_곧_쉬라고_두_시간에_지금_쉬라고_한다() {
        timer.check(at(0), moving)
        assertNull(timer.check(at(104), moving)!!.level)
        assertEquals(RestLevel.SOON, timer.check(at(105), moving)!!.level)
        assertEquals(RestLevel.NOW, timer.check(at(120), moving)!!.level)
    }

    @Test
    fun 잠깐_선_것은_운전에_넣는다() {
        timer.check(at(0), moving)
        timer.check(at(60), stopped)
        timer.check(at(74), stopped)
        timer.check(at(75), moving)
        assertEquals(Duration.ofMinutes(105), timer.check(at(105), moving)!!.drivingFor)
    }

    @Test
    fun 십오분_넘게_서_있으면_쉰_것으로_보고_다시_잰다() {
        timer.check(at(0), moving)
        timer.check(at(110), stopped)
        assertNull(timer.check(at(125), stopped))
        timer.check(at(126), moving)
        assertEquals(Duration.ofMinutes(10), timer.check(at(136), moving)!!.drivingFor)
    }

    @Test
    fun 두_시간을_넘기면_2시간_45분과_3시간에_연장운행을_알린다() {
        timer.check(at(0), moving)
        assertEquals(RestLevel.NOW, timer.check(at(164), moving)!!.level)
        assertEquals(RestLevel.EXTENDED_SOON, timer.check(at(165), moving)!!.level)
        assertEquals(RestLevel.EXTENDED_NOW, timer.check(at(180), moving)!!.level)
    }

    @Test
    fun 두_시간을_넘긴_뒤에는_30분을_쉬어야_다시_잰다() {
        timer.check(at(0), moving)
        timer.check(at(130), stopped)
        timer.check(at(150), stopped)
        assertEquals(RestLevel.NOW, timer.check(at(151), stopped)!!.level)
        assertNull(timer.check(at(160), stopped))
    }

    @Test
    fun 두_시간_전에_섰으면_15분이면_된다() {
        timer.check(at(0), moving)
        timer.check(at(100), stopped)
        assertNull(timer.check(at(115), stopped))
    }

    @Test
    fun 신호가_끊겨도_세던_것을_이어_간다() {
        timer.check(at(0), moving)
        assertEquals(RestLevel.NOW, timer.check(at(130), null)!!.level)
    }
}
