package com.ieo.app.domain.voice

/**
 * 음성 안내 (10_음성_안전UX). 운전 중에 화면을 보지 않아도 되게 말로 알려 준다.
 * 지금 구현은 Android 기본 TextToSpeech(data/voice/TtsAnnouncer). Pleos TTS SDK를 받으면 구현만 바꾼다 (UX-030).
 */
interface Announcer {
    /** 말할 차례가 되면 말한다. 겹치면 [Announcement.priority]가 높은 것부터, 같으면 들어온 순서로 */
    fun say(announcement: Announcement)
}

/** 15초 안에 끝나는 짧은 문장 하나 (UX-011) */
data class Announcement(val text: String, val priority: AnnouncementPriority)

/** 안내 우선순위 (UX-010). 위에 있을수록 먼저 말한다 */
enum class AnnouncementPriority {
    /** 휴게, 높이 제한 */
    WARNING,

    /** 도착, 대기 시작, 출발 */
    TRIP,

    /** 다음 배차 추천 */
    RECOMMENDATION,

    /** 상하차지 정보 카드, 리포트 */
    INFO,
}
