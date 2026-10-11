package com.zahxrun.ttslive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.zahxrun.ttslive.tiktok.ConnState

/**
 * Foreground service: menjaga proses tetap hidup selama live komentar TikTok berjalan,
 * memegang WakeLock + WifiLock, dan menampilkan notifikasi status dengan tombol Stop.
 * Logika sendiri ada di [LiveController]. (Streaming video RTMP dinonaktifkan untuk
 * mengurangi delay.)
 */
class LiveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        acquireLocks()
        LiveController.get(this).onStatus = { _, _ -> refreshNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                LiveController.get(this).stopLive()
                stopSelf()
            }
        }
        // Kalau proses dimatikan sistem, controller ikut hilang, jadi tidak ada gunanya restart otomatis.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LiveController.get(this).onStatus = null
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ---- lock ----

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ttslive:live").apply {
            setReferenceCounted(false)
            acquire(WAKE_TIMEOUT_MS)
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm.createWifiLock(mode, "ttslive:live").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock = null
    }

    // ---- notifikasi ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL_ID, "Status live", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val controller = LiveController.get(this)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LiveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stop,
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("TTS Live: ${label(controller.connState)}")
            .setContentText(controller.connDetail)
            .setContentIntent(open)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun label(state: ConnState): String = when (state) {
        ConnState.IDLE -> "siap"
        ConnState.CONNECTING -> "menyambung"
        ConnState.CONNECTED -> "terhubung"
        ConnState.RECONNECTING -> "menyambung ulang"
        ConnState.ENDED -> "live berakhir"
        ConnState.ERROR -> "error"
    }

    companion object {
        private const val CHANNEL_ID = "live"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.zahxrun.ttslive.STOP"
        private const val WAKE_TIMEOUT_MS = 24L * 60 * 60 * 1000

        private fun launch(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun start(context: Context) {
            launch(context, Intent(context, LiveService::class.java))
        }

        /** Hentikan service hanya kalau komentar TikTok tidak aktif. */
        fun stopIfIdle(context: Context) {
            val liveActive = LiveController.get(context).isLive
            if (!liveActive) {
                context.stopService(Intent(context, LiveService::class.java))
            }
        }
    }
}
