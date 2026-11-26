package com.vernutsya

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class TrackerService : Service(), LocationListener {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"
        private const val ACTION_ECO = "eco"
        private const val ACTION_REFRESH = "refresh"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"
        private const val EXTRA_SAVE = "save"
        private const val EXTRA_ECO = "eco"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIF_ID = 1

        fun start(context: Context, lat: Double, lon: Double) {
            ContextCompat.startForegroundService(
                context, Intent(context, TrackerService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LON, lon)
            )
        }
        fun resume(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackerService::class.java).setAction(ACTION_RESUME))
        }
        fun stop(context: Context, save: Boolean) {
            context.startService(Intent(context, TrackerService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_SAVE, save))
        }
        fun setEco(context: Context, eco: Boolean) {
            context.startService(Intent(context, TrackerService::class.java).setAction(ACTION_ECO).putExtra(EXTRA_ECO, eco))
        }
        fun refresh(context: Context) {
            context.startService(Intent(context, TrackerService::class.java).setAction(ACTION_REFRESH))
        }
        fun formatDistance(m: Float): String =
            if (m < 1000) "${m.roundToInt()} м" else String.format(java.util.Locale.US, "%.2f км", m / 1000)
    }

    private lateinit var lm: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifAt = 0L
    private var batteryRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i == null || !AppSettings.state.value.autoEco) return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            if (pct <= 15 && plugged == 0 && TrackStore.state.value.tracking && !TrackStore.state.value.ecoMode) {
                TrackStore.setEco(true)
                requestUpdates()
                updateNotification()
                Toast.makeText(this@TrackerService, "Низкий заряд — включён ЭКО-режим", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TrackStore.init(this)
        AppSettings.init(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val channel = NotificationChannel(CHANNEL_ID, "Запись трека", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        try {
            ContextCompat.registerReceiver(
                this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            batteryRegistered = true
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (intent.getBooleanExtra(EXTRA_SAVE, true)) {
                    if (TrackStore.saveCurrentWalk() != null)
                        Toast.makeText(this, "Маршрут сохранён", Toast.LENGTH_SHORT).show()
                }
                shutdown(); return START_NOT_STICKY
            }
            ACTION_START -> {
                TrackStore.beginTracking(intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LON, 0.0))
                goForeground()
            }
            ACTION_ECO -> {
                TrackStore.setEco(intent.getBooleanExtra(EXTRA_ECO, false))
                if (TrackStore.state.value.tracking) { requestUpdates(); updateNotification() } else stopSelf()
            }
            ACTION_REFRESH -> { if (TrackStore.state.value.tracking) updateNotification() else stopSelf() }
            else -> {
                if (!TrackStore.restore()) { stopSelf(); return START_NOT_STICKY }
                goForeground()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun goForeground() {
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vernutsya:gps").apply { acquire() }
        }
        requestUpdates()
        try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { TrackStore.onNewLocation(it) } } catch (_: Exception) {}
        updateNotification()
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        try {
            lm.removeUpdates(this)
            val eco = TrackStore.state.value.ecoMode
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                if (eco) 5000L else 1000L,
                if (eco) 5f else 0f,
                this, Looper.getMainLooper()
            )
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        TrackStore.onNewLocation(location)
        val now = System.currentTimeMillis()
        if (now - lastNotifAt >= 5000) { updateNotification(); lastNotifAt = now }
    }

    private fun buildNotification(): Notification {
        val s = TrackStore.state.value
        val dist = distanceToTarget()
        val text = "До «${s.target?.name ?: "цели"}»: ${dist?.let { formatDistance(it) } ?: "—"}" +
                " • Пройдено: ${formatDistance(s.walkedMeters)}" + if (s.ecoMode) " • ЭКО" else ""
        val contentPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPi = PendingIntent.getService(this, 1,
            Intent(this, TrackerService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_SAVE, true),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("🍄 Вернутся — запись трека")
            .setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setWhen(s.startedAt).setShowWhen(true).setUsesChronometer(true)
            .setContentIntent(contentPi)
            .addAction(0, "Завершить (сохранить)", stopPi)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun distanceToTarget(): Float? {
        val s = TrackStore.state.value
        val t = s.target ?: return null
        val loc = s.location ?: return null
        val r = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, t.lat, t.lon, r)
        return r[0]
    }

    private fun shutdown() {
        lm.removeUpdates(this)
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        TrackStore.clearAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (batteryRegistered) { try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {} }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}