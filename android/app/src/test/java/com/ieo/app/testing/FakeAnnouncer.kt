package com.ieo.app.testing

import com.ieo.app.domain.voice.Announcement
import com.ieo.app.domain.voice.Announcer

/** 말한 문장을 모아 둔다 */
class FakeAnnouncer : Announcer {
    val said = mutableListOf<Announcement>()
    val texts: List<String> get() = said.map { it.text }

    val cancelled = mutableListOf<String>()

    override fun cancel(key: String) {
        cancelled += key
    }

    override fun say(announcement: Announcement) {
        said += announcement
    }
}
