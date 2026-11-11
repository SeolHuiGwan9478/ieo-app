package com.ieo.app.domain.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YesNoDialogTest {

    private val heard = ArrayDeque<String?>()
    private val spoken = mutableListOf<String>()
    private val asked = mutableListOf<String>()
    private var serverMeaning = YesNo.UNKNOWN

    private val dialog = YesNoDialog(
        recognizer = object : SpeechRecognizer {
            override suspend fun listen(timeoutMillis: Long) = heard.removeFirstOrNull()
        },
        classifier = object : AnswerClassifier {
            override suspend fun classify(question: String, answer: String): YesNo {
                asked += answer
                return serverMeaning
            }
        },
        speak = { spoken += it },
    )

    @Test
    fun `흔한 대답은 단어 목록으로 바로 알아듣는다`() = runTest {
        heard += "네"
        assertEquals(YesNo.YES, dialog.listen("도착하셨어요?", 15_000))
        heard += "아직이요"
        assertEquals(YesNo.NO, dialog.listen("도착하셨어요?", 15_000))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `못 알아들은 말은 서버에 묻고 그래도 모르면 다시 묻는다`() = runTest {
        heard += listOf("어 그게 말이야", "어어 들어왔지")
        serverMeaning = YesNo.UNKNOWN
        val answers = ArrayDeque(listOf(YesNo.UNKNOWN, YesNo.YES))
        val dialog = YesNoDialog(
            recognizer = object : SpeechRecognizer {
                override suspend fun listen(timeoutMillis: Long) = heard.removeFirstOrNull()
            },
            classifier = object : AnswerClassifier {
                override suspend fun classify(question: String, answer: String) = answers.removeFirst()
            },
            speak = { spoken += it },
        )

        assertEquals(YesNo.YES, dialog.listen("도착하셨어요?", 15_000))
        assertEquals(listOf(YesNoDialog.RETRY_TEXT), spoken)
    }

    @Test
    fun `대답이 없으면 시간이 다 될 때까지 기다리고 null`() = runTest {
        assertNull(dialog.listen("도착하셨어요?", 15_000))
        assertEquals(15_000L, testScheduler.currentTime)
    }
}
