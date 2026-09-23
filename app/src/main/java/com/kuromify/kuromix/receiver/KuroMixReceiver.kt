package com.kuromify.kuromix.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuromify.kuromix.manager.RearDisplayManager
import com.kuromify.kuromix.notification.SuperIslandManager
import com.kuromify.kuromix.root.RootShell
import com.kuromify.kuromix.service.MirrorMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles KuroMix broadcast actions.
 *
 * Currently the only action is [ACTION_STOP_MIRROR], dispatched by the
 * mirror island's Stop button and by any other surface that wants to tear
 * down an active mirror session.
 *
 * Media control actions were removed along with the Media Player island.
 */
class KuroMixReceiver : BroadcastReceiver() {

    companion object {

        private const val TAG =
            "KuroMixReceiver"

        const val ACTION_STOP_MIRROR =
            "com.kuromify.kuromix.ACTION_STOP_MIRROR"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val action =
            intent.action
                ?: return

        Log.d(
            TAG,
            "Received broadcast: $action"
        )

        when (action) {

            ACTION_STOP_MIRROR -> {

                handleStopMirror(
                    context.applicationContext
                )

                return
            }

            else -> {

                Log.d(
                    TAG,
                    "Ignoring unknown broadcast: $action"
                )

                return
            }
        }
    }

    // ============================================================
    // STOP MIRROR
    // ============================================================

    private fun handleStopMirror(
        context: Context
    ) {

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            try {

                val rearManager =
                    RearDisplayManager(
                        context
                    )

                val displayId =
                    rearManager
                        .primaryRearDisplayId()
                        ?: 1

                Log.d(
                    TAG,
                    "Stopping mirror on display $displayId"
                )

                /*
                 * 1. Find the task that is actually on the
                 *    rear display.
                 */
                val taskId =
                    RootShell
                        .getTaskIdOnDisplay(
                            displayId
                        )

                if (taskId != null) {

                    Log.d(
                        TAG,
                        "Moving task $taskId back to display 0"
                    )

                    /*
                     * 2. Move it back to the primary display.
                     */
                    RootShell
                        .moveTaskToDisplay(
                            taskId,
                            0
                        )
                }

                /*
                 * 3. Always reset the rear display.
                 */
                RootShell
                    .resetDisplayArea(
                        displayId
                    )

                RootShell
                    .setDisplayDpi(
                        displayId,
                        null
                    )

                /*
                 * 4. Stop the mirror monitor.
                 */
                val monitorIntent =
                    Intent(
                        context,
                        MirrorMonitorService::class.java
                    )

                context.stopService(
                    monitorIntent
                )

                /*
                 * 5. Remove both mirror notification surfaces:
                 *    the HyperIsland card and the legacy plain
                 *    notification. Only one of them will have
                 *    been posted, but cancelling both is harmless.
                 */
                SuperIslandManager
                    .cancelMirrorIsland(
                        context
                    )

                SuperIslandManager
                    .cancelMirrorNotification(
                        context
                    )

                /*
                 * 6. Signal the LSPosed hook to stop keeping
                 *    the rear display awake.
                 */
                RootShell
                    .setKeepAwakeProp(
                        false
                    )

                Log.d(
                    TAG,
                    "Mirror cleanup completed"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to stop mirror",
                    e
                )
            }
        }
    }
}