package com.kuromify.kuromix.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.*

/**
 * Background service that monitors the rear display and automatically
 * updates or cancels the Super Island notification.
 */
class MirrorMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    companion object {
        private const val TAG = "MirrorMonitorService"
        private const val REAR_DISPLAY_ID = 1
        private const val MONITOR_INTERVAL_MS = 3000L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[KUROMIX_LOG] MirrorMonitorService started")
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val topPackage = RootShell.getTopPackageOnDisplay(REAR_DISPLAY_ID)
                    
                    if (topPackage != null && topPackage != "com.xiaomi.subscreencenter") {
                        // App is active on rear, ensure notification is shown/updated
                        Log.v(TAG, "[KUROMIX_LOG] Detected active app on rear: $topPackage")
                        SuperIslandManager.showMirrorNotification(applicationContext, topPackage)
                    } else {
                        // No app or just launcher, clear notification
                        Log.v(TAG, "[KUROMIX_LOG] No active app detected on rear, clearing notification")
                        SuperIslandManager.cancelMirrorNotification(applicationContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[KUROMIX_LOG] Error in monitor loop", e)
                }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "[KUROMIX_LOG] MirrorMonitorService destroyed")
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
