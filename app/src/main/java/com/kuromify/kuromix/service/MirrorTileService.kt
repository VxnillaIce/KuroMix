package com.kuromify.kuromix.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile to mirror the current foreground app to the rear display.
 */
class MirrorTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "MirrorTileService"
        private val IGNORE_PACKAGES = listOf(
            "com.android.systemui",
            "com.miui.home",
            "com.kuromify.kuromix",
            "com.xiaomi.subscreencenter"
        )
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "[KUROMIX_LOG] Mirror tile clicked")
        
        serviceScope.launch {
            val context = applicationContext
            val whitelistManager = WhitelistManager(context)
            val rearManager = RearDisplayManager(context)
            val displayId = rearManager.primaryRearDisplayId() ?: 1

            // 1. Get actual top app (ignoring SystemUI/Launcher/KuroMix itself)
            val taskId = RootShell.getTopTaskId(IGNORE_PACKAGES)
            Log.d(TAG, "[KUROMIX_LOG] Top taskId found: $taskId")
            if (taskId == null) {
                Log.d(TAG, "[KUROMIX_LOG] No valid top task found to mirror")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No app found to mirror", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            
            // 2. Identify the package of this task to check whitelist
            val packageName = RootShell.getPackageNameForTask(taskId) ?: ""
            Log.d(TAG, "[KUROMIX_LOG] Package name for task $taskId: $packageName")
            
            if (packageName.isEmpty()) {
                Log.d(TAG, "[KUROMIX_LOG] Could not resolve package name for task $taskId")
                return@launch
            }
            
            // 3. Load config for this app
            val config = whitelistManager.getAppConfig(packageName)
            if (!config.enabled) {
                Log.d(TAG, "[KUROMIX_LOG] App $packageName is not enabled in whitelist")
                return@launch 
            }
            
            val globalLensOpt = whitelistManager.lensOptimizationFlow.first()
            val keepAwakeGlobal = whitelistManager.keepAwakeEnabledFlow.first()
            val antiKillGlobal = whitelistManager.antiKillEnabledFlow.first()

            Log.d(TAG, "[KUROMIX_LOG] Config loaded: lensOpt=$globalLensOpt, keepAwake=$keepAwakeGlobal, antiKill=$antiKillGlobal")

            // 4. Apply settings
            val targetOffset = if (globalLensOpt) config.lensOffset else 0
            val targetDpi = config.dpi
            
            Log.d(TAG, "[KUROMIX_LOG] Applying display settings: displayId=$displayId, offset=$targetOffset, dpi=$targetDpi")
            RootShell.applyDisplayOffset(displayId, targetOffset)
            RootShell.setDisplayDpi(displayId, targetDpi)

            if (keepAwakeGlobal) {
                Log.d(TAG, "[KUROMIX_LOG] Setting keep-awake props")
                RootShell.disableSubScreenDoubleTap()
                RootShell.setSubScreenTimeout(86400) // 24 hours
            }

            if (antiKillGlobal) {
                Log.d(TAG, "[KUROMIX_LOG] Suppressing sub-screen launcher")
                RootShell.suppressSubScreenLauncher()
            }
            
            delay(200) // Synchronization delay

            // 5. Migrate task
            Log.d(TAG, "[KUROMIX_LOG] Moving task $taskId to display $displayId")
            val result = RootShell.moveTaskToDisplay(taskId, displayId)
            
            // 6. Start Monitor Service (It will handle showing the notification)
            if (result.ok) {
                val intent = Intent(context, MirrorMonitorService::class.java)
                context.startService(intent)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        val rearManager = RearDisplayManager(applicationContext)
        
        if (rearManager.isRearDisplayPresent()) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Ready to mirror"
            }
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Rear display not found"
            }
        }
        tile.updateTile()
    }
}
