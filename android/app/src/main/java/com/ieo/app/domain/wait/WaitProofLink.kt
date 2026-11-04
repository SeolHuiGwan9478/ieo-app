package com.ieo.app.domain.wait

import java.time.Instant

/**
 * 대기 증빙 링크 (WAIT-020). 로그인 없이 열리고, 주선사는 이 링크로 대기 시간을 확인한다.
 * 한 대기 기록에 링크는 하나라서 여러 번 만들어도 같은 링크가 온다.
 */
data class WaitProofLink(val url: String, val expiresAt: Instant)
