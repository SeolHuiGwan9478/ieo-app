package com.ieo.app.data.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ieo.app.MainActivity
import com.ieo.app.R
import com.ieo.app.domain.trip.LocationTracker

/**
 * 운행 중에만 켜 두는 위치 포그라운드 서비스 (TRIP-013).
 * 내비가 앞에 뜨면 우리 앱은 뒤로 밀려 Android가 GPS를 30분에 한 번으로 줄인다.
 * 이 서비스가 떠 있으면 앱이 "앞에서 일하는 중"으로 쳐져 GPS가 계속 들어온다.
 * 위치를 읽고 보내는 일은 [com.ieo.app.domain.trip.TripLocationReporter]가 하고, 여기서는 알림만 띄운다.
 */
class TripLocationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // 앱이 뒤에 있을 때는 시작이 막힐 수 있다. 다음에 앞에 왔을 때 다시 켜진다
            Log.w(TAG, "위치 서비스를 켜지 못했습니다", it)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.trip_location_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_launcher_foreground_white)
            .setContentTitle(getString(R.string.trip_location_title))
            .setContentText(getString(R.string.trip_location_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TripLocationService"
        private const val CHANNEL_ID = "trip_location"
        private const val NOTIFICATION_ID = 13

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, TripLocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }.onFailure { Log.w(TAG, "위치 서비스를 켜지 못했습니다", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripLocationService::class.java))
        }
    }
}

/** 운행이 있으면 위치 서비스를 켜고 없으면 끈 뒤, 실제 위치 보내기는 [delegate]에 맡긴다 */
class ForegroundLocationTracker(
    context: Context,
    private val delegate: LocationTracker,
) : LocationTracker {

    private val appContext = context.applicationContext
    private var current: Long? = null

    override fun track(tripId: Long?) {
        if (tripId != current) {
            current = tripId
            if (tripId != null) TripLocationService.start(appContext) else TripLocationService.stop(appContext)
        }
        delegate.track(tripId)
    }
}
