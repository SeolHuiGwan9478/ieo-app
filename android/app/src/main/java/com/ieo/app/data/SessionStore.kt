package com.ieo.app.data

import android.content.Context

/**
 * 로그인 상태를 기기에 저장한다. 서버가 발급한 토큰이 있으면 로그인된 것으로 본다.
 * TODO: 서버 인증(/api/auth/verify)이 구현되면 로그인 성공 시 saveTokens()를 부른다.
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getString(KEY_ACCESS_TOKEN, null) != null

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "ieo_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
