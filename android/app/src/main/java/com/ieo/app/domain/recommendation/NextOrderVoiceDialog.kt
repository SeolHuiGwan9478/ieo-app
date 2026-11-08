package com.ieo.app.domain.recommendation

import com.ieo.app.domain.voice.SpeechRecognizer
import com.ieo.app.domain.voice.VoiceCommand

/** 음성으로 받은 대답 */
enum class VoiceAnswer {
    /** 수락하고 배차 요청 */
    ACCEPT,

    /** 이 후보는 넘긴다 */
    NEXT,

    /** 대답이 없거나 끝내 알아듣지 못했다. 아무것도 하지 않는다 */
    NONE,
}

/**
 * 다음 배차 안내 뒤의 음성 대화 (REC-020, REC-022, UX-020, UX-022). 안내 문장을 말한 뒤에 부른다.
 * - "다음" → 넘김. "자세히" → 더 말하고 다시 듣는다. "다시" → 첫 안내를 다시 말한다
 * - "수락" → 잘못 알아들은 수락을 막으려고 한 번 더 묻고 "응"일 때만 수락한다
 * - [confirmed]면 이미 "수락할까요?"라고 물은 뒤라 "응"이나 "수락"으로 바로 수락한다 (정차 중 수락 창)
 * - 대답이 없으면 끝낸다. 두 번 못 알아들으면 정차 후 화면에서 하라고 말하고 끝낸다
 */
class NextOrderVoiceDialog(
    private val recognizer: SpeechRecognizer,
    /** 말을 끝낼 때까지 기다린다 */
    private val speak: suspend (String) -> Unit,
) {

    suspend fun ask(
        repeatText: String,
        detailText: String,
        confirmText: String,
        confirmed: Boolean,
    ): VoiceAnswer {
        var misses = 0
        while (true) {
            val heard = recognizer.listen(LISTEN_MILLIS) ?: return VoiceAnswer.NONE
            when (VoiceCommand.parse(heard)) {
                VoiceCommand.NO -> return VoiceAnswer.NEXT
                VoiceCommand.DETAIL -> speak(detailText)
                VoiceCommand.REPEAT -> speak(repeatText)
                VoiceCommand.ACCEPT -> return if (confirmed) VoiceAnswer.ACCEPT else confirm(confirmText)
                // 주행 중 첫 안내 뒤의 "응"은 자세히 듣겠다는 뜻이다
                VoiceCommand.YES -> if (confirmed) return VoiceAnswer.ACCEPT else speak(detailText)
                VoiceCommand.UNKNOWN -> {
                    misses++
                    if (misses >= MAX_MISSES) {
                        speak(GIVE_UP_TEXT)
                        return VoiceAnswer.NONE
                    }
                    speak(RETRY_TEXT)
                }
            }
        }
    }

    private suspend fun confirm(confirmText: String): VoiceAnswer {
        speak(confirmText)
        val heard = recognizer.listen(LISTEN_MILLIS) ?: return VoiceAnswer.NONE
        return when (VoiceCommand.parse(heard)) {
            VoiceCommand.YES, VoiceCommand.ACCEPT -> VoiceAnswer.ACCEPT
            VoiceCommand.NO -> VoiceAnswer.NEXT
            else -> VoiceAnswer.NONE
        }
    }

    companion object {
        /** 안내 뒤 8초 동안 듣는다 (UX-020) */
        const val LISTEN_MILLIS = 8_000L
        private const val MAX_MISSES = 2
        const val RETRY_TEXT = "다시 말씀해 주세요."
        const val GIVE_UP_TEXT = "정차 후 화면에서 해 주세요."
    }
}
