package com.ieo.app.data.notification

import android.content.Context
import com.ieo.app.domain.notification.LastNotificationStore

/** 마지막으로 받은 알림 id를 SharedPreferences에 남긴다 */
class PrefsLastNotificationStore(context: Context) : LastNotificationStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var lastId: Long?
        get() = if (prefs.contains(KEY_LAST_ID)) prefs.getLong(KEY_LAST_ID, 0) else null
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_LAST_ID) else putLong(KEY_LAST_ID, value) }.apply()
        }

    private companion object {
        const val PREFS_NAME = "ieo_notifications"
        const val KEY_LAST_ID = "last_id"
    }
}
