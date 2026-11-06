package com.ieo.app.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ieo.app.BuildConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener

/**
 * 문장을 소리로 바꾸는 엔진. [TtsAnnouncer]가 차례를 정하고, 엔진은 받은 문장을 말하기만 한다.
 * 콜백은 아무 스레드에서나 올 수 있다. 받는 쪽([TtsAnnouncer])이 메인 스레드로 옮긴다.
 */
interface SpeechEngine {
    /** 준비되면 onReady(true), 이 차에서 쓸 수 없으면 onReady(false) */
    fun start(onReady: (Boolean) -> Unit)

    /** 말을 끝내거나 실패하거나 멈추면 onDone */
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
    fun release()
}

/**
 * Pleos 차량 TTS (TextToSpeech SDK). 차량 음성(한국어 화자)으로 말한다.
 * 권한 pleos.car.permission.TTS_SERVICE가 필요하다. 에뮬레이터의 Android 기본 TTS는 이 엔진에 붙지 못해서 이쪽을 먼저 쓴다.
 */
class PleosSpeechEngine(
    context: Context,
    private val attributes: AudioAttributes,
    private val clientId: String = BuildConfig.PLEOS_CLIENT_ID,
    private val clientSecret: String = BuildConfig.PLEOS_CLIENT_SECRET,
) : SpeechEngine {

    private val tts = PleosTextToSpeech(context.applicationContext, Mode.HYBRID)

    @Volatile
    private var onDone: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = finish()
        override fun onStop() = finish()
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(error: ErrorType) {
            Log.w(TAG, "말하지 못함: $error")
            finish()
        }
    }

    override fun start(onReady: (Boolean) -> Unit) {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA)
                    tts.setAudioAttributes(attributes)
                    tts.addEventListener(events)
                    registerThen { onReady(true) }
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "Pleos TTS를 시작하지 못함: $error")
                    onReady(false)
                }
            })
        } catch (e: Exception) {
            // Pleos 차량이 아니거나 권한이 없다
            Log.w(TAG, "Pleos TTS를 쓸 수 없음", e)
            onReady(false)
        }
    }

    /**
     * 앱 인증을 받은 뒤 [next]. 서버 음성 합성에 필요하다. 인증 정보가 없거나 실패해도 말하기는 시도한다 (기기 안 합성이 있을 수 있다).
     * 응답이 오지 않아도 음성 안내가 멈추지 않게 [REGISTER_TIMEOUT_MS] 뒤에는 넘어간다
     */
    private fun registerThen(next: () -> Unit) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            Log.w(TAG, "Pleos 인증 정보가 없음 (local.properties)")
            next()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val once = AtomicBoolean(false)
        val proceed = { if (once.compareAndSet(false, true)) next() }
        handler.postDelayed({ Log.w(TAG, "Pleos 앱 인증 응답 없음"); proceed() }, REGISTER_TIMEOUT_MS)
        tts.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "Pleos 앱 인증 성공")
                handler.removeCallbacksAndMessages(null)
                proceed()
            }

            override fun onFailed(error: ErrorType) {
                Log.w(TAG, "Pleos 앱 인증 실패: $error")
                handler.removeCallbacksAndMessages(null)
                proceed()
            }
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        this.onDone = onDone
        try {
            tts.speak(text, true)
        } catch (e: Exception) {
            Log.w(TAG, "말하지 못함", e)
            finish()
        }
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun release() {
        runCatching {
            tts.removeEventListener(events)
            tts.release()
        }
    }

    private fun finish() {
        val done = onDone ?: return
        onDone = null
        done()
    }

    private companion object {
        const val REGISTER_TIMEOUT_MS = 5_000L
        const val TAG = "PleosSpeechEngine"
    }
}

/** Android 기본 TextToSpeech. Pleos가 아닌 기기(휴대폰 에뮬레이터 등)에서 쓴다 */
class AndroidSpeechEngine(private val context: Context, private val attributes: AudioAttributes) : SpeechEngine {

    private var tts: TextToSpeech? = null
    private val pending = mutableMapOf<String, () -> Unit>()
    private var nextId = 0

    override fun start(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.w(TAG, "Android TTS를 시작하지 못함: $status")
                onReady(false)
                return@TextToSpeech
            }
            engine.setLanguage(Locale.KOREAN)
            engine.setAudioAttributes(attributes)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finish(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(utteranceId)
            })
            onReady(true)
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        val id = "ieo-${nextId++}"
        synchronized(pending) { pending[id] = onDone }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
    }

    private fun finish(id: String?) {
        val done = synchronized(pending) { pending.remove(id) } ?: return
        done()
    }

    private companion object {
        const val TAG = "AndroidSpeechEngine"
    }
}
