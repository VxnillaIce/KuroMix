package com.kuromify.kuromix.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.kuromify.kuromix.data.WhitelistManager
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile to move the current task back from the rear display
 * to the main display, reset the display, and tear down the mirror island.
 *
 * Gated on the "Enable Mirroring" switch in the Rear Screen settings screen.
 * If the module is off, the tile refuses to act and shows a toast pointing
 * the user at the app.
 */
class UnmirrorTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "UnmirrorTileService"
        private const val TOAST_MODULE_OFF =
            "Please enable Mirroring and Root Access in the app"
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "[KUROMIX_LOG] Unmirror tile clicked")

        serviceScope.launch {
            val context = applicationContext
            val whitelistManager = WhitelistManager(context)

            // Gate on the master switch
            val mirrorEnabled =
                whitelistManager.mirrorModuleEnabledFlow.first()

            if (!mirrorEnabled) {
                Log.d(TAG, "[KUROMIX_LOG] Mirror module disabled, refusing")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        TOAST_MODULE_OFF,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            val rearManager = RearDisplayManager(context)
            val displayId = rearManager.primaryRearDisplayId() ?: 1

            // 1. Find the task that is actually ON the rear display
            val taskId = RootShell.getTaskIdOnDisplay(displayId)

            if (taskId != null) {
                // 2. Move it back to primary (0)
                RootShell.moveTaskToDisplay(taskId, 0)
            }

            // 3. Cleanup: reset rear display to defaults and stop monitor
            RootShell.resetDisplayArea(displayId)
            RootShell.setDisplayDpi(displayId, null)

            // Tell the LSPosed hook to stop keeping the rear display awake.
            RootShell.setKeepAwakeProp(false)

            val monitorIntent = Intent(
                applicationContext,
                MirrorMonitorService::class.java
            )
            applicationContext.stopService(monitorIntent)

            // Cancel both mirror notification paths (island + legacy)
            SuperIslandManager.cancelMirrorIsland(applicationContext)
            SuperIslandManager.cancelMirrorNotification(applicationContext)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        val rearManager = RearDisplayManager(applicationContext)

        if (rearManager.isRearDisplayPresent()) {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Return to phone"
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
        }
        tile.updateTile()
    }
}