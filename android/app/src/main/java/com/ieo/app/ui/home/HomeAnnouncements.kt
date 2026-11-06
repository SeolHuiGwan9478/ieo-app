package com.ieo.app.ui.home

import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import com.ieo.app.domain.recommendation.NextOrder
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.voice.Announcement
import com.ieo.app.domain.voice.AnnouncementPriority
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.ui.common.formatMinutesKorean
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.common.formatWonSpoken
import com.ieo.app.ui.common.josa

/**
 * 운행 중 음성 안내 문장. 15초 안에 끝나게 짧게, 숫자는 듣기 쉽게 쓴다 (UX-011).
 * 언제 말할지는 [HomeViewModel]이 정한다.
 */
object HomeAnnouncements {

    /** 배차 확정은 운행 안내로, 마감·만료는 추천과 같은 순서로 말한다 (09 알림: 주행 중 음성) */
    fun notification(notification: AppNotification): Announcement = Announcement(
        text = notification.message,
        priority = if (notification.type == NotificationType.DISPATCH_CONFIRMED) AnnouncementPriority.TRIP
        else AnnouncementPriority.RECOMMENDATION,
    )

    /**
     * 상하차지 3km 전 (SITE-006). 정보가 없으면 null(말하지 않는다).
     * 높이 제한에 걸리거나 아슬아슬하면 경고로 먼저 말한다 (UX-010).
     */
    fun approaching(card: SiteCard): Announcement? {
        val facts = listOfNotNull(
            card.heightLimitMeters?.let { "높이 제한 ${formatHeight(it)}" },
            card.forklift?.let { if (it) "지게차 있음" else "지게차 없음" },
            card.averageWaitMinutes?.let { "평균 대기 ${formatMinutesKorean(it.toLong())}" },
        )
        val warning = when (card.heightWarning) {
            HeightWarning.BLOCKED -> "내 차가 높이 제한보다 높아요. 들어가기 전에 확인하세요."
            HeightWarning.CAUTION -> "내 차와 높이 차이가 20센티 안쪽이에요. 천천히 들어가세요."
            HeightWarning.OK, HeightWarning.UNKNOWN -> null
        }
        if (facts.isEmpty() && warning == null) return null
        // 요구사항의 예처럼 이름 뒤에 항목만 늘어놓는다: "칠곡 물류센터는 높이 제한 3.5미터, 지게차 있음, 평균 대기 1시간 20분."
        val summary = if (facts.isEmpty()) null else "${josa(card.name, "은", "는")} ${facts.joinToString(", ")}."
        val text = listOfNotNull(warning?.let { "주의. $it" }, summary).joinToString(" ")
        val priority = if (warning != null) AnnouncementPriority.WARNING else AnnouncementPriority.INFO
        return Announcement(text, priority)
    }

    /** 도착 = 대기 시작 (WAIT-002). 대기비 기준이 있으면 무료 대기 시간도 알려 준다 */
    fun arrived(stopName: String, policy: WaitPolicy?): Announcement {
        val free = policy?.let { " 무료 대기는 ${formatMinutesKorean(it.freeMinutes.toLong())}이에요." }.orEmpty()
        return trip("${stopName}에 도착했어요. 대기를 시작해요.$free")
    }

    /** 무료 대기 시간이 끝난 순간 (WAIT-012) */
    fun chargeableStarted(policy: WaitPolicy): Announcement =
        trip("지금부터 대기비가 붙어요. ${policy.unitMinutes}분마다 ${formatWon(policy.unitAmount)}이에요.")

    /** 상차지를 떠나 하차지로 */
    fun leftPickup(pickupName: String, dropoffName: String): Announcement =
        trip("${josa(pickupName, "을", "를")} 떠났어요. ${josa(dropoffName, "으로", "로")} 안내할게요.")

    /** 하차지를 떠나 운행이 끝남. 장부에 운임이 기록된다 */
    fun tripCompleted(fare: Long): Announcement = trip("운행을 마쳤어요. 장부에 ${formatWon(fare)}을 기록했어요.")

    /** 수하인 서명으로 운행이 끝남 (POD-008) */
    fun deliverySigned(signerName: String?): Announcement =
        trip("${signerName?.let { "${it}님이 " } ?: ""}인수증에 서명했어요. 운행을 마쳤어요.")

    /**
     * 다음 배차 추천 (REC-020). 요구사항의 예를 따른다:
     * "대구 성서 공단 하차 후 17분 거리, 칠곡 물류센터 16:00 상차, 부산 신항행 5톤, 실수익 28만 원 건이 있어요."
     * 공차 이동 시간은 서버와 같은 평균 시속 60km로 어림한다
     */
    fun nextOrder(dropoffName: String, next: NextOrder, pickupTime: String): Announcement {
        val order = next.order
        val minutes = (next.emptyMeters + 999) / 1000
        val wait = if (next.longWait) " 대기가 길었던 곳이에요." else ""
        return Announcement(
            "다음 배차 추천이에요. $dropoffName 하차 후 ${formatMinutesKorean(minutes)} 거리, " +
                "${order.pickup.name} $pickupTime 상차, ${order.dropoff.name}행 ${order.tonnageLabel}, " +
                "실수익 ${formatWonSpoken(next.netProfit)} 건이 있어요.$wait",
            AnnouncementPriority.RECOMMENDATION,
            NEXT_ORDER_KEY,
        )
    }

    /** 수락 창과 함께 묻는다. 자세한 내용은 창에 있고 15초 안에 답해야 하니 짧게 */
    fun nextOrderOffer(next: NextOrder): Announcement {
        val order = next.order
        return Announcement(
            "${order.pickup.name}에서 ${order.dropoff.name}행, 실수익 ${formatWonSpoken(next.netProfit)}. 수락할까요?",
            AnnouncementPriority.RECOMMENDATION,
            NEXT_ORDER_KEY,
        )
    }

    /** 다음 배차 안내의 이름. 수락 창이 닫히거나 다음 후보로 넘어가면 말하던 것을 멈춘다 */
    const val NEXT_ORDER_KEY = "next-order"

    private fun trip(text: String) = Announcement(text, AnnouncementPriority.TRIP)

    /** 3.5 → "3.5미터", 4.0 → "4미터" */
    private fun formatHeight(meters: Double): String =
        if (meters % 1.0 == 0.0) "${meters.toInt()}미터" else "${"%.1f".format(meters)}미터"
}
