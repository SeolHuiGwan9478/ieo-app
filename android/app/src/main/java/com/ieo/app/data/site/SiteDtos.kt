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
        averageWorkMinutes = stats?.avgWorkMinutes,
        notes = notes.map { it.text },
    )
}

@Serializable
data class SiteAttributesResponse(
    val heightLimitM: Double? = null,
    val forklift: Boolean? = null,
    val parking: Boolean? = null,
)

@Serializable
data class HeightWarningResponse(val level: String)

@Serializable
data class SiteStatsResponse(val avgWaitMinutes: Int? = null, val avgWorkMinutes: Int? = null)

@Serializable
data class SiteNoteResponse(val text: String)
