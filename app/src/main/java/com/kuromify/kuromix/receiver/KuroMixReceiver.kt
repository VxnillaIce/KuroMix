package com.kuromify.kuromix.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.service.MirrorMonitorService
import com.kuromify.kuromix.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles broadcasts for KuroMix, specifically the "Stop Mirroring" action from notifications.
 */
class KuroMixReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_MIRROR = "com.kuromify.kuromix.ACTION_STOP_MIRROR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_MIRROR) {
            CoroutineScope(Dispatchers.IO).launch {
                val rearManager = RearDisplayManager(context.applicationContext)
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

                val monitorIntent = Intent(context, MirrorMonitorService::class.java)
                context.stopService(monitorIntent)

                SuperIslandManager.cancelMirrorNotification(context)
            }
        }
    }
}
