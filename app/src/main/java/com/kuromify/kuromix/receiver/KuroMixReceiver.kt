package com.kuromify.kuromix.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SuperIslandManager.ACTION_STOP) {
            CoroutineScope(Dispatchers.IO).launch {
                // Find the task on the rear display (ID: 1) and pull it back
                val taskId = RootShell.getTaskIdOnDisplay(1)
                if (taskId != null) {
                    RootShell.moveTaskToDisplay(taskId, 0)
                }
                
                // Cleanup: reset display and stop monitor
                RootShell.resetDisplayArea(1)
                RootShell.setDisplayDpi(1, null)
                
                val monitorIntent = Intent(context, MirrorMonitorService::class.java)
                context.stopService(monitorIntent)
                
                SuperIslandManager.cancelMirrorNotification(context)
            }
        }
    }
}
