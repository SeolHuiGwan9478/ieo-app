package com.ieo.app.domain.voice

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 음성 안내 (10_음성_안전UX). 운전 중에 화면을 보지 않아도 되게 말로 알려 준다.
 * 지금 구현은 Android 기본 TextToSpeech(data/voice/TtsAnnouncer). Pleos TTS SDK를 받으면 구현만 바꾼다 (UX-030).
 */
interface Announcer {
    /** 말할 차례가 되면 말한다. 겹치면 [Announcement.priority]가 높은 것부터, 같으면 들어온 순서로 */
    fun say(announcement: Announcement) = say(announcement) {}

    /** 다 말했거나, 취소되었거나, 말할 수 없을 때 [onSpoken]을 부른다 */
    fun say(announcement: Announcement, onSpoken: () -> Unit)

    /** [key]가 같은 안내를 말하는 중이면 멈추고, 차례를 기다리면 뺀다. 화면이 바뀌어 맞지 않게 된 안내에 쓴다 */
    fun cancel(key: String)
}

/** 말을 마칠 때까지 기다린다 */
suspend fun Announcer.speak(announcement: Announcement) = suspendCancellableCoroutine { done ->
    say(announcement) { if (done.isActive) done.resume(Unit) }
}

/** 15초 안에 끝나는 짧은 문장 하나 (UX-011) */
data class Announcement(
    val text: String,
    val priority: AnnouncementPriority,
    /** [Announcer.cancel]로 취소할 수 있게 붙이는 이름. 같은 이름의 새 안내는 이전 것을 대신한다 */
    val key: String? = null,
)

/** 안내 우선순위 (UX-010). 위에 있을수록 먼저 말한다 */
enum class AnnouncementPriority {
    /** 휴게, 높이 제한 */
    WARNING,

    /** 도착, 대기 시작, 출발 */
    TRIP,

    /** 배차 알림 (확정·마감·만료) */
    RECOMMENDATION,

    /** 상하차지 정보 카드, 리포트 */
    INFO,
}
