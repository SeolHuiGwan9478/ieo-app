package com.ieo.app.domain.voice

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeoutOrNull

/** 예/아니오로 물은 질문에 대한 대답의 뜻 */
enum class YesNo { YES, NO, UNKNOWN }

/**
 * 차량 앱이 단어 목록으로 알아듣지 못한 대답을 서버에서 판별한다 (UX-025 ②).
 * 실패하거나 시간이 넘으면 [YesNo.UNKNOWN]
 */
interface AnswerClassifier {
    suspend fun classify(question: String, answer: String): YesNo
}

/** 서버 판별을 쓰지 않을 때 */
object NoAnswerClassifier : AnswerClassifier {
    override suspend fun classify(question: String, answer: String) = YesNo.UNKNOWN
}

/**
 * 예/아니오 질문의 대답을 듣는다 (UX-025). 질문을 말한 뒤에 부른다.
 * ① 단어 목록([VoiceCommand])으로 바로 판별하고, ② 못 알아들은 말만 서버([AnswerClassifier])에 묻는다.
 * 그래도 모르면 "다시 말씀해 주세요"라고 한 번 더 듣는다. 두 번 못 알아들으면 시간이 다 될 때까지 기다린다.
 * 들은 말은 판별에만 쓰고 남기지 않는다.
 */
class YesNoDialog(
    private val recognizer: SpeechRecognizer,
    private val classifier: AnswerClassifier,
    /** 말을 끝낼 때까지 기다린다 */
    private val speak: suspend (String) -> Unit,
) {

    /** [timeoutMillis] 안에 예나 아니오를 못 들으면 null. 코루틴을 취소하면 마이크를 닫는다 */
    suspend fun listen(question: String, timeoutMillis: Long): YesNo? = withTimeoutOrNull(timeoutMillis) {
        var misses = 0
        while (true) {
            // 말이 없으면 창이 닫힐 때까지 기다린다 (차주는 화면 버튼으로 대답할 수 있다)
            val heard = recognizer.listen(timeoutMillis) ?: awaitCancellation()
            val meaning = when (VoiceCommand.parse(heard)) {
                VoiceCommand.YES, VoiceCommand.ACCEPT -> YesNo.YES
                VoiceCommand.NO -> YesNo.NO
                else -> classifier.classify(question, heard)
            }
            if (meaning != YesNo.UNKNOWN) return@withTimeoutOrNull meaning
            if (++misses >= MAX_MISSES) awaitCancellation()
            speak(RETRY_TEXT)
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    companion object {
        private const val MAX_MISSES = 2
        const val RETRY_TEXT = "다시 말씀해 주세요."
    }
}
