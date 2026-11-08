package com.ieo.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandTest {

    private fun assertAll(command: VoiceCommand, vararg heard: String) =
        heard.forEach { assertEquals(it, command, VoiceCommand.parse(it)) }

    @Test
    fun `수락과 긍정을 알아듣는다`() {
        assertAll(VoiceCommand.ACCEPT, "수락", "수락할게요", "수락 할게", "받을게요", "이거 받겠습니다")
        assertAll(VoiceCommand.YES, "응", "네", "네.", "예", "어 그래", "맞아", "좋아요")
    }

    @Test
    fun `할게는 그 말만 했을 때 수락이다`() {
        assertAll(VoiceCommand.ACCEPT, "할게요", "할게", "이거 할게요", "그거 할게")
        assertAll(VoiceCommand.UNKNOWN, "이따 갈게 할게", "저녁 먹고 할게요", "내일 할게요")
    }

    @Test
    fun `전화와 통화 이야기는 수락으로 듣지 않는다`() {
        assertAll(VoiceCommand.UNKNOWN, "이따 전화할게", "전화 받을게요", "잠깐 통화 좀 할게")
    }

    @Test
    fun `아니라는 말이 섞이면 수락으로 듣지 않는다`() {
        assertAll(VoiceCommand.NO, "다음", "다음 거", "아니", "아니요", "수락 안 해", "안 받을래", "거절", "넘겨")
    }

    @Test
    fun `긴 문장의 첫 글자만 맞는다고 긍정으로 듣지 않는다`() {
        assertAll(VoiceCommand.UNKNOWN, "어제 부산 갔다 왔어", "네비게이션 켜 줘", "오늘 날씨 어때", "", "  ")
    }

    @Test
    fun `자세히와 다시를 알아듣는다`() {
        assertAll(VoiceCommand.DETAIL, "자세히", "자세히 알려 줘", "상세 정보")
        assertAll(VoiceCommand.REPEAT, "다시", "다시 말해 줘", "뭐라고", "한 번 더")
    }
}
