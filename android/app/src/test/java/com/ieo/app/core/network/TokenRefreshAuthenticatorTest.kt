package com.ieo.app.core.network

import com.ieo.app.core.session.AuthTokens
import com.ieo.app.core.session.SessionManager
import com.ieo.app.core.session.TokenRefresher
import com.ieo.app.testing.MemoryTokenStore
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRefreshAuthenticatorTest {

    private val store = MemoryTokenStore(AuthTokens("old-access", "refresh"))
    private val session = SessionManager(store)

    @Test
    fun `401이면 토큰을 갱신하고 새 토큰으로 다시 보낸다`() {
        val authenticator = TokenRefreshAuthenticator(session) { TokenRefresher.Result.Refreshed(AuthTokens("new-access", "new-refresh")) }

        val retry = authenticator.authenticate(null, unauthorized("old-access"))

        assertEquals("Bearer new-access", retry?.header("Authorization"))
        assertEquals("new-refresh", store.tokens?.refreshToken)
    }

    @Test
    fun `다른 요청이 먼저 갱신했으면 다시 갱신하지 않는다`() {
        session.updateTokens(AuthTokens("already-new", "refresh-2"))
        var refreshCalls = 0
        val authenticator = TokenRefreshAuthenticator(session) {
            refreshCalls++
            TokenRefresher.Result.Rejected
        }

        val retry = authenticator.authenticate(null, unauthorized("old-access"))

        assertEquals("Bearer already-new", retry?.header("Authorization"))
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `리프레시 토큰을 거절하면 로그아웃한다`() {
        val authenticator = TokenRefreshAuthenticator(session) { TokenRefresher.Result.Rejected }

        val retry = authenticator.authenticate(null, unauthorized("old-access"))

        assertNull(retry)
        assertFalse(session.isLoggedIn.value)
        assertNull(store.tokens)
    }

    @Test
    fun `서버에 닿지 못하면 로그인을 유지한다`() {
        val authenticator = TokenRefreshAuthenticator(session) { TokenRefresher.Result.Unavailable }

        val retry = authenticator.authenticate(null, unauthorized("old-access"))

        assertNull(retry)
        assertTrue(session.isLoggedIn.value)
    }

    private fun unauthorized(accessToken: String): Response {
        val request = Request.Builder()
            .url("http://localhost/api/trips")
            .header("Authorization", "Bearer $accessToken")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }
}
