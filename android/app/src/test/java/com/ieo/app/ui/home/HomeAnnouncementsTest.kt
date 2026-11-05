package com.ieo.app.ui.home

import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.voice.AnnouncementPriority
import com.ieo.app.testing.TripFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAnnouncementsTest {

    private val card = TripFixtures.site(1)

    @Test
    fun `정보가 하나도 없으면 읽지 않는다`() {
        val empty = card.copy(heightLimitMeters = null, heightWarning = HeightWarning.UNKNOWN, forklift = null, averageWaitMinutes = null)

        assertNull(HomeAnnouncements.approaching(empty))
    }

    @Test
    fun `높이가 괜찮으면 정보로 읽는다`() {
        val announcement = HomeAnnouncements.approaching(card.copy(heightLimitMeters = 4.0, heightWarning = HeightWarning.OK))!!

        assertEquals(AnnouncementPriority.INFO, announcement.priority)
        assertEquals("평택 포승은 높이 제한 4미터, 지게차 있음, 평균 대기 1시간 20분.", announcement.text)
    }

    @Test
    fun `높이 제한에 걸리면 경고부터 말한다`() {
        val announcement = HomeAnnouncements.approaching(card.copy(heightWarning = HeightWarning.BLOCKED))!!

        assertEquals(AnnouncementPriority.WARNING, announcement.priority)
        assertEquals(
            "주의. 내 차가 높이 제한보다 높아요. 들어가기 전에 확인하세요. 평택 포승은 높이 제한 3.5미터, 지게차 있음, 평균 대기 1시간 20분.",
            announcement.text,
        )
    }

    @Test
    fun `서명한 사람 이름이 없으면 빼고 말한다`() {
        assertEquals("김수하님이 인수증에 서명했어요. 운행을 마쳤어요.", HomeAnnouncements.deliverySigned("김수하").text)
        assertEquals("인수증에 서명했어요. 운행을 마쳤어요.", HomeAnnouncements.deliverySigned(null).text)
    }
}
