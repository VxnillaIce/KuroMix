package com.kuromify.kuromix.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile to move the current task back from the rear display to the main display.
 */
class UnmirrorTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onClick() {
        super.onClick()
        
        serviceScope.launch {
            val rearManager = RearDisplayManager(applicationContext)
            val displayId = rearManager.primaryRearDisplayId() ?: 1
            
            // 1. Find the task that is actually ON the rear display
            val taskId = RootShell.getTaskIdOnDisplay(displayId)
            
            if (taskId != null) {
                // 2. Move it back to primary (0)
                RootShell.moveTaskToDisplay(taskId, 0)
            }

            // 3. ALWAYS Cleanup: Reset rear display to defaults and stop monitor
            RootShell.resetDisplayArea(displayId)
            RootShell.setDisplayDpi(displayId, null)
            
            val monitorIntent = Intent(applicationContext, MirrorMonitorService::class.java)
            applicationContext.stopService(monitorIntent)
            
            SuperIslandManager.cancelMirrorNotification(applicationContext)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        val rearManager = RearDisplayManager(applicationContext)
        
        if (rearManager.isRearDisplayPresent()) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Return to phone"
            }
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
        }
        tile.updateTile()
    }
}
