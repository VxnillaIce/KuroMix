package com.kuromify.kuromix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log 
import androidx.core.app.NotificationCompat
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixReceiver
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

object SuperIslandManager {

    private const val TAG = "SuperIslandManager"

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private const val CHANNEL_ID =
        "kuromix_hyperisland"

    private const val MIRROR_NOTIFICATION_ID =
        202

    private const val TEST_NOTIFICATION_ID =
        203

    private const val DOWNLOAD_NOTIFICATION_ID =
        204

    private const val TEST_BUSINESS_ID =
        "kuromix_test"

    private const val DOWNLOAD_BUSINESS_ID =
        "download"

    // ============================================================
    // HYPEROS FOCUS
    // ============================================================

    private const val EXTRA_FOCUS_PARAM =
        "miui.focus.param"

    private const val EXTRA_FOCUS_ACTIONS =
        "miui.focus.actions"

    // ============================================================
    // PREFERENCES
    // ============================================================

    private const val PREFS_NAME =
        "kuromix_hyperisland"

    private const val PREF_HYPERISLAND_HOOK =
        "hyperisland_hook_enabled"

    @Suppress("DEPRECATION")
    private fun hyperIslandPreferences(
        context: Context
    ): SharedPreferences {
        return try {
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_WORLD_READABLE
            )
        } catch (_: SecurityException) {
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        }
    }

    // ============================================================
    // DOWNLOAD STATE
    // ============================================================

    @Volatile
    private var downloadTestRunning =
        false

    @Volatile
    private var downloadTestThread:
            Thread? = null

    // ============================================================
    // SUPPORT
    // ============================================================

    fun getFocusProtocolVersion(
        context: Context
    ): Int {

        return try {

            Settings.System.getInt(
                context.contentResolver,
                "miui_focus_protocol_version",
                0
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to read Focus protocol version",
                e
            )

            0
        }
    }

    fun isHyperIslandSupported(
        context: Context
    ): Boolean {

        return try {

            val supported =
                HyperIslandNotification.isSupported(
                    context
                )

            Log.d(
                TAG,
                "HyperIsland Toolkit supported = $supported"
            )

            if (supported) {
                true
            } else {
                getFocusProtocolVersion(context) > 0
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "HyperIsland support check failed",
                e
            )

            getFocusProtocolVersion(context) > 0
        }
    }

    fun hasFocusPermission(
        context: Context
    ): Boolean {

        return try {

            val uri =
                android.net.Uri.parse(
                    "content://miui.statusbar.notification.public"
                )

            val cursor =
                context.contentResolver.query(
                    uri,
                    null,
                    "package=?",
                    arrayOf(
                        context.packageName
                    ),
                    null
                )

            cursor?.use {

                if (it.moveToFirst()) {

                    val columnIndex =
                        it.getColumnIndex(
                            "canShowFocus"
                        )

                    if (columnIndex >= 0) {

                        return it.getInt(
                            columnIndex
                        ) != 0
                    }
                }
            }

            true

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Focus permission provider unavailable",
                e
            )

            true
        }
    }

    // ============================================================
    // HYPERISLAND STATE
    // ============================================================

    fun isHyperIslandHookEnabled(
        context: Context
    ): Boolean {

        return hyperIslandPreferences(
            context
        ).getBoolean(
            PREF_HYPERISLAND_HOOK,
            false
        )
    }

    fun setHyperIslandHookEnabled(
        context: Context,
        enabled: Boolean
    ) {

        hyperIslandPreferences(
            context
        )
            .edit()
            .putBoolean(
                PREF_HYPERISLAND_HOOK,
                enabled
            )
            .commit()

        Log.d(
            TAG,
            "HyperIsland enabled = $enabled"
        )

        if (!enabled) {

            stopDownloadTest(
                context
            )

            cancelTestIsland(
                context
            )
        }
    }

    // ============================================================
    // CHANNEL
    // ============================================================

    private fun createNotificationChannel(
        context: Context
    ) {

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        if (
            manager.getNotificationChannel(
                CHANNEL_ID
            ) != null
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "KuroMix HyperIsland",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "KuroMix HyperIsland notifications"

                setShowBadge(true)

                lockscreenVisibility =
                    Notification.VISIBILITY_PUBLIC
            }

        manager.createNotificationChannel(
            channel
        )

        Log.d(
            TAG,
            "Notification channel created"
        )
    }

    // ============================================================
    // MIRROR NOTIFICATION
    // ============================================================

    fun showMirrorNotification(
        context: Context,
        packageName: String
    ) {

        try {

            createNotificationChannel(
                context
            )

            val stopIntent =
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    action =
                        KuroMixReceiver.ACTION_STOP_MIRROR

                    putExtra(
                        "packageName",
                        packageName
                    )
                }

            val stopPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    MIRROR_NOTIFICATION_ID,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            val notification =
                NotificationCompat.Builder(
                    context,
                    CHANNEL_ID
                )
                    .setSmallIcon(
                        R.drawable.kuromify_logo_w
                    )
                    .setContentTitle(
                        "Rear Display Active"
                    )
                    .setContentText(
                        packageName
                    )
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(
                        NotificationCompat.CATEGORY_SERVICE
                    )
                    .addAction(
                        NotificationCompat.Action.Builder(
                            R.drawable.kuromify_logo_w,
                            "Stop",
                            stopPendingIntent
                        ).build()
                    )
                    .build()

            context
                .getSystemService(
                    NotificationManager::class.java
                )
                .notify(
                    MIRROR_NOTIFICATION_ID,
                    notification
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to show mirror notification",
                e
            )
        }
    }

    fun cancelMirrorNotification(
        context: Context
    ) {

        try {

            context
                .getSystemService(
                    NotificationManager::class.java
                )
                .cancel(
                    MIRROR_NOTIFICATION_ID
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel mirror notification",
                e
            )
        }
    }

    // ============================================================
    // TEST ISLAND
    // ============================================================

    fun showTestIsland(
        context: Context,
        title: String = "KuroMix",
        content: String = "HyperIsland Test",
        business: String = TEST_BUSINESS_ID
    ) {

        try {

            createNotificationChannel(
                context
            )

            if (
                !HyperIslandNotification.isSupported(
                    context
                )
            ) {

                Log.w(
                    TAG,
                    "HyperIsland Toolkit reports unsupported"
                )

                return
            }

            val pictureKey =
                "kuromix_$business"

            val picture =
                HyperPicture(
                    pictureKey,
                    context,
                    R.drawable.kuromify_logo_w
                )

            /*
             * IMPORTANT:
             *
             * This matches the official Toolkit demo.
             *
             * Do not declare this as:
             *
             *     HyperIslandNotification.Builder
             *
             * The Builder is used to create the
             * HyperIslandNotification object.
             */
            val builder =
                HyperIslandNotification.Builder(
                    context,
                    business,
                    title
                )
                    .setSmallWindowTarget(
                        "${context.packageName}.MainActivity"
                    )
                    .addPicture(
                        picture
                    )
                    .setChatInfo(
                        title,
                        content,
                        pictureKey
                    )
                    .setSmallIsland(
                        pictureKey
                    )
                    .setBigIslandInfo(
                        left =
                            ImageTextInfoLeft(
                                type = 1,
                                picInfo =
                                    PicInfo(
                                        type = 1,
                                        pic = pictureKey
                                    ),
                                textInfo =
                                    TextInfo(
                                        title = title,
                                        content = content
                                    )
                            )
                    )
                    .setEnableFloat(
                        false
                    )
                    .setShowNotification(
                        true
                    )

            notifyHyperIsland(
                context = context,
                title = title,
                notificationId =
                    TEST_NOTIFICATION_ID,
                builder = builder
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to show Test HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // HYPERISLAND NOTIFY
    // ============================================================

    /*
     * This is the important part taken from the official
     * HyperIsland-ToolKit demo.
     *
     * The builder itself generates:
     *
     *     buildResourceBundle()
     *     buildJsonParam()
     *
     * We do NOT call those methods on a Builder class.
     *
     * The object returned by:
     *
     *     HyperIslandNotification.Builder(...)
     *
     * is passed here as HyperIslandNotification.
     */

    private fun notifyHyperIsland(
        context: Context,
        title: String,
        notificationId: Int,
        builder: HyperIslandNotification
    ) {

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.kuromify_logo_w
                )
                .setContentTitle(
                    title
                )
                .setContentIntent(
                    createOpenAppPendingIntent(
                        context
                    )
                )
                .setOnlyAlertOnce(true)
                .addExtras(
                    builder.buildResourceBundle()
                )
                .build()

        notification.extras.putString(
            EXTRA_FOCUS_PARAM,
            builder.buildJsonParam()
        )

        context
            .getSystemService(
                NotificationManager::class.java
            )
            .notify(
                notificationId,
                notification
            )

        Log.d(
            TAG,
            "HyperIsland notification posted: $title"
        )
    }

    // ============================================================
    // OPEN APP
    // ============================================================

    private fun createOpenAppPendingIntent(
        context: Context
    ): PendingIntent {

        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(
                    context.packageName
                )
                ?: Intent(
                    Intent.ACTION_MAIN
                ).apply {

                    addCategory(
                        Intent.CATEGORY_LAUNCHER
                    )

                    setPackage(
                        context.packageName
                    )
                }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        return PendingIntent.getActivity(
            context,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ============================================================
    // CANCEL TEST
    // ============================================================

    fun cancelTestIsland(
        context: Context
    ) {

        try {

            val manager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            manager.cancel(
                TEST_NOTIFICATION_ID
            )

            manager.cancel(
                DOWNLOAD_NOTIFICATION_ID
            )

            Log.d(
                TAG,
                "Test HyperIsland cancelled"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel Test HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // CHARGING
    // ============================================================

    fun showChargingTest(
        context: Context,
        battery: Int = 82,
        power: Int = 67
    ) {

        showTestIsland(
            context = context,
            title = "Charging",
            content = "$battery% • ${power}W"
        )
    }

    // ============================================================
    // MEDIA
    // ============================================================

    fun showMediaTest(
        context: Context,
        artist: String = "KuroMix",
        title: String = "HyperIsland Demo"
    ) {

        showTestIsland(
            context = context,
            title = artist,
            content = title
        )
    }

    // ============================================================
    // TIMER
    // ============================================================

    fun showTimerTest(
        context: Context,
        remaining: String = "05:00"
    ) {

        showTestIsland(
            context = context,
            title = "Timer",
            content = remaining
        )
    }

    // ============================================================
    // NETWORK
    // ============================================================

    fun showNetworkTest(
        context: Context,
        network: String = "5G",
        speed: String = "128 Mbps"
    ) {

        showTestIsland(
            context = context,
            title = network,
            content = speed
        )
    }

    // ============================================================
    // GAME
    // ============================================================

    fun showGameTest(
        context: Context,
        fps: Int = 120,
        temperature: Int = 34
    ) {

        showTestIsland(
            context = context,
            title = "Gaming",
            content = "$fps FPS • $temperature°C"
        )
    }

    // ============================================================
    // DOWNLOAD
    // ============================================================

    fun showDownloadTest(
        context: Context,
        progress: Int = 0,
        isFirstUpdate: Boolean = false
    ) {

        try {

            createNotificationChannel(
                context
            )

            if (
                !HyperIslandNotification.isSupported(
                    context
                )
            ) {

                Log.w(
                    TAG,
                    "Download HyperIsland unsupported"
                )

                return
            }

            val safeProgress =
                progress.coerceIn(
                    0,
                    100
                )

            val pictureKey =
                "kuromix_download"

            val picture =
                HyperPicture(
                    pictureKey,
                    context,
                    R.drawable.kuromify_logo_w
                )

            /*
             * Use the same ProgressTextInfo structure
             * demonstrated by the official Toolkit.
             */
            val progressInfo =
                io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo(
                    io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo(
                        progress =
                            safeProgress,
                        colorReach =
                            "#007AFF",
                        isCCW =
                            true
                    )
                )

            val builder =
                HyperIslandNotification.Builder(
                    context,
                    DOWNLOAD_BUSINESS_ID,
                    "Download"
                )
                    .setSmallWindowTarget(
                        "${context.packageName}.MainActivity"
                    )
                    .addPicture(
                        picture
                    )
                    .setChatInfo(
                        title = "KuroMix.apk",
                        content =
                            "$safeProgress% complete",
                        pictureKey =
                            pictureKey
                    )
                    .setProgressBar(
                        progress =
                            safeProgress,
                        color =
                            "#007AFF"
                    )
                    .setSmallIslandCircularProgress(
                        pictureKey =
                            pictureKey,
                        progress =
                            safeProgress,
                        color =
                            "#007AFF"
                    )
                    .setBigIslandInfo(
                        left =
                            ImageTextInfoLeft(
                                type = 1,
                                picInfo =
                                    PicInfo(
                                        type = 1,
                                        pic = pictureKey
                                    ),
                                textInfo =
                                    TextInfo(
                                        title =
                                            "KuroMix.apk",
                                        content =
                                            "$safeProgress%"
                                    )
                            ),
                        progressText =
                            progressInfo
                    )
                    .setEnableFloat(
                        isFirstUpdate
                    )
                    .setShowNotification(
                        true
                    )

            if (
                safeProgress >= 100
            ) {

                builder.setTimeout(
                    5000L
                )
            }

            notifyHyperIsland(
                context = context,
                title =
                    "KuroMix.apk",
                notificationId =
                    DOWNLOAD_NOTIFICATION_ID,
                builder =
                    builder
            )

            Log.d(
                TAG,
                "Download HyperIsland posted: $safeProgress%"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to show Download HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // DOWNLOAD STATE
    // ============================================================

    fun isDownloadTestRunning(): Boolean {
        return downloadTestRunning
    }

    fun startDownloadTest(
        context: Context
    ) {

        if (
            downloadTestRunning
        ) {
            return
        }

        if (
            !isHyperIslandHookEnabled(
                context
            )
        ) {

            Log.w(
                TAG,
                "Download ignored: HyperIsland disabled"
            )

            return
        }

        if (
            !HyperIslandNotification.isSupported(
                context
            )
        ) {

            Log.w(
                TAG,
                "Download ignored: HyperIsland unsupported"
            )

            return
        }

        downloadTestRunning =
            true

        downloadTestThread =
            Thread {

                try {

                    for (
                    progress in 0..100
                    ) {

                        if (
                            !downloadTestRunning
                        ) {
                            break
                        }

                        showDownloadTest(
                            context,
                            progress,
                            progress == 0
                        )

                        if (
                            progress < 100
                        ) {

                            /*
                             * Avoid flooding HyperOS SystemUI.
                             */
                            Thread.sleep(
                                500L
                            )
                        }
                    }

                } catch (
                    e: InterruptedException
                ) {

                    Log.d(
                        TAG,
                        "Download test interrupted"
                    )

                    Thread.currentThread()
                        .interrupt()

                } catch (
                    e: Exception
                ) {

                    Log.e(
                        TAG,
                        "Download test failed",
                        e
                    )

                } finally {

                    downloadTestRunning =
                        false

                    downloadTestThread =
                        null
                }
            }

        downloadTestThread?.start()
    }

    fun stopDownloadTest(
        context: Context
    ) {

        downloadTestRunning =
            false

        downloadTestThread?.interrupt()

        downloadTestThread =
            null

        try {

            context
                .getSystemService(
                    NotificationManager::class.java
                )
                .cancel(
                    DOWNLOAD_NOTIFICATION_ID
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel download notification",
                e
            )
        }

        Log.d(
            TAG,
            "Download test stopped"
        )
    }

    // ============================================================
    // FOCUS ACTION
    // ============================================================

    fun addFocusAction(
        context: Context,
        builder: NotificationCompat.Builder,
        actionKey: String,
        title: String,
        intent: Intent,
        iconRes: Int =
            R.drawable.kuromify_logo_w
    ) {

        try {

            val requestCode =
                actionKey.hashCode() and
                        0x7fffffff

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            builder.addAction(
                NotificationCompat.Action.Builder(
                    iconRes,
                    title,
                    pendingIntent
                ).build()
            )

            val actionBundle =
                Bundle().apply {

                    putString(
                        "key",
                        actionKey
                    )

                    putString(
                        "title",
                        title
                    )
                }

            val actionsBundle =
                Bundle().apply {

                    putBundle(
                        actionKey,
                        actionBundle
                    )
                }

            builder.addExtras(
                Bundle().apply {

                    putBundle(
                        EXTRA_FOCUS_ACTIONS,
                        actionsBundle
                    )
                }
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to add Focus action: $actionKey",
                e
            )
        }
    }
}
