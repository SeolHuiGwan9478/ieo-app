package com.ieo.app.domain.voice

/** 차주 말 듣기 (UX-020). 구현은 Pleos SpeechToText SDK(data/voice/PleosSpeechRecognizer) */
interface SpeechRecognizer {
    /** 한 문장을 듣는다. 못 들었거나 [timeoutMillis]가 지났거나 쓸 수 없으면 null. 코루틴을 취소하면 마이크를 닫는다 */
    suspend fun listen(timeoutMillis: Long): String?
}

/** 듣는 기능이 없을 때: 듣지 않고 바로 null */
object NoSpeechRecognizer : SpeechRecognizer {
    override suspend fun listen(timeoutMillis: Long): String? = null
}
