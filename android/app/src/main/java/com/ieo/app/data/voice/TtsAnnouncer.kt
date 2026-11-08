package com.ieo.app.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ieo.app.domain.voice.Announcement
import com.ieo.app.domain.voice.Announcer
import java.util.PriorityQueue

/**
 * 음성 안내를 차례대로 말한다.
 * - 한 번에 하나씩, 우선순위 순서로 (UX-010)
 * - 말하는 동안만 오디오 포커스를 잠깐 받는다(GAIN_TRANSIENT_MAY_DUCK). 음악은 소리가 줄고(UX-013),
 *   내비가 안내 음성으로 포커스를 쥐고 있으면 포커스가 돌아올 때까지 기다린다(UX-012, 지연 포커스)
 * - 말하는 중에 포커스를 뺏기면 멈추고, 돌아오면 그 문장부터 다시 말한다
 * - 엔진은 [engines] 순서로 시도한다: Pleos 차량 TTS → Android 기본 TTS (UX-030)
 * 상태는 메인 스레드에서만 바꾼다.
 */
class TtsAnnouncer(
    context: Context,
    private val engines: List<(Context, AudioAttributes) -> SpeechEngine> = listOf({ c, a -> PleosSpeechEngine(c, a) }, ::AndroidSpeechEngine),
) : Announcer {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(::onFocusChange, main)
        .build()

    private class Queued(val announcement: Announcement, val order: Long, val onSpoken: () -> Unit)

    private val queue = PriorityQueue(compareBy<Queued>({ it.announcement.priority.ordinal }, { it.order }))
    private var order = 0L
    private var current: Queued? = null
    private var engine: SpeechEngine? = null
    private var starting = false
    private var hasFocus = false
    private var waitingForFocus = false

    override fun cancel(key: String) {
        main.post { drop(key); speakNext() }
    }

    /** 기다리는 것은 빼고, 말하는 중이면 멈춘다. 멈춘 문장의 늦은 콜백은 [finished]가 무시한다 */
    private fun drop(key: String) {
        queue.filter { it.announcement.key == key }.forEach {
            queue.remove(it)
            it.onSpoken()
        }
        current?.takeIf { it.announcement.key == key }?.let {
            current = null
            engine?.stop()
            it.onSpoken()
        }
    }

    override fun say(announcement: Announcement, onSpoken: () -> Unit) {
        main.post {
            announcement.key?.let(::drop)
            queue += Queued(announcement, order++, onSpoken)
            if (engine == null) startEngine(0) else speakNext()
        }
    }

    /** [index]번째 엔진부터 시도한다. 모두 안 되면 쌓인 안내를 버리고 다음 안내 때 처음부터 다시 시도한다 */
    private fun startEngine(index: Int) {
        if (starting && index == 0) return
        if (index >= engines.size) {
            Log.w(TAG, "쓸 수 있는 TTS 엔진이 없음")
            starting = false
            queue.forEach { it.onSpoken() }
            queue.clear()
            return
        }
        starting = true
        val candidate = engines[index](appContext, attributes)
        candidate.start { ready ->
            main.post {
                if (ready) {
                    starting = false
                    engine = candidate
                    speakNext()
                } else {
                    candidate.release()
                    startEngine(index + 1)
                }
            }
        }
    }

    private fun speakNext() {
        val engine = engine ?: return
        if (current != null || waitingForFocus) return
        if (queue.isEmpty()) {
            releaseFocus()
            return
        }
        if (!hasFocus) {
            when (audio.requestAudioFocus(focusRequest)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> hasFocus = true
                // 내비가 말하는 중. 포커스가 오면 onFocusChange에서 이어서 말한다
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    waitingForFocus = true
                    return
                }
                // 전화 중 등. 잠시 뒤 다시 묻는다
                else -> {
                    main.postDelayed(::speakNext, FOCUS_RETRY_MILLIS)
                    return
                }
            }
        }
        val next = queue.poll() ?: return
        current = next
        engine.speak(next.announcement.text) { main.post { finished(next) } }
    }

    /** 멈추게 한 문장의 늦은 콜백은 무시한다 */
    private fun finished(spoken: Queued) {
        if (current !== spoken) return
        current = null
        spoken.onSpoken()
        speakNext()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                waitingForFocus = false
                speakNext()
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                // 끊긴 문장은 처음부터 다시 말한다
                current?.let { queue += it }
                current = null
                engine?.stop()
                if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    waitingForFocus = true
                } else {
                    main.postDelayed(::speakNext, FOCUS_RETRY_MILLIS)
                }
            }
        }
    }

    private fun releaseFocus() {
        if (!hasFocus) return
        audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private companion object {
        const val TAG = "TtsAnnouncer"
        const val FOCUS_RETRY_MILLIS = 5_000L
    }
}
