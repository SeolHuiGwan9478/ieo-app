package com.ieo.app.data.ledger

import android.content.Context
import com.ieo.app.domain.ledger.RouteMarks

/** 운행마다 하차지로 처음 안내받은 경로 거리(m) (키: 운행 id) */
class PrefsRouteMarks(context: Context) : RouteMarks {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveFirst(tripId: Long, meters: Int) {
        val key = tripId.toString()
        if (!prefs.contains(key)) prefs.edit().putInt(key, meters).apply()
    }

    override fun take(tripId: Long): Int? {
        val key = tripId.toString()
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, 0).also { prefs.edit().remove(key).apply() }
    }

    private companion object {
        const val PREFS_NAME = "route_marks"
    }
}
