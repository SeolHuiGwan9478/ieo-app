package com.ieo.app.data.wait

import android.content.Context
import com.ieo.app.domain.trip.StopType
import com.ieo.app.domain.vehicle.GeoPoint
import com.ieo.app.domain.wait.PendingWait
import com.ieo.app.domain.wait.PendingWaitStore
import com.ieo.app.domain.wait.WaitSource
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 보내지 못한 대기 요청을 SharedPreferences에 JSON으로 남긴다. 앱을 다시 켜도 이어서 보낸다 */
class PrefsPendingWaitStore(context: Context) : PendingWaitStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<PendingWait> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Saved>>(raw).map(Saved::toDomain) }.getOrDefault(emptyList())
    }

    override fun save(pending: List<PendingWait>) {
        prefs.edit().putString(KEY, json.encodeToString(pending.map(Saved::of))).apply()
    }

    @Serializable
    private data class Saved(
        val action: String,
        val tripId: Long,
        val stop: String,
        val at: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val source: String,
    ) {
        fun toDomain() = PendingWait(
            action = PendingWait.Action.valueOf(action),
            tripId = tripId,
            stop = StopType.valueOf(stop),
            at = Instant.parse(at),
            location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
            source = WaitSource.valueOf(source),
        )

        companion object {
            fun of(pending: PendingWait) = Saved(
                action = pending.action.name,
                tripId = pending.tripId,
                stop = pending.stop.name,
                at = pending.at.toString(),
                latitude = pending.location?.latitude,
                longitude = pending.location?.longitude,
                source = pending.source.name,
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "ieo_pending_waits"
        const val KEY = "pending"
    }
}
