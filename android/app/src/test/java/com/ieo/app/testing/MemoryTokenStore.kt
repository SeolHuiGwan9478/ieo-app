package com.ieo.app.testing

import com.ieo.app.core.session.AuthTokens
import com.ieo.app.core.session.TokenStore

/** 암호화 없이 메모리에만 두는 토큰 저장소 */
class MemoryTokenStore(var tokens: AuthTokens?) : TokenStore {
    override fun read() = tokens
    override fun write(tokens: AuthTokens) {
        this.tokens = tokens
    }
    override fun clear() {
        tokens = null
    }
}
