package com.kuromify.kuromix.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kuromify.kuromix.R
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.*

/**
 * Background service to keep the rear display alive using a "Soft Wake" loop.
 * It sends a wake broadcast every 250ms and holds a CPU wake lock.
 */
class RearDisplayService : Service() {

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "rear_display_service"
        private const val NOTIFICATION_ID = 101
        private const val WAKE_INTERVAL_MS = 250L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        startWakeLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Double-ensure we are in foreground and loop is running
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KuroMix:SoftWakeLock").apply {
            acquire(12 * 60 * 60 * 1000L) // 12 hours max
        }
    }

    private fun startWakeLoop() {
        serviceScope.launch {
            while (isActive) {
                RootShell.wakeRear()
                delay(WAKE_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KuroMix Persistence")
            .setContentText("Keeping the rear display active (Soft Wake active)")
            .setSmallIcon(R.drawable.kuromify_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rear Display Persistence",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
