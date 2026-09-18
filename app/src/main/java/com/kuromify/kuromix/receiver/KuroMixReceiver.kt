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
 * Includes:
 * - Rear display / mirror stop
 * - HyperIsland media controls
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

            // ====================================================
            // MEDIA
            // ====================================================

            SuperIslandManager.ACTION_MEDIA_TOGGLE,
            SuperIslandManager.ACTION_MEDIA_PLAY,
            SuperIslandManager.ACTION_MEDIA_PAUSE,
            SuperIslandManager.ACTION_MEDIA_NEXT,
            SuperIslandManager.ACTION_MEDIA_PREVIOUS -> {

                /*
                 * IMPORTANT:
                 *
                 * The media PendingIntent created by
                 * SuperIslandManager contains the package name
                 * of the media app currently displayed.
                 *
                 * We pass that package through to the media
                 * manager instead of ignoring it.
                 */
                val packageName =
                    intent.getStringExtra(
                        "packageName"
                    )

                Log.d(
                    TAG,
                    "Media action: $action, " +
                            "package=$packageName"
                )

                /*
                 * Do this synchronously.
                 *
                 * BroadcastReceiver.onReceive() is already running
                 * on the main thread and performMediaAction()
                 * only dispatches the MediaController command.
                 */
                SuperIslandManager
                    .performMediaAction(
                        context.applicationContext,
                        action,
                        packageName
                    )

                return
            }

            // ====================================================
            // STOP MIRROR
            // ====================================================

            ACTION_STOP_MIRROR -> {

                handleStopMirror(
                    context.applicationContext
                )

                return
            }

            // ====================================================
            // UNKNOWN
            // ====================================================

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
                 * 5. Remove the mirror notification.
                 */
                SuperIslandManager
                    .cancelMirrorNotification(
                        context
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