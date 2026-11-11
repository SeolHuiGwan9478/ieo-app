package com.ieo.app.data.voice

import com.ieo.app.core.network.ApiCaller
import com.ieo.app.core.network.ApiResult
import com.ieo.app.domain.voice.AnswerClassifier
import com.ieo.app.domain.voice.YesNo
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 서버(OpenRouter)에 대답 판별을 맡긴다 (UX-025 ②). 15초 안에 다시 물어야 하므로 3초 넘게 기다리지 않는다.
 * 통신이 끊겼거나 서버가 모르면 [YesNo.UNKNOWN]
 */
class ServerAnswerClassifier(private val api: VoiceApi, private val caller: ApiCaller) : AnswerClassifier {

    override suspend fun classify(question: String, answer: String): YesNo {
        val result = withTimeoutOrNull(TIMEOUT_MILLIS) {
            caller.call { api.classify(ClassifyAnswerRequest(question.take(MAX_LENGTH), answer.take(MAX_LENGTH))) }
        }
        val answered = (result as? ApiResult.Success)?.data?.answer ?: return YesNo.UNKNOWN
        return YesNo.entries.firstOrNull { it.name == answered } ?: YesNo.UNKNOWN
    }

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L

        /** 서버가 받는 길이 */
        const val MAX_LENGTH = 200
    }
}
