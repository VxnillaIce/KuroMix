package com.kuromify.kuromix.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixReceiver
import org.json.JSONObject

/**
 * Manages Xiaomi Super Island (Focus Notifications) for KuroMix.
 */
object SuperIslandManager {
    private const val TAG = "SuperIslandManager"
    private const val CHANNEL_ID = "kuromix_mirror_channel"
    private const val NOTIFICATION_ID = 202
    const val ACTION_STOP = "com.kuromify.kuromix.ACTION_STOP_MIRROR"

    fun showMirrorNotification(context: Context, packageName: String) {
        Log.d(TAG, "[KUROMIX_LOG] showMirrorNotification called for: $packageName")
        try {
            val pm = context.packageManager
            val appLabel = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app label for $packageName", e)
                packageName
            }
            
            val appIcon = try {
                pm.getApplicationIcon(packageName).toBitmap()
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app icon for $packageName", e)
                null
            }

            createNotificationChannel(context)

            val stopIntent = Intent(context, KuroMixReceiver::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Construct Xiaomi Focus Notification JSON (Countdown/Status Template)
            val islandJson = JSONObject().apply {
                val paramV2 = JSONObject().apply {
                    put("protocol", 2)
                    put("business", "countdown") // Often allows manual state management
                    put("orderId", "mirror_$packageName")
                    put("enableFloat", true)
                    put("islandFirstFloat", true)
                    put("updatable", true)
                    put("ticker", "Mirroring $appLabel")
                    
                    val paramIsland = JSONObject().apply {
                        put("islandProperty", 1)
                        // Summary shown in the pill
                        val summary = JSONObject().apply {
                            put("title", appLabel)
                            put("content", "Mirroring")
                            put("icon_type", 1)
                            put("left_pic", "miui.focus.pic_small")
                        }
                        put("summary", summary)
                        
                        // Expanded view info (Spotlight)
                        val bigIslandArea = JSONObject().apply {
                            val baseInfo = JSONObject().apply {
                                put("title", appLabel)
                                put("content", "Mirroring to Rear Display")
                            }
                            put("baseInfo", baseInfo)
                        }
                        put("bigIslandArea", bigIslandArea)
                    }
                    put("param_island", paramIsland)
                }
                put("param_v2", paramV2)
            }

            Log.d(TAG, "[KUROMIX_LOG] islandJson: $islandJson")

            val extras = Bundle().apply {
                putString("miui.focus.param", islandJson.toString())
                
                // Add icons to multiple places for maximum compatibility
                val pics = Bundle()
                appIcon?.let {
                    val icon = Icon.createWithBitmap(it)
                    pics.putParcelable("miui.focus.pic_small", icon)
                    pics.putParcelable("miui.focus.pic_ticker", icon)
                    pics.putParcelable("miui.focus.pic_aod", icon)
                    
                    // Duplicate directly in extras (some system versions look here)
                    putParcelable("miui.focus.pic_small", icon)
                    putParcelable("miui.focus.pic_ticker", icon)
                }
                putBundle("miui.focus.pics", pics)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.kuromify_logo)
                .setContentTitle("KuroMix Mirroring")
                .setContentText("Active: $appLabel")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addExtras(extras)
                .addAction(0, "Stop Mirroring", stopPendingIntent)
                .setSilent(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            Log.d(TAG, "[KUROMIX_LOG] Sending notification...")
            manager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "[KUROMIX_LOG] Notification sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "[KUROMIX_LOG] Error sending notification", e)
        }
    }

    fun cancelMirrorNotification(context: Context) {
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
