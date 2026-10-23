package com.example.service

import android.app.AlarmManager
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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.formatTimeRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AutoRefreshService : Service() {

    companion object {
        const val CHANNEL_ID = "auto_refresh_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_RELOAD_NOW = "com.example.service.ACTION_RELOAD_NOW"

        fun startService(context: Context) {
            val intent = Intent(context, AutoRefreshService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutoRefreshService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AutoRefreshManager.setBackgroundServiceActive(true)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoRefresh::WakeLockTag")
        wakeLock?.setReferenceCounted(false)

        observeStateAndReloads()
    }

    private fun observeStateAndReloads() {
        serviceScope.launch {
            AutoRefreshManager.state.collectLatest { state ->
                // Manage wake lock
                if (state.isRunning && !state.isPaused) {
                    if (wakeLock?.isHeld != true) {
                        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safe
                    }
                } else {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                }
                updateNotification(state.url, state.remainingSeconds, state.totalRefreshes, state.isPaused, state.isRunning)
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            AutoRefreshManager.reloadTrigger.collectLatest {
                val state = AutoRefreshManager.state.value
                // Perform background HTTP keepalive ping for the website
                try {
                    val request = Request.Builder()
                        .url(state.url)
                        .header("User-Agent", if (state.desktopMode) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        } else {
                            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        })
                        .build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {
                    // Suppress network errors in background keepalive ping
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val initialNotification = buildNotification(
                    AutoRefreshManager.state.value.url,
                    AutoRefreshManager.state.value.remainingSeconds,
                    AutoRefreshManager.state.value.totalRefreshes,
                    AutoRefreshManager.state.value.isPaused,
                    AutoRefreshManager.state.value.isRunning
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }
            }
            ACTION_PAUSE -> AutoRefreshManager.pause()
            ACTION_RESUME -> AutoRefreshManager.resume()
            ACTION_RELOAD_NOW -> AutoRefreshManager.triggerReload()
            ACTION_STOP -> {
                AutoRefreshManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suru Auto 24/7 Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live website refresh status and background keep-alive"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        url: String,
        remainingSec: Int,
        totalRefreshes: Int,
        isPaused: Boolean,
        isRunning: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, AutoRefreshService::class.java).apply {
            action = if (isPaused || !isRunning) ACTION_RESUME else ACTION_PAUSE
        }
        val pendingPauseResume = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reloadNowIntent = Intent(this, AutoRefreshService::class.java).apply {
            action = ACTION_RELOAD_NOW
        }
        val pendingReloadNow = PendingIntent.getService(
            this,
            2,
            reloadNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AutoRefreshService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            !isRunning -> "Stopped • Ready to run 24/7"
            isPaused -> "Paused (Total: $totalRefreshes reloads)"
            else -> "Next reload in ${formatTimeRemaining(remainingSec)} • Reloaded $totalRefreshes times"
        }

        val pauseResumeTitle = if (isPaused || !isRunning) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Suru Auto: $url")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, pauseResumeTitle, pendingPauseResume)
            .addAction(0, "Reload Now", pendingReloadNow)
            .addAction(0, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(
        url: String,
        remainingSec: Int,
        totalRefreshes: Int,
        isPaused: Boolean,
        isRunning: Boolean
    ) {
        val notification = buildNotification(url, remainingSec, totalRefreshes, isPaused, isRunning)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep running 24/7 until explicitly stopped by user
        if (AutoRefreshManager.state.value.isRunning) {
            val restartServiceIntent = Intent(applicationContext, AutoRefreshService::class.java).apply {
                action = ACTION_START
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                999,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        serviceScope.cancel()
        AutoRefreshManager.setBackgroundServiceActive(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
