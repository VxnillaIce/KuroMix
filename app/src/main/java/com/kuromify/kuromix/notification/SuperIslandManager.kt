package com.kuromify.kuromix.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixReceiver

/**
 * SuperIsland Manager for KuroMix.
 */

object SuperIslandManager {
    private const val TAG = "SuperIslandManager"
    private const val CHANNEL_ID = "kuromix_mirror_channel"
    private const val NOTIFICATION_ID = 202

    fun showMirrorNotification(context: Context, packageName: String) {
        Log.d(TAG, "[KUROMIX_LOG] showMirrorNotification for: $packageName")

        try {
            val pm = context.packageManager
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app label", e)
                packageName
            }

            val appIconCompat = try {
                val bitmap = pm.getApplicationIcon(packageName).toBitmap()
                IconCompat.createWithBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app icon, falling back to KuroMix logo", e)
                IconCompat.createWithResource(context, R.drawable.kuromify_logo)
            }

            createNotificationChannel(context)

            val stopIntent = Intent(context, KuroMixReceiver::class.java).apply {
                action = KuroMixReceiver.ACTION_STOP_MIRROR
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.kuromify_logo_w)
                .setLargeIcon(appIconCompat.toIcon(context))
                .setContentTitle("KuroMix")
                .setContentText("Active: $appLabel")
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setColorized(false)
                .setSilent(true)
                .addAction(0, "Stop Mirroring", stopPendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "[KUROMIX_LOG] Notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "[KUROMIX_LOG] Error sending notification", e)
        }
    }

    fun cancelMirrorNotification(context: Context) {
        Log.d(TAG, "[KUROMIX_LOG] cancelMirrorNotification called")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mirroring Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows real-time status of rear display mirroring"
            setSound(null, null)
            enableVibration(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}