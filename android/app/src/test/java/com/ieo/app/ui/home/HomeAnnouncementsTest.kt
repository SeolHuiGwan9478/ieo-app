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
    fun `인수증 결제 운행을 마치면 종이 인수증을 휴대폰으로 보내라고 한다`() {
        assertEquals("운행을 마쳤어요. 장부에 45만 원을 기록했어요.", HomeAnnouncements.tripCompleted(450_000).text)
        assertEquals("운행을 마쳤어요. 장부에 45만 원을 기록했어요. 인수증 사진은 휴대폰으로 보내 주세요.", HomeAnnouncements.tripCompleted(450_000, receipt = true).text)
    }
}
