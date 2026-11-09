package com.ieo.app.domain.voice

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsentAwareSpeechRecognizerTest {

    private var listened = 0
    private val delegate = object : SpeechRecognizer {
        override suspend fun listen(timeoutMillis: Long): String? {
            listened++
            return "응"
        }
    }

    @Test
    fun `동의했으면 듣는다`() = runTest {
        assertEquals("응", ConsentAwareSpeechRecognizer(delegate, { true }).listen(1_000))
    }

    @Test
    fun `동의하지 않았으면 마이크를 켜지 않는다`() = runTest {
        assertNull(ConsentAwareSpeechRecognizer(delegate, { false }).listen(1_000))
        assertEquals(0, listened)
    }
}
