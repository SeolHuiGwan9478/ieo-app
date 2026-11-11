package com.ieo.app.ui.home

import com.ieo.app.domain.notification.AppNotification
import com.ieo.app.domain.notification.NotificationType
import com.ieo.app.domain.restarea.RestAdvice
import com.ieo.app.domain.restarea.RestStop
import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.site.SiteCard
import com.ieo.app.domain.trip.RestLevel
import com.ieo.app.domain.voice.Announcement
import com.ieo.app.domain.voice.AnnouncementPriority
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.ui.common.DayTimeFormatter
import com.ieo.app.ui.common.formatMinutesKorean
import com.ieo.app.ui.common.formatWon
import com.ieo.app.ui.common.josa
import java.time.Instant

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

    /** 도착을 감지하고 묻는다 (WAIT-007). [startsAt]은 확인하면 대기를 세기 시작하는 때(감지한 때) */
    fun arrivalQuestion(stopName: String, startsAt: Instant): Announcement =
        Announcement("${stopName}에 도착하셨어요? ${spokenTime(startsAt)}부터 대기로 기록할까요?", AnnouncementPriority.TRIP, ARRIVAL_KEY)

    /** 도착 확인 중 다시 묻는 말 */
    fun arrivalLine(text: String) = Announcement(text, AnnouncementPriority.TRIP, ARRIVAL_KEY)

    /** "아직"이라고 했을 때. 다시 묻지 않으니 직접 시작하는 법을 알려 준다 (WAIT-008) */
    fun arrivalDeclined(): Announcement = trip("알겠어요. 도착하면 대기 시작을 눌러 주세요.")

    /** 떠남을 감지하고 묻는다 (WAIT-005) */
    fun departureQuestion(stopName: String, isPickup: Boolean): Announcement = Announcement(
        if (isPickup) "${stopName}에서 상차 끝내고 출발할까요?" else "${stopName}에서 하차 끝내고 운행을 마칠까요?",
        AnnouncementPriority.TRIP,
        ARRIVAL_KEY,
    )

    /** 떠남을 "아직"이라고 했을 때. 다시 묻지 않으니 버튼으로 끝내는 법을 알려 준다 */
    fun departureDeclined(isPickup: Boolean): Announcement =
        trip(if (isPickup) "알겠어요. 끝나면 상차 끝, 출발을 눌러 주세요." else "알겠어요. 끝나면 운행 완료를 눌러 주세요.")

    /** 도착·떠남 확인의 이름. 창이 닫히면 묻던 말을 멈춘다 */
    const val ARRIVAL_KEY = "arrival"

    /** 14:05 → "오후 2시 5분", 09:00 → "오전 9시" */
    fun spokenTime(at: Instant): String {
        val time = at.atZone(DayTimeFormatter.KOREA).toLocalTime()
        val hour = (time.hour % 12).let { if (it == 0) 12 else it }
        val minute = if (time.minute == 0) "" else " ${time.minute}분"
        return "${if (time.hour < 12) "오전" else "오후"} ${hour}시$minute"
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
     * 쉬어야 할 때 (TRIP-031). 단계마다 한 번. [advice]가 있으면 경로 위 쉴 곳을 함께 말한다 (TRIP-032).
     * 예: "1시간 45분째 운전 중이에요. 18km 앞 성주휴게소에 수면실과 샤워실이 있어요. 15분 안에 쉬어 가세요."
     */
    fun rest(level: RestLevel, advice: RestAdvice? = null): Announcement {
        val head = when (level) {
            RestLevel.SOON -> "1시간 45분째 운전 중이에요."
            RestLevel.NOW -> "2시간 넘게 쉬지 않고 운전했어요."
            RestLevel.EXTENDED_SOON -> "곧 3시간이에요."
            RestLevel.EXTENDED_NOW -> "3시간 넘게 운전했어요."
        }
        val tail = when (level) {
            RestLevel.SOON -> "15분 안에 쉬어 가세요."
            RestLevel.NOW -> "15분 이상 쉬어 가세요."
            RestLevel.EXTENDED_SOON -> "15분 안에 30분 이상 쉬어 가세요."
            RestLevel.EXTENDED_NOW -> "바로 30분 이상 쉬어 가세요."
        }
        val text = when (advice) {
            null -> when (level) {
                RestLevel.SOON -> "$head 15분 안에 휴게소나 졸음쉼터에서 쉬어 가세요."
                RestLevel.NOW -> "$head 가까운 곳에 세우고 15분 이상 쉬어 가세요."
                else -> "$head 가까운 곳에 세우고 $tail"
            }
            is RestAdvice.Lounge -> "$head ${km(advice.stop)} 앞 ${josa(advice.stop.name, "에", "에")} ${facilityLine(advice.stop)} $tail"
            is RestAdvice.Shelter -> "$head ${km(advice.stop)} 앞 ${josa(advice.stop.name, "이", "가")} 있어요. $tail" +
                (advice.nextLounge?.let { " 다음 화물차 라운지는 ${km(it)} 앞이에요." } ?: "")
            is RestAdvice.NextLounge -> "$head $tail 다음 화물차 라운지는 ${km(advice.stop)} 앞 ${josa(advice.stop.name, "이에요", "예요")}."
        }
        return Announcement(text, AnnouncementPriority.WARNING, REST_KEY)
    }

    /** "수면실과 샤워실이 있어요." 라운지면 늘 둘 다 있다 */
    private fun facilityLine(stop: RestStop): String {
        val said = listOf("수면실", "샤워실").filter { it in stop.facilities }
        return if (said.isEmpty()) "화물차 라운지가 있어요." else "${said.joinToString("과 ")}이 있어요."
    }

    private fun km(stop: RestStop) = "${(stop.aheadMeters + 500) / 1000}km"

    const val REST_KEY = "rest"

    private fun trip(text: String) = Announcement(text, AnnouncementPriority.TRIP)

    /** 3.5 → "3.5미터", 4.0 → "4미터" */
    private fun formatHeight(meters: Double): String =
        if (meters % 1.0 == 0.0) "${meters.toInt()}미터" else "${"%.1f".format(meters)}미터"
}
