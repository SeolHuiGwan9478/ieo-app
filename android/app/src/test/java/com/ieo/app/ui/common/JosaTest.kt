package com.ieo.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {

    @Test
    fun `받침이 없으면 뒤의 조사를 쓴다`() {
        assertEquals("한빛물류와", josa("한빛물류", "과", "와"))
        assertEquals("한빛물류가", josa("한빛물류", "이", "가"))
    }

    @Test
    fun `받침이 있으면 앞의 조사를 쓴다`() {
        assertEquals("대한운송과", josa("대한운송", "과", "와"))
        assertEquals("대한운송이", josa("대한운송", "이", "가"))
    }

    @Test
    fun `ㄹ 받침 뒤의 으로는 로가 된다`() {
        assertEquals("평택 물류센터로", josa("평택 물류센터", "으로", "로"))
        assertEquals("대전 산업단지로", josa("대전 산업단지", "으로", "로"))
        assertEquals("서울로", josa("서울", "으로", "로"))
        assertEquals("부산 신항으로", josa("부산 신항", "으로", "로"))
    }

    @Test
    fun `숫자는 읽는 소리로 본다`() {
        assertEquals("경기80바1234로", josa("경기80바1234", "으로", "로"))
        assertEquals("경기82아5678로", josa("경기82아5678", "으로", "로"))
        assertEquals("경기82아5676으로", josa("경기82아5676", "으로", "로"))
        assertEquals("3번과", josa("3번", "과", "와"))
        assertEquals("1과", josa("1", "과", "와"))
        assertEquals("2와", josa("2", "과", "와"))
    }
}
