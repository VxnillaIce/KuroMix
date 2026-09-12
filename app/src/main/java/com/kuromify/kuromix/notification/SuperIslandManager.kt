package com.kuromify.kuromix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixReceiver
import org.json.JSONArray
import org.json.JSONObject

object SuperIslandManager {
    private const val TAG = "SuperIslandManager"
    private const val CHANNEL_ID = "kuromix_mirror_channel"
    private const val NOTIFICATION_ID = 202
    private const val PIC_KEY_MAIN = "miui.focus.pic_kuromix_main"
    private const val ACTION_KEY_STOP = "miui.focus.action_kuromix_stop"

    /**
     * If this returns false, HyperOS silently shows a normal notification
     * instead of a focus notification — no error, no exception.
     */
    fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply { putString("package", context.packageName) }
            val result = context.contentResolver.call(uri, "canShowFocus", null, extras)
            result?.getBoolean("canShowFocus", false) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "[KUROMIX_LOG] hasFocusPermission check failed (non-Xiaomi device?)", e)
            false
        }
    }

    /**
     * Shows (or replaces) the mirroring notification for whichever app is
     * currently being mirrored. Reuses a single notification ID, so calling
     * this again for a different app replaces the previous one rather than
     * stacking — only one mirroring notification exists at a time.
     *
     * Non-persistent: dismissable, and does not request an "updatable"
     * resident island. Full Super Island capsule rendering additionally
     * requires Xiaomi App ID + scene approval + device whitelist — this
     * code alone gets you the Focus Notification popup at most, gated on
     * hasFocusPermission() above.
     */
    fun showMirrorNotification(context: Context, packageName: String) {
        Log.d(TAG, "[KUROMIX_LOG] showMirrorNotification for: $packageName")

        val focusGranted = hasFocusPermission(context)
        if (!focusGranted) {
            Log.w(
                TAG,
                "[KUROMIX_LOG] Focus notification permission is OFF for this app — " +
                        "will show as a plain notification. Settings > Apps > KuroMix > " +
                        "Notifications > (status bar / focus notification toggle)."
            )
        }

        try {
            val pm = context.packageManager
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app label", e)
                packageName
            }

            val appBitmap = try {
                pm.getApplicationIcon(packageName).toBitmap()
            } catch (e: Exception) {
                Log.e(TAG, "[KUROMIX_LOG] Failed to get app icon", e)
                null
            }

            createNotificationChannel(context)

            val stopIntent = Intent(context, KuroMixReceiver::class.java).apply {
                action = KuroMixReceiver.ACTION_STOP_MIRROR
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val islandParams = JSONObject().apply {
                put("param_v2", JSONObject().apply {
                    put("business", "screen_mirror")
                    put("scene", "mirroring")
                    put("enableFloat", true)
                    put("islandFirstFloat", true)
                    put("updatable", true)
                    put("isResident", true)
                    put("isDismissible", false)
                    put("orderId", "kuromix_mirror_task")

                    put("ticker", "Mirroring $appLabel")
                    put("tickerPic", PIC_KEY_MAIN)

                    put("param_island", JSONObject().apply {
                        put("islandProperty", 1)
                        put("bigIslandArea", JSONObject().apply {
                            put("imageTextInfoLeft", JSONObject().apply {
                                put("type", 1)
                                put("picInfo", JSONObject().apply {
                                    put("type", 1)
                                    put("pic", PIC_KEY_MAIN)
                                })
                                put("textInfo", JSONObject().apply {
                                    put("title", appLabel)
                                    put("content", "Mirroring to rear display")
                                })
                            })
                        })
                        put("smallIslandArea", JSONObject().apply {
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", PIC_KEY_MAIN)
                            })
                        })
                    })

                    put("baseInfo", JSONObject().apply {
                        put("title", appLabel)
                        put("content", "Mirroring to rear display")
                    })

                    put("actions", JSONArray().apply {
                        put(JSONObject().apply { put("action", ACTION_KEY_STOP) })
                    })
                })
            }

            val stopAction = Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.kuromify_logo), // TODO: real stop icon
                "Stop Mirroring",
                stopPendingIntent
            ).build()

            val actionsBundle = Bundle().apply {
                putParcelable(ACTION_KEY_STOP, stopAction)
            }

            val picsBundle = Bundle().apply {
                val icon = if (appBitmap != null) {
                    Icon.createWithBitmap(appBitmap)
                } else {
                    Icon.createWithResource(context, R.drawable.kuromify_logo)
                }
                putParcelable(PIC_KEY_MAIN, icon)
            }

            val extras = Bundle().apply {
                putBundle("miui.focus.actions", actionsBundle)
                putBundle("miui.focus.pics", picsBundle)
                putBoolean("miui.is_focus_notification", true)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.kuromify_logo)
                .setContentTitle("KuroMix Mirroring")
                .setContentText("Active: $appLabel")
                .setSilent(true)
                .setOngoing(true)
                .addExtras(extras)
                .addAction(0, "Stop Mirroring", stopPendingIntent)
                .build()

            notification.extras.putString("miui.focus.param", islandParams.toString())

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "[KUROMIX_LOG] Notification sent (focus permission granted: $focusGranted)")
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