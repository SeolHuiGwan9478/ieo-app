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
 * Pleos Vehicle SDK의 CarInfo.getVINInfo로 차대번호를 읽는다. 권한: pleos.car.permission.CAR_INFO.
 * SDK는 차량 서비스에 비동기로 붙어서, 붙기 전에 물으면 실패한다. 그래서 몇 번 다시 묻는다.
 */
class PleosCarIdentity(context: Context) : CarIdentity {

    private val vehicle by lazy { Vehicle(context.applicationContext).apply { initialize() } }

    override suspend fun vin(): String? {
        repeat(ATTEMPTS) { attempt ->
            readOnce()?.let { return it }
            if (attempt < ATTEMPTS - 1) delay(RETRY_MILLIS)
        }
        return null
    }

    private suspend fun readOnce(): String? = withTimeoutOrNull(ANSWER_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { cont ->
            try {
                vehicle.getCarInfo().getVINInfo(
                    { vin -> if (cont.isActive) cont.resume(vin?.trim()?.ifBlank { null }) },
                    { error ->
                        Log.w(TAG, "차대번호를 읽지 못함", error)
                        if (cont.isActive) cont.resume(null)
                    },
                )
            } catch (e: Exception) {
                // 권한이 없거나 서비스에 아직 붙지 않았다
                Log.w(TAG, "차대번호를 읽지 못함", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private companion object {
        const val TAG = "PleosCarIdentity"
        const val ATTEMPTS = 5
        const val RETRY_MILLIS = 2_000L
        const val ANSWER_TIMEOUT_MILLIS = 3_000L
    }
}
