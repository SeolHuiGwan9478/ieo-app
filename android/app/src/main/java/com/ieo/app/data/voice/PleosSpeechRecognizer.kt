package com.ieo.app.data.voice

import com.ieo.app.BuildConfig
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ieo.app.domain.voice.SpeechRecognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ai.pleos.playground.stt.SpeechToText as PleosSpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener

/**
 * Pleos SpeechToText SDK로 차량 마이크를 듣는다. 권한 pleos.car.permission.STT_SERVICE.
 * 기기 안 인식(ON_DEVICE)을 쓴다: 짧은 명령에는 충분하고 터널에서도 된다.
 * HYBRID는 서버가 PERMISSION_DENIED로 거절한 뒤에야 넘어가서 앞의 몇 초를 놓쳤다 (2026-11-06 에뮬레이터).
 * 쓸 수 없으면 듣지 못한 것(null)으로 돌려준다.
 */
class PleosSpeechRecognizer(context: Context) : SpeechRecognizer {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val startLock = Mutex()
    private var stt: PleosSpeechToText? = null

    override suspend fun listen(timeoutMillis: Long): String? {
        val engine = startLock.withLock { stt ?: start()?.also { stt = it } } ?: return null
        return withTimeoutOrNull(timeoutMillis) {
            // 조용하면 엔진이 빈 결과로 끝낸다. 시간이 남았으면 다시 듣는다
            var heard: String?
            do {
                heard = listenOnce(engine)
                if (heard.isNullOrBlank()) delay(RELISTEN_DELAY_MILLIS)
            } while (heard.isNullOrBlank())
            heard
        }
    }

    private suspend fun listenOnce(engine: PleosSpeechToText): String? = suspendCancellableCoroutine { result ->
        val done = AtomicBoolean(false)
        lateinit var listener: ResultListener
        var loudest = Double.NEGATIVE_INFINITY
        fun finish(text: String?) {
            if (!done.compareAndSet(false, true)) return
            debug("들은 말: \"$text\", 가장 큰 소리(rms): $loudest")
            main.removeCallbacksAndMessages(listener)
            engine.removeListener(listener)
            if (result.isActive) result.resume(text)
        }
        listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onStartedRecognition() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) {
                if (rms > loudest) loudest = rms
            }
            override fun onUpdated(stt: String, completed: Boolean) {
                if (completed) finish(stt)
            }

            // 끝났다는 신호가 최종 결과보다 먼저 온다. 조금 기다렸다가 결과가 없으면 끝낸다
            override fun onEndedRecognition() {
                main.postDelayed({ finish(null) }, listener, FINAL_RESULT_GRACE_MILLIS)
            }

            override fun onError(errorType: ErrorType) {
                Log.w(TAG, "음성 인식 실패: $errorType")
                finish(null)
            }
        }
        result.invokeOnCancellation {
            if (done.compareAndSet(false, true)) {
                main.removeCallbacksAndMessages(listener)
                runCatching {
                    engine.removeListener(listener)
                    engine.stop()
                }
            }
        }
        try {
            engine.addListener(listener)
            debug("듣기 시작")
            engine.request()
        } catch (e: Exception) {
            Log.w(TAG, "음성 인식을 시작하지 못함", e)
            finish(null)
        }
    }

    /** 들은 말은 개인정보라 개발용 빌드에서만 남긴다 */
    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private suspend fun start(): PleosSpeechToText? = suspendCancellableCoroutine { result ->
        try {
            val engine = PleosSpeechToText(appContext, Mode.ON_DEVICE)
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    engine.setLocale(Locale.KOREA)
                    if (result.isActive) result.resume(engine)
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "Pleos 음성 인식을 시작하지 못함: $errorType")
                    if (result.isActive) result.resume(null)
                }
            })
        } catch (e: Exception) {
            // Pleos 차량이 아니거나 권한이 없다
            Log.w(TAG, "Pleos 음성 인식을 쓸 수 없음", e)
            if (result.isActive) result.resume(null)
        }
    }

    private companion object {
        const val TAG = "PleosSpeechRecognizer"
        const val FINAL_RESULT_GRACE_MILLIS = 1_500L
        const val RELISTEN_DELAY_MILLIS = 200L
    }
}
