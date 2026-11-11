package com.ieo.app.data.voice

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface VoiceApi {

    /** 예/아니오 질문의 대답 판별 (UX-025). 서버는 대답을 저장하지 않는다 */
    @POST("api/voice/answers/classify")
    suspend fun classify(@Body body: ClassifyAnswerRequest): Response<ClassifyAnswerResponse>
}

@Serializable
data class ClassifyAnswerRequest(val question: String, val answer: String)

/** answer: YES, NO, UNKNOWN */
@Serializable
data class ClassifyAnswerResponse(val answer: String)
