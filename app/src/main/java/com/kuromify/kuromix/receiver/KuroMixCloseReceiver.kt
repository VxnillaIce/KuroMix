package com.kuromify.kuromix.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuromify.kuromix.notification.SuperIslandManager

class KuroMixCloseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KuroMixCloseReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action !=
            SuperIslandManager.ACTION_CLOSE_LIVE
        ) {
            return
        }

        Log.d(
            TAG,
            "Close action received"
        )

        val appContext = context.applicationContext

        SuperIslandManager.stopLiveUpdates(
            appContext
        )

        SuperIslandManager.stopDownloadTest(
            appContext
        )
    }
}
