package com.ieo.app.data.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import com.ieo.app.domain.vehicle.CarIdentity
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos Vehicle SDK로 이 트럭 정보를 읽는다.
 * 차대번호는 CarInfo.getVINInfo (권한 CAR_INFO), 누적 연비는 Fuel.getCumulativeFuelEconomy (권한 CAR_ENERGY).
 * SDK는 차량 서비스에 비동기로 붙어서, 붙기 전에 물으면 실패한다. 그래서 몇 번 다시 묻는다.
 * 차대번호를 모르는 차(에뮬레이터 포함)는 "invalid" 같은 글자를 준다. 이런 값은 차대번호로 보지 않는다.
 * 그대로 보내면 서버가 차대번호를 모르는 차들을 모두 한 트럭으로 묶어 버린다.
 */
class PleosCarIdentity(context: Context) : CarIdentity {

    private val vehicle by lazy { Vehicle(context.applicationContext).apply { initialize() } }

    override suspend fun vin(): String? = retrying("차대번호") { onValue, onError ->
        vehicle.getCarInfo().getVINInfo({ vin -> onValue(vin?.let(::validVin)) }, onError)
    }

    /** 0 이하 같은 값(아직 집계 전)은 서버가 버린다. 여기서는 그대로 넘긴다 */
    override suspend fun cumulativeFuelEconomy(): Float? = retrying("누적 연비") { onValue, onError ->
        vehicle.getFuel().getCumulativeFuelEconomy({ value -> onValue(value) }, onError)
    }

    /** SDK가 아직 붙지 않았으면 실패하므로 몇 번 다시 묻는다 */
    private suspend fun <T : Any> retrying(
        what: String,
        ask: (onValue: (T?) -> Unit, onError: (Exception) -> Unit) -> Unit,
    ): T? {
        repeat(ATTEMPTS) { attempt ->
            readOnce(what, ask)?.let { return it }
            if (attempt < ATTEMPTS - 1) delay(RETRY_MILLIS)
        }
        return null
    }

    private suspend fun <T : Any> readOnce(
        what: String,
        ask: (onValue: (T?) -> Unit, onError: (Exception) -> Unit) -> Unit,
    ): T? = withTimeoutOrNull(ANSWER_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { cont ->
            try {
                ask(
                    { value -> if (cont.isActive) cont.resume(value) },
                    { error ->
                        Log.w(TAG, "${what}를 읽지 못함", error)
                        if (cont.isActive) cont.resume(null)
                    },
                )
            } catch (e: Exception) {
                // 권한이 없거나 서비스에 아직 붙지 않았다
                Log.w(TAG, "${what}를 읽지 못함", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** 차대번호는 영문 대문자·숫자 17자리 (I, O, Q 제외). 아니면 모르는 것으로 */
    private fun validVin(raw: String): String? {
        val vin = raw.trim().uppercase()
        return vin.takeIf { VIN_PATTERN.matches(it) }.also { if (it == null) Log.w(TAG, "차대번호가 아님: $raw") }
    }

    private companion object {
        val VIN_PATTERN = Regex("^[A-HJ-NPR-Z0-9]{17}$")
        const val TAG = "PleosCarIdentity"
        const val ATTEMPTS = 5
        const val RETRY_MILLIS = 2_000L
        const val ANSWER_TIMEOUT_MILLIS = 3_000L
    }
}
