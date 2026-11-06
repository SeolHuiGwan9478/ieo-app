package com.ieo.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBannersTest {

    @Test
    fun `들어온 순서대로 하나씩 보여 주고 끝나면 다음으로 넘어간다`() {
        val messages = MessageBanners()
        assertNull(messages.current)

        messages.show("저장했어요")
        messages.show("연결이 끊겼어요")
        val first = messages.current!!
        assertEquals("저장했어요", first.text)
        assertEquals(MESSAGE_VISIBLE_MS, first.visibleMs)

        messages.done(first.id)
        assertEquals("연결이 끊겼어요", messages.current?.text)

        messages.done(messages.current!!.id)
        assertNull(messages.current)
    }

    @Test
    fun `같은 말을 연달아 넣어도 한 번만 보여 준다`() {
        val messages = MessageBanners()
        messages.show("연결이 끊겼어요")
        messages.show("연결이 끊겼어요")

        messages.done(messages.current!!.id)

        assertNull(messages.current)
    }

    @Test
    fun `서버 알림 id와 겹치지 않게 음수 id를 쓴다`() {
        val messages = MessageBanners()
        messages.show("저장했어요")

        assertTrue(messages.current!!.id < 0)
    }
}
