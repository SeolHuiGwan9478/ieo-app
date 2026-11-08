package com.ieo.app.domain.recommendation

import com.ieo.app.domain.voice.SpeechRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NextOrderVoiceDialogTest {

    private val spoken = mutableListOf<String>()

    /** 대본대로 듣는다. null은 아무 말도 안 함 */
    private fun dialog(vararg heard: String?): NextOrderVoiceDialog {
        val script = ArrayDeque(heard.toList())
        val recognizer = object : SpeechRecognizer {
            override suspend fun listen(timeoutMillis: Long) = script.removeFirstOrNull()
        }
        return NextOrderVoiceDialog(recognizer) { spoken += it }
    }

    private suspend fun NextOrderVoiceDialog.askDriving() = ask("안내", "자세한 내용", "칠곡 부산행 수락할까요?", confirmed = false)

    @Test
    fun `대답이 없으면 아무것도 하지 않는다`() = runTest {
        assertEquals(VoiceAnswer.NONE, dialog(null).askDriving())
    }

    @Test
    fun `수락은 한 번 더 묻고 응이어야 수락한다`() = runTest {
        assertEquals(VoiceAnswer.ACCEPT, dialog("수락", "응").askDriving())
        assertEquals(listOf("칠곡 부산행 수락할까요?"), spoken)
    }

    @Test
    fun `수락 뒤 확인에 답이 없거나 다른 말이면 수락하지 않는다`() = runTest {
        assertEquals(VoiceAnswer.NONE, dialog("수락", null).askDriving())
        assertEquals(VoiceAnswer.NONE, dialog("수락", "오늘 날씨").askDriving())
        assertEquals(VoiceAnswer.NEXT, dialog("수락", "아니").askDriving())
    }

    @Test
    fun `자세히는 더 말하고 다시 듣고 다음은 넘긴다`() = runTest {
        assertEquals(VoiceAnswer.NEXT, dialog("자세히", "다시", "다음").askDriving())
        assertEquals(listOf("자세한 내용", "안내"), spoken)
    }

    @Test
    fun `주행 중 첫 대답의 응은 수락이 아니라 자세히다`() = runTest {
        assertEquals(VoiceAnswer.NONE, dialog("응", null).askDriving())
        assertEquals(listOf("자세한 내용"), spoken)
    }

    @Test
    fun `두 번 못 알아들으면 정차 후 화면에서 하라고 말하고 끝낸다`() = runTest {
        assertEquals(VoiceAnswer.NONE, dialog("뭐뭐", "뭐뭐", "수락").askDriving())
        assertEquals(listOf(NextOrderVoiceDialog.RETRY_TEXT, NextOrderVoiceDialog.GIVE_UP_TEXT), spoken)
    }

    @Test
    fun `수락 창에서 물은 뒤에는 응이나 수락으로 바로 수락한다`() = runTest {
        assertEquals(VoiceAnswer.ACCEPT, dialog("응").ask("안내", "자세히", "확인", confirmed = true))
        assertEquals(VoiceAnswer.ACCEPT, dialog("수락").ask("안내", "자세히", "확인", confirmed = true))
        assertEquals(emptyList<String>(), spoken)
    }
}
