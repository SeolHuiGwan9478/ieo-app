package com.ieo.app.data.wait

import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.wait.WaitPolicy
import com.ieo.app.domain.wait.WaitRecord
import com.ieo.app.domain.wait.WaitStatus
import java.time.Instant
import kotlinx.serialization.Serializable

// 서버 domain/wait/dto와 같은 모양. 쓰지 않는 필드는 뺐다

/** 시각을 비우면 서버가 받은 시각으로 남긴다 */
@Serializable
data class WaitTimeRequest(val at: String? = null)

@Serializable
data class WaitRecordResponse(
    val stopType: String,
    val status: String,
    val arrivedAt: String,
    val workStartedAt: String? = null,
    val departedAt: String? = null,
    val chargeableFrom: String? = null,
    val chargeableAmount: Long? = null,
    val policy: WaitPolicyResponse? = null,
) {
    fun toDomain(): WaitRecord {
        val arrived = Instant.parse(arrivedAt)
        return WaitRecord(
            stopType = StopType.valueOf(stopType),
            status = WaitStatus.valueOf(status),
            arrivedAt = arrived,
            workStartedAt = workStartedAt?.let(Instant::parse),
            departedAt = departedAt?.let(Instant::parse),
            chargeableFrom = chargeableFrom?.let(Instant::parse) ?: arrived,
            chargeableAmount = chargeableAmount,
            policy = policy?.toDomain(),
        )
    }
}

@Serializable
data class WaitPolicyResponse(
    val freeMinutes: Int,
    val unitMinutes: Int,
    val unitAmount: Long,
    val maxAmount: Long,
    val rounding: String,
) {
    fun toDomain() = WaitPolicy(
        freeMinutes = freeMinutes,
        unitMinutes = unitMinutes,
        unitAmount = unitAmount,
        maxAmount = maxAmount,
        roundUp = rounding != "DOWN",
    )
}
