package com.ieo.app.domain.voice

/**
 * 음성 데이터 처리에 동의한 사람만 듣는다 (ACC-006). 동의하지 않았거나 확인하지 못하면 듣지 않고 null을 돌려준다.
 * 호출하는 쪽은 "못 들었다"로 처리해 화면 버튼으로 넘어간다.
 */
class ConsentAwareSpeechRecognizer(
    private val delegate: SpeechRecognizer,
    private val hasConsent: suspend () -> Boolean,
) : SpeechRecognizer {

    override suspend fun listen(timeoutMillis: Long): String? =
        if (hasConsent()) delegate.listen(timeoutMillis) else null
}
