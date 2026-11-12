package com.ieo.app.domain.ledger

import com.ieo.app.testing.FakeBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDistanceRecorderTest {

    private class Marks : RouteMarks {
        val saved = mutableMapOf<Long, Int>()
        override fun saveFirst(tripId: Long, meters: Int) { saved.putIfAbsent(tripId, meters) }
        override fun take(tripId: Long) = saved.remove(tripId)
    }

    private val books = FakeBooks(mutableListOf())

    @Test
    fun `하차지로 처음 안내받은 경로 거리를 운행을 마칠 때 보낸다`() = runTest {
        val marks = Marks()
        val recorder = TripDistanceRecorder(marks, books)
        recorder.routeToDropoff(21, 312_400)
        // 길을 벗어나 다시 안내받아도 처음 거리를 쓴다
        recorder.routeToDropoff(21, 180_000)
        assertEquals(312, recorder.completed(21))
        assertTrue("distance 21 312" in books.calls)
        assertTrue(marks.saved.isEmpty())
    }

    @Test
    fun `경로를 모르거나 말이 안 되는 값이면 보내지 않는다`() = runTest {
        val recorder = TripDistanceRecorder(Marks(), books)
        assertNull(recorder.completed(21))
        recorder.routeToDropoff(22, 0)
        assertNull(recorder.completed(22))
        recorder.routeToDropoff(23, 9_000_000)
        assertNull(recorder.completed(23))
        assertTrue(books.calls.none { it.startsWith("distance") })
    }
}
