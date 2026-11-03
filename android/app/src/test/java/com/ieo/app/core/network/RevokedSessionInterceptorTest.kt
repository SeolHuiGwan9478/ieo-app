package com.ieo.app.core.network

import com.ieo.app.core.session.AuthTokens
import com.ieo.app.core.session.SessionManager
import com.ieo.app.testing.MemoryTokenStore
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevokedSessionInterceptorTest {

    private val store = MemoryTokenStore(AuthTokens("access", "refresh"))
    private val session = SessionManager(store)
    private val interceptor = RevokedSessionInterceptor(session)

    @Test
    fun `차주 프로필이 없는 계정이면 로그아웃한다`() {
        val response = interceptor.intercept(chain(403, """{"code":"LEDGER_403_ROLE","message":"차주 계정만 장부를 쓸 수 있습니다."}"""))

        assertEquals(403, response.code)
        assertFalse(session.isLoggedIn.value)
    }

    @Test
    fun `이용 제한된 계정이면 로그아웃한다`() {
        interceptor.intercept(chain(403, """{"code":"USER_403","message":"이용이 제한되었거나 탈퇴한 계정입니다."}"""))

        assertFalse(session.isLoggedIn.value)
    }

    @Test
    fun `다른 403은 로그인을 유지한다`() {
        interceptor.intercept(chain(403, """{"code":"WAIT_403_POLICY","message":"주선사 대표만 대기비 정책을 바꿀 수 있습니다."}"""))

        assertTrue(session.isLoggedIn.value)
    }

    @Test
    fun `그 사이 다시 로그인했으면 새 세션은 그대로 둔다`() {
        val stale = chain(403, """{"code":"TRIP_403_ROLE","message":""}""", sentToken = "old-access")

        interceptor.intercept(stale)

        assertTrue(session.isLoggedIn.value)
    }

    private fun chain(code: Int, body: String, sentToken: String = "access"): Interceptor.Chain =
        FakeChain(
            Request.Builder()
                .url("http://localhost/api/ledger")
                .header(BearerTokenInterceptor.AUTHORIZATION, BearerTokenInterceptor.bearer(sentToken))
                .build(),
        ) { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Forbidden")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }

    private class FakeChain(private val request: Request, private val respond: (Request) -> Response) : Interceptor.Chain {
        override fun request() = request
        override fun proceed(request: Request) = respond(request)
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}
