package com.kuromify.kuromix.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuromify.kuromix.notification.SuperIslandManager

class KuroMixLiveActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KuroMixLiveActionReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val appContext = context.applicationContext

        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            SuperIslandManager.ACTION_TIMER_PAUSE ->
                SuperIslandManager.pauseTimer(appContext)

            SuperIslandManager.ACTION_TIMER_RESUME ->
                SuperIslandManager.resumeTimer(appContext)

            SuperIslandManager.ACTION_TIMER_RESET ->
                SuperIslandManager.resetTimer(appContext)

            SuperIslandManager.ACTION_DOWNLOAD_PAUSE ->
                SuperIslandManager.pauseDownloadTest(appContext)

            SuperIslandManager.ACTION_DOWNLOAD_RESUME ->
                SuperIslandManager.resumeDownloadTest(appContext)

            SuperIslandManager.ACTION_DOWNLOAD_RESET ->
                SuperIslandManager.resetDownloadTest(appContext)

            else ->
                Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }
}
