package com.kuromify.kuromix.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Background service that monitors the rear display and pushes the mirror
 * state as a HyperIsland card (instead of a plain notification).
 *
 * Only re-posts when the top package on the rear display actually changes,
 * so we don't spam the island every polling tick.
 */
class MirrorMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    companion object {
        private const val TAG = "MirrorMonitorService"
        private const val REAR_DISPLAY_ID = 1
        private const val MONITOR_INTERVAL_MS = 3000L
        private const val SUBSCREEN_PKG = "com.xiaomi.subscreencenter"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[KUROMIX_LOG] MirrorMonitorService started")
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            var lastPackage: String? = null

            while (isActive) {
                try {
                    val topPackage = RootShell.getTopPackageOnDisplay(REAR_DISPLAY_ID)

                    if (topPackage != null && topPackage != SUBSCREEN_PKG) {
                        // Only re-post when the app actually changes.
                        if (topPackage != lastPackage) {
                            Log.d(TAG, "[KUROMIX_LOG] Mirroring $topPackage on rear")
                            SuperIslandManager.showMirrorIsland(
                                applicationContext,
                                topPackage
                            )
                            lastPackage = topPackage
                        }
                    } else {
                        if (lastPackage != null) {
                            Log.d(TAG, "[KUROMIX_LOG] No active app on rear, clearing island")
                            SuperIslandManager.cancelMirrorIsland(applicationContext)
                            lastPackage = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[KUROMIX_LOG] Error in monitor loop", e)
                }
                delay(MONITOR_INTERVAL_MS.milliseconds)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "[KUROMIX_LOG] MirrorMonitorService destroyed")
        SuperIslandManager.cancelMirrorIsland(applicationContext)
        SuperIslandManager.cancelMirrorNotification(applicationContext)
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}