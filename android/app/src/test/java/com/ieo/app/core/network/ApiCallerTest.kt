package com.ieo.app.core.network

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ApiCallerTest {

    private val caller = ApiCaller(Json { ignoreUnknownKeys = true })

    @Test
    fun `서버 오류 응답의 code와 message를 그대로 담는다`() = runTest {
        val body = """{"code":"AUTH_400_CODE","message":"인증번호가 맞지 않습니다.","fieldErrors":[]}"""
            .toResponseBody("application/json".toMediaType())

        val result = caller.call<String> { Response.error(400, body) }

        assertEquals(
            ApiResult.Failure(ApiError.Server(400, "AUTH_400_CODE", "인증번호가 맞지 않습니다.")),
            result,
        )
    }

    @Test
    fun `서버 형식이 아닌 오류도 Server로 돌려준다`() = runTest {
        val result = caller.call<String> { Response.error(502, "Bad Gateway".toResponseBody(null)) }

        val error = (result as ApiResult.Failure).error as ApiError.Server
        assertEquals(502, error.status)
        assertEquals("HTTP_502", error.code)
    }

    @Test
    fun `연결 실패는 Network`() = runTest {
        val result = caller.call<String> { throw IOException("timeout") }

        assertEquals(ApiResult.Failure(ApiError.Network), result)
    }

    @Test
    fun `본문 없는 204는 callOptional에서 null`() = runTest {
        val result = caller.callOptional<String> { Response.success<String>(204, null as String?) }

        assertEquals(ApiResult.Success(null), result)
    }

    @Test
    fun `모르는 enum 값이 오면 앱이 죽지 않고 Unexpected`() {
        val result = ApiResult.Success("FLYING").mapResponse { enumValueOf<Kind>(it) }

        assertEquals(ApiResult.Failure(ApiError.Unexpected), result)
    }

    private enum class Kind { DRIVING }
}
