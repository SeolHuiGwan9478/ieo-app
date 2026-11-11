package com.ieo.app.data.site

import com.ieo.app.domain.site.HeightWarning
import com.ieo.app.domain.site.SiteCard
import kotlinx.serialization.Serializable

// 서버 domain/site/dto와 같은 모양. 쓰지 않는 필드는 뺐다

@Serializable
data class SiteCardResponse(
    val id: Long,
    val name: String,
    val attributes: SiteAttributesResponse,
    val heightWarning: HeightWarningResponse,
    val stats: SiteStatsResponse? = null,
    val notes: List<SiteNoteResponse> = emptyList(),
) {
    fun toDomain() = SiteCard(
        id = id,
        name = name,
        heightLimitMeters = attributes.heightLimitM,
        heightWarning = runCatching { HeightWarning.valueOf(heightWarning.level) }.getOrDefault(HeightWarning.UNKNOWN),
        forklift = attributes.forklift,
        parking = attributes.parking,
        averageWaitMinutes = stats?.avgWaitMinutes,
        notes = notes.map { it.text },
        reportedAt = attributes.updatedAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() },
    )
}

@Serializable
data class SiteAttributesResponse(
    val heightLimitM: Double? = null,
    val forklift: Boolean? = null,
    val parking: Boolean? = null,
    /** 높이·지게차·주차 중 가장 최근에 남긴 시각 */
    val updatedAt: String? = null,
)

@Serializable
data class HeightWarningResponse(val level: String)

@Serializable
data class SiteStatsResponse(val avgWaitMinutes: Int? = null)

@Serializable
data class SiteNoteResponse(val text: String)
