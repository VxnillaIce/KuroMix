package com.kuromify.kuromix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixCloseReceiver
import com.kuromify.kuromix.receiver.KuroMixReceiver
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object SuperIslandManager {

    private const val TAG = "SuperIslandManager"

    private const val CHANNEL_ID =
        "kuromix_hyperisland"

    private const val MIRROR_NOTIFICATION_ID = 202
    private const val TEST_NOTIFICATION_ID = 203
    private const val DOWNLOAD_NOTIFICATION_ID = 204
    private const val LIVE_NOTIFICATION_ID = 205

    private const val TEST_BUSINESS_ID =
        "kuromix_live"

    private const val DOWNLOAD_BUSINESS_ID =
        "download"

    private const val EXTRA_FOCUS_PARAM =
        "miui.focus.param"

    private const val EXTRA_FOCUS_ACTIONS =
        "miui.focus.actions"

    private const val PREFS_NAME =
        "kuromix_hyperisland"

    private const val PREF_HYPERISLAND_HOOK =
        "hyperisland_hook_enabled"

    /*
     * ------------------------------------------------------------
     * MEDIA ACTIONS
     * ------------------------------------------------------------
     *
     * IMPORTANT:
     *
     * MEDIA_TOGGLE is the only Play/Pause action exposed to
     * HyperIsland.
     *
     * We intentionally do not create separate HyperIsland actions
     * called "play" and "pause".
     *
     * HyperOS may cache a Focus action/PendingIntent by its key.
     * If the key changes from play -> pause, the visual state may
     * change while the old PendingIntent remains cached.
     *
     * Therefore:
     *
     *     media_toggle
     *             |
     *             v
     *     check current PlaybackState
     *             |
     *       +-----+-----+
     *       |           |
     *    PLAYING     PAUSED
     *       |           |
     *     PAUSE        PLAY
     *
     * The decision is made when the user presses the button.
     */

    const val ACTION_MEDIA_PLAY =
        "com.kuromify.kuromix.MEDIA_PLAY"

    const val ACTION_MEDIA_PAUSE =
        "com.kuromify.kuromix.MEDIA_PAUSE"

    const val ACTION_MEDIA_TOGGLE =
        "com.kuromify.kuromix.MEDIA_TOGGLE"

    const val ACTION_MEDIA_NEXT =
        "com.kuromify.kuromix.MEDIA_NEXT"

    const val ACTION_MEDIA_PREVIOUS =
        "com.kuromify.kuromix.MEDIA_PREVIOUS"

    const val ACTION_CLOSE_LIVE =
        "com.kuromify.kuromix.CLOSE_LIVE"

    const val ACTION_TIMER_PAUSE =
        "com.kuromify.kuromix.TIMER_PAUSE"

    const val ACTION_TIMER_RESUME =
        "com.kuromify.kuromix.TIMER_RESUME"

    const val ACTION_TIMER_RESET =
        "com.kuromify.kuromix.TIMER_RESET"

    const val ACTION_DOWNLOAD_PAUSE =
        "com.kuromify.kuromix.DOWNLOAD_PAUSE"

    const val ACTION_DOWNLOAD_RESUME =
        "com.kuromify.kuromix.DOWNLOAD_RESUME"

    const val ACTION_DOWNLOAD_RESET =
        "com.kuromify.kuromix.DOWNLOAD_RESET"

    /*
     * Stable request code for the media toggle.
     *
     * DO NOT change this to "play" / "pause".
     */
    private const val MEDIA_TOGGLE_REQUEST_CODE = 7100

    /*
     * Base request code for the Close PendingIntent.
     *
     * Each notification ID XORs into this to produce a unique
     * request code, so HyperOS cannot reuse the live island's
     * cached Close PendingIntent for the download island.
     */
    private const val CLOSE_REQUEST_CODE_BASE = 0x4B55524F

    /*
     * Last package actually displayed on HyperIsland.
     *
     * This prevents a second media notification/session from
     * stealing the Play/Pause operation.
     */
    @Volatile
    private var displayedMediaPackage: String? = null

    /*
     * Small click debounce.
     *
     * Some HyperOS builds can deliver a Focus action more than once
     * during a transition.
     */
    @Volatile
    private var lastMediaToggleMs = 0L

    private const val MEDIA_TOGGLE_DEBOUNCE_MS = 350L

    // ============================================================
    // PREFERENCES
    // ============================================================

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
    // LIVE ENGINE
    // ============================================================

    private val mainHandler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    @Volatile
    private var liveRunning = false

    private var currentLiveMode =
        LiveMode.CLOCK

    private var batteryReceiver:
            BroadcastReceiver? = null

    private var clockRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var networkRunnable: Runnable? = null
    private var mediaRunnable: Runnable? = null
    private var temperatureRunnable: Runnable? = null

    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastNetworkSampleMs = 0L

    /*
     * Thermal state
     */
    private var lastTemperatureC = Float.NaN
    private var lastThermalStatus =
        PowerManager.THERMAL_STATUS_NONE

    private enum class LiveMode {
        CHARGING,
        MEDIA,
        CLOCK,
        TIMER,
        NETWORK,
        TEMP
    }

    // ============================================================
    // FOCUS ACTION MODEL
    // ============================================================

    private data class FocusAction(
        val key: String,
        val title: String,
        val pendingIntent: PendingIntent,
        val iconRes: Int
    )

    private var pendingMediaActions =
        mutableListOf<FocusAction>()

    // ============================================================
    // ICONS
    // ============================================================

    private fun mediaIcon(
        key: String
    ): Int {
        return when (key) {

            "previous" ->
                android.R.drawable.ic_media_previous

            "pause" ->
                android.R.drawable.ic_media_pause

            "play" ->
                android.R.drawable.ic_media_play

            "next" ->
                android.R.drawable.ic_media_next

            else ->
                android.R.drawable.ic_menu_close_clear_cancel
        }
    }

    private fun closeIcon(): Int {
        return android.R.drawable.ic_menu_close_clear_cancel
    }

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

    fun refreshMediaNow(
        context: Context
    ) {
        if (
            !liveRunning ||
            currentLiveMode != LiveMode.MEDIA
        ) {
            return
        }

        updateMediaIsland(context)
    }

    fun isHyperIslandSupported(
        context: Context
    ): Boolean {
        return try {

            val supported =
                HyperIslandNotification
                    .isSupported(context)

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
                    arrayOf(context.packageName),
                    null
                )

            cursor?.use {

                if (it.moveToFirst()) {

                    val index =
                        it.getColumnIndex(
                            "canShowFocus"
                        )

                    if (index >= 0) {
                        return it.getInt(index) != 0
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
    // STATE
    // ============================================================

    fun isHyperIslandHookEnabled(
        context: Context
    ): Boolean {
        return hyperIslandPreferences(context)
            .getBoolean(
                PREF_HYPERISLAND_HOOK,
                false
            )
    }

    fun setHyperIslandHookEnabled(
        context: Context,
        enabled: Boolean
    ) {

        hyperIslandPreferences(context)
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

            stopLiveUpdates(context)
            stopDownloadTest(context)
            cancelTestIsland(context)

            displayedMediaPackage = null
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
                "KuroMix Live Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "KuroMix real-time HyperIsland updates"

                setShowBadge(true)

                lockscreenVisibility =
                    Notification.VISIBILITY_PUBLIC
            }

        manager.createNotificationChannel(channel)
    }

    // ============================================================
    // LIVE CONTROL
    // ============================================================

    fun isLiveUpdatesRunning(): Boolean {
        return liveRunning
    }

    fun startLiveUpdates(
        context: Context,
        mode: String = "clock"
    ) {

        if (
            !isHyperIslandHookEnabled(context)
        ) {

            Log.w(
                TAG,
                "Live updates ignored: HyperIsland disabled"
            )

            return
        }

        if (
            !isHyperIslandSupported(context)
        ) {

            Log.w(
                TAG,
                "Live updates ignored: unsupported"
            )

            return
        }

        stopLiveUpdates(context)

        liveRunning = true

        currentLiveMode =
            parseMode(mode)

        createNotificationChannel(context)

        startCurrentLiveMode(context)
    }

    fun switchLiveMode(
        context: Context,
        mode: String
    ) {

        if (!liveRunning) {

            startLiveUpdates(
                context,
                mode
            )

            return
        }

        stopLiveRunnablesOnly(context)

        currentLiveMode =
            parseMode(mode)

        if (currentLiveMode != LiveMode.MEDIA) {
            displayedMediaPackage = null
        }

        startCurrentLiveMode(context)
    }

    private fun startCurrentLiveMode(
        context: Context
    ) {

        when (currentLiveMode) {

            LiveMode.CHARGING ->
                startChargingUpdates(context)

            LiveMode.MEDIA ->
                startMediaUpdates(context)

            LiveMode.CLOCK ->
                startClockUpdates(context)

            LiveMode.TIMER ->
                startTimerUpdates(context)

            LiveMode.NETWORK ->
                startNetworkUpdates(context)

            LiveMode.TEMP ->
                startTemperatureUpdates(context)
        }
    }

    private fun parseMode(
        mode: String
    ): LiveMode {

        return when (
            mode.lowercase(Locale.US)
        ) {

            "charging",
            "battery" ->
                LiveMode.CHARGING

            "media",
            "music",
            "player" ->
                LiveMode.MEDIA

            "clock",
            "time" ->
                LiveMode.CLOCK

            "timer",
            "countdown",
            "5min",
            "5minute" ->
                LiveMode.TIMER

            "network",
            "net",
            "speed" ->
                LiveMode.NETWORK

            /*
             * Temperature mode.
             *
             * "fps", "game" and "gaming" are kept as aliases so
             * existing callers that still pass the old strings
             * keep working.
             */
            "temp",
            "temperature",
            "thermal",
            "heat",
            "fps",
            "game",
            "gaming" ->
                LiveMode.TEMP

            else ->
                LiveMode.CLOCK
        }
    }

    fun stopLiveUpdates(
        context: Context
    ) {

        liveRunning = false

        stopLiveRunnablesOnly(context)

        displayedMediaPackage = null

        try {

            context.getSystemService(
                NotificationManager::class.java
            ).cancel(
                LIVE_NOTIFICATION_ID
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel live notification",
                e
            )
        }
    }

    private fun stopLiveRunnablesOnly(
        context: Context
    ) {

        clockRunnable?.let(
            mainHandler::removeCallbacks
        )

        timerRunnable?.let(
            mainHandler::removeCallbacks
        )

        networkRunnable?.let(
            mainHandler::removeCallbacks
        )

        mediaRunnable?.let(
            mainHandler::removeCallbacks
        )

        temperatureRunnable?.let(
            mainHandler::removeCallbacks
        )

        clockRunnable = null
        timerRunnable = null
        networkRunnable = null
        mediaRunnable = null
        temperatureRunnable = null

        batteryReceiver?.let {

            try {

                context.applicationContext
                    .unregisterReceiver(it)

            } catch (_: Exception) {
            }
        }

        batteryReceiver = null
    }

    // ============================================================
    // CHARGING
    // ============================================================

    fun startChargingUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        val filter =
            android.content.IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )

        val receiver =
            object : BroadcastReceiver() {

                override fun onReceive(
                    receiverContext: Context,
                    intent: Intent
                ) {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.CHARGING
                    ) {
                        return
                    }

                    updateChargingIsland(
                        receiverContext,
                        intent
                    )
                }
            }

        batteryReceiver = receiver

        context.applicationContext
            .registerReceiver(
                receiver,
                filter
            )
    }

    private fun updateChargingIsland(
        context: Context,
        intent: Intent
    ) {

        val status =
            intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )

        val plugged =
            intent.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0
            )

        val level =
            intent.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                0
            )

        val scale =
            intent.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                100
            )

        val batteryPercent =
            if (scale > 0) {

                (
                        level * 100f / scale
                        ).toInt().coerceIn(
                        0,
                        100
                    )

            } else {
                0
            }

        val charging =
            status ==
                    BatteryManager.BATTERY_STATUS_CHARGING ||
                    status ==
                    BatteryManager.BATTERY_STATUS_FULL

        val voltageMv =
            intent.getIntExtra(
                BatteryManager.EXTRA_VOLTAGE,
                0
            )

        val currentNowUa =
            readBatteryCurrent(context)

        val watts =
            if (
                voltageMv > 0 &&
                currentNowUa != 0L
            ) {

                kotlin.math.abs(
                    voltageMv.toDouble() *
                            currentNowUa.toDouble()
                ) /
                        1_000_000_000.0

            } else {
                0.0
            }

        val powerText =
            if (watts > 0.05) {

                String.format(
                    Locale.US,
                    "%.1f W",
                    watts
                )

            } else {
                "Power unavailable"
            }

        val plugText =
            when (plugged) {

                BatteryManager.BATTERY_PLUGGED_AC ->
                    "AC"

                BatteryManager.BATTERY_PLUGGED_USB ->
                    "USB"

                BatteryManager.BATTERY_PLUGGED_WIRELESS ->
                    "Wireless"

                else ->
                    ""
            }

        val content =
            if (plugText.isNotEmpty()) {

                "$batteryPercent% • " +
                        "$powerText • " +
                        plugText

            } else {

                "$batteryPercent% • " +
                        powerText
            }

        showLiveIsland(
            context,
            if (charging) {
                "Charging"
            } else {
                "Battery"
            },
            content,
            "charging_live"
        )
    }

    private fun readBatteryCurrent(
        context: Context
    ): Long {

        return try {

            if (
                Build.VERSION.SDK_INT < 21
            ) {
                return 0L
            }

            val manager =
                context.getSystemService(
                    BatteryManager::class.java
                )
                    ?: return 0L

            manager.getLongProperty(
                BatteryManager
                    .BATTERY_PROPERTY_CURRENT_NOW
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to read battery current",
                e
            )

            0L
        }
    }

    // ============================================================
    // MEDIA
    // ============================================================

    fun startMediaUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        val runnable =
            object : Runnable {

                override fun run() {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.MEDIA
                    ) {
                        return
                    }

                    updateMediaIsland(context)

                    mediaRunnable = this

                    mainHandler.postDelayed(
                        this,
                        750L
                    )
                }
            }

        mediaRunnable = runnable

        mainHandler.post(runnable)
    }

    private fun updateMediaIsland(
        context: Context
    ) {

        pendingMediaActions.clear()

        val service =
            MediaSessionListenerService.instance

        val previousPackage =
            displayedMediaPackage

        val media =
            service?.findBestMediaSnapshot(
                preferredPackage =
                    previousPackage
            )
                ?: service?.findBestMediaSnapshot()

        if (media == null) {

            displayedMediaPackage = null

            Log.d(
                TAG,
                "No media notification/session found"
            )

            showLiveIsland(
                context,
                "Media",
                "No active player",
                "media_live",
                includeClose = true
            )

            return
        }

        /*
         * Remember exactly which player generated the current
         * HyperIsland.
         */
        displayedMediaPackage =
            media.packageName

        val title =
            media.title.ifBlank {
                "Unknown track"
            }

        val artist =
            media.artist.ifBlank {

                media.appName.ifBlank {
                    media.packageName
                }
            }

        val positionText =
            formatMediaTime(
                media.position.coerceAtLeast(0L)
            )

        val durationText =
            if (media.duration > 0L) {

                formatMediaTime(
                    media.duration
                )

            } else {
                "--:--"
            }

        Log.d(
            TAG,
            "Media snapshot: " +
                    "package=${media.packageName}, " +
                    "title=$title, " +
                    "artist=$artist, " +
                    "playing=${media.playing}, " +
                    "state=${media.playbackState}, " +
                    "actions=${media.actions.size}"
        )

        val builder =
            buildLiveBuilder(
                context = context,
                business = "media_live",
                title = artist,
                content =
                    "$title • " +
                            "$positionText / $durationText"
            )

        /*
         * PREVIOUS
         */
        addMediaAction(
            context = context,
            key = "previous",
            title = "Previous",
            media = media
        )

        /*
         * PLAY / PAUSE
         *
         * The visual icon changes, but the actual HyperIsland
         * action remains "media_toggle".
         */
        val playbackKey =
            if (media.playing) {
                "pause"
            } else {
                "play"
            }

        addMediaAction(
            context = context,
            key = playbackKey,
            title =
                if (media.playing) {
                    "Pause"
                } else {
                    "Play"
                },
            media = media
        )

        /*
         * NEXT
         */
        addMediaAction(
            context = context,
            key = "next",
            title = "Next",
            media = media
        )

        notifyHyperIsland(
            context = context,
            title = artist,
            notificationId = LIVE_NOTIFICATION_ID,
            builder = builder,
            focusActions = pendingMediaActions,
            includeClose = false
        )
    }

    private fun addMediaAction(
        context: Context,
        key: String,
        title: String,
        media: MediaSessionListenerService.MediaSnapshot
    ) {

        try {

            /*
             * ====================================================
             * STABLE PLAY/PAUSE TOGGLE
             * ====================================================
             */

            if (
                key == "play" ||
                key == "pause"
            ) {

                val toggleIntent =
                    Intent(
                        context,
                        KuroMixReceiver::class.java
                    ).apply {

                        action =
                            ACTION_MEDIA_TOGGLE

                        putExtra(
                            "packageName",
                            media.packageName
                        )

                        addFlags(
                            Intent.FLAG_RECEIVER_FOREGROUND
                        )
                    }

                /*
                 * SAME request code every time.
                 *
                 * SAME action every time.
                 *
                 * SAME Focus key every time.
                 */
                val togglePendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        MEDIA_TOGGLE_REQUEST_CODE,
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )

                pendingMediaActions.add(
                    FocusAction(
                        key = "media_toggle",
                        title = title,
                        pendingIntent =
                            togglePendingIntent,
                        iconRes =
                            mediaIcon(key)
                    )
                )

                Log.d(
                    TAG,
                    "Stable media toggle: " +
                            "display=$key, " +
                            "package=${media.packageName}"
                )

                return
            }

            /*
             * ====================================================
             * PREVIOUS / NEXT
             * ====================================================
             */

            val originalAction =
                media.actions.firstOrNull {
                    it.key == key
                }

            if (originalAction != null) {

                pendingMediaActions.add(
                    FocusAction(
                        key = key,
                        title = title,
                        pendingIntent =
                            originalAction.pendingIntent,
                        iconRes =
                            mediaIcon(key)
                    )
                )

                Log.d(
                    TAG,
                    "Using original media action: " +
                            "$key -> ${media.packageName}"
                )

                return
            }

            /*
             * Fallback receiver.
             */
            val action =
                when (key) {

                    "next" ->
                        ACTION_MEDIA_NEXT

                    "previous" ->
                        ACTION_MEDIA_PREVIOUS

                    else ->
                        return
                }

            val fallbackIntent =
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    this.action =
                        action

                    putExtra(
                        "packageName",
                        media.packageName
                    )

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    (
                            "media_fallback_$key"
                                .hashCode()
                            ) and 0x7fffffff,
                    fallbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            pendingMediaActions.add(
                FocusAction(
                    key = key,
                    title = title,
                    pendingIntent =
                        pendingIntent,
                    iconRes =
                        mediaIcon(key)
                )
            )

            Log.d(
                TAG,
                "Using KuroMix fallback for $key"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create media action: $key",
                e
            )
        }
    }

    /*
     * ============================================================
     * MEDIA TOGGLE
     * ============================================================
     *
     * This is the actual Play/Pause implementation.
     *
     * The receiver does NOT tell us whether to play or pause.
     *
     * We inspect the MediaController at click time.
     */

    fun performMediaToggle(): Boolean {
        return performMediaToggle(
            displayedMediaPackage
        )
    }

    fun performMediaToggle(
        preferredPackage: String?
    ): Boolean {

        val now =
            SystemClock.elapsedRealtime()

        /*
         * Prevent accidental double delivery.
         */
        if (
            now - lastMediaToggleMs <
            MEDIA_TOGGLE_DEBOUNCE_MS
        ) {

            Log.d(
                TAG,
                "MEDIA_TOGGLE ignored: debounce"
            )

            return false
        }

        lastMediaToggleMs = now

        val service =
            MediaSessionListenerService.instance
                ?: run {

                    Log.w(
                        TAG,
                        "MEDIA_TOGGLE: listener not connected"
                    )

                    return false
                }

        /*
         * First attempt:
         *
         * Use the exact package currently shown on HyperIsland.
         */
        var snapshot =
            if (
                !preferredPackage.isNullOrBlank()
            ) {
                service.findBestMediaSnapshot(
                    preferredPackage =
                        preferredPackage
                )
            } else {
                null
            }

        /*
         * Fallback:
         *
         * If that package disappeared, find another active player.
         */
        if (snapshot == null) {

            snapshot =
                service.findBestMediaSnapshot()
        }

        if (snapshot == null) {

            Log.w(
                TAG,
                "MEDIA_TOGGLE: no media snapshot"
            )

            return false
        }

        val controller =
            snapshot.controller

        if (controller == null) {

            /*
             * No MediaController.
             *
             * Use the original notification's Play/Pause
             * PendingIntent as a last-resort fallback.
             */
            val fallbackKey =
                if (snapshot.playing) {
                    "pause"
                } else {
                    "play"
                }

            val notificationAction =
                snapshot.actions.firstOrNull {
                    it.key == fallbackKey
                }

            if (notificationAction != null) {

                return try {

                    notificationAction
                        .pendingIntent
                        .send()

                    Log.d(
                        TAG,
                        "MEDIA_TOGGLE fallback PendingIntent: " +
                                "${snapshot.packageName} / " +
                                fallbackKey
                    )

                    scheduleMediaRefresh(
                        applicationContext(),
                        250L
                    )

                    scheduleMediaRefresh(
                        applicationContext(),
                        700L
                    )

                    true

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "MEDIA_TOGGLE fallback failed",
                        e
                    )

                    false
                }
            }

            Log.w(
                TAG,
                "MEDIA_TOGGLE: no controller and no fallback action"
            )

            return false
        }

        val state =
            controller.playbackState

        val playbackState =
            state?.state
                ?: android.media.session
                    .PlaybackState
                    .STATE_NONE

        val actions =
            state?.actions ?: 0L

        Log.d(
            TAG,
            "MEDIA_TOGGLE: " +
                    "preferredPackage=$preferredPackage, " +
                    "selectedPackage=${snapshot.packageName}, " +
                    "controllerPackage=${controller.packageName}, " +
                    "state=$playbackState, " +
                    "snapshotPlaying=${snapshot.playing}, " +
                    "actions=$actions"
        )

        return try {

            when (playbackState) {

                /*
                 * Definitely playing.
                 */
                android.media.session
                    .PlaybackState
                    .STATE_PLAYING -> {

                    Log.d(
                        TAG,
                        "MEDIA_TOGGLE -> PAUSE"
                    )

                    controller.transportControls
                        .pause()
                }

                /*
                 * BUFFERING is special.
                 *
                 * A lot of media players use BUFFERING while
                 * the current item is still effectively active.
                 *
                 * If PAUSE is supported, pause.
                 * Otherwise play.
                 */
                android.media.session
                    .PlaybackState
                    .STATE_BUFFERING -> {

                    if (
                        actions and
                        android.media.session
                            .PlaybackState
                            .ACTION_PAUSE != 0L
                    ) {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE BUFFERING -> PAUSE"
                        )

                        controller.transportControls
                            .pause()

                    } else {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE BUFFERING -> PLAY"
                        )

                        controller.transportControls
                            .play()
                    }
                }

                /*
                 * Definitely paused/stopped.
                 */
                android.media.session
                    .PlaybackState
                    .STATE_PAUSED,

                android.media.session
                    .PlaybackState
                    .STATE_STOPPED -> {

                    Log.d(
                        TAG,
                        "MEDIA_TOGGLE -> PLAY"
                    )

                    controller.transportControls
                        .play()
                }

                /*
                 * NONE is ambiguous.
                 *
                 * Prefer PLAY unless only PAUSE is advertised.
                 */
                android.media.session
                    .PlaybackState
                    .STATE_NONE -> {

                    if (
                        actions and
                        android.media.session
                            .PlaybackState
                            .ACTION_PLAY == 0L &&
                        actions and
                        android.media.session
                            .PlaybackState
                            .ACTION_PAUSE != 0L
                    ) {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE NONE -> PAUSE"
                        )

                        controller.transportControls
                            .pause()

                    } else {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE NONE -> PLAY"
                        )

                        controller.transportControls
                            .play()
                    }
                }

                /*
                 * ERROR:
                 *
                 * Attempt PLAY to recover the session.
                 */
                android.media.session
                    .PlaybackState
                    .STATE_ERROR -> {

                    Log.d(
                        TAG,
                        "MEDIA_TOGGLE ERROR -> PLAY"
                    )

                    controller.transportControls
                        .play()
                }

                /*
                 * CONNECTING and all unknown states.
                 */
                else -> {

                    /*
                     * If PAUSE is explicitly supported and PLAY
                     * is not, the player is likely active.
                     */
                    val canPause =
                        actions and
                                android.media.session
                                    .PlaybackState
                                    .ACTION_PAUSE !=
                                0L

                    val canPlay =
                        actions and
                                android.media.session
                                    .PlaybackState
                                    .ACTION_PLAY !=
                                0L

                    if (
                        canPause &&
                        !canPlay
                    ) {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE UNKNOWN -> PAUSE"
                        )

                        controller.transportControls
                            .pause()

                    } else {

                        Log.d(
                            TAG,
                            "MEDIA_TOGGLE UNKNOWN -> PLAY"
                        )

                        controller.transportControls
                            .play()
                    }
                }
            }

            /*
             * Update the remembered package.
             */
            displayedMediaPackage =
                snapshot.packageName

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "MEDIA_TOGGLE failed",
                e
            )

            false
        }
    }

    /*
     * ============================================================
     * MEDIA ACTION
     * ============================================================
     */

    private fun refreshMediaIslandDelayed(
        context: Context,
        delayMs: Long
    ) {
        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).postDelayed(
            {
                updateMediaIsland(context)
            },
            delayMs
        )
    }

    private fun performMediaActionForPackage(
        context: Context,
        action: String,
        packageName: String?
    ) {
        val service =
            MediaSessionListenerService.instance
                ?: return

        val snapshot =
            service.findBestMediaSnapshot(
                preferredPackage = packageName
            ) ?: service.findBestMediaSnapshot()
            ?: return

        val controller =
            snapshot.controller
                ?: return

        try {
            when (action) {

                ACTION_MEDIA_PLAY -> {
                    controller.transportControls.play()
                }

                ACTION_MEDIA_PAUSE -> {
                    controller.transportControls.pause()
                }

                ACTION_MEDIA_NEXT -> {
                    controller.transportControls.skipToNext()
                }

                ACTION_MEDIA_PREVIOUS -> {
                    controller.transportControls.skipToPrevious()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "SuperIslandManager",
                "Failed media action: $action",
                e
            )
        }
    }

    fun performMediaAction(
        context: Context,
        action: String,
        packageName: String? = null
    ) {
        when (action) {

            ACTION_MEDIA_TOGGLE -> {
                val targetPackage =
                    packageName?.takeIf { it.isNotBlank() }
                        ?: displayedMediaPackage

                performMediaToggle(
                    targetPackage
                )

                refreshMediaIslandDelayed(context, 200L)
                refreshMediaIslandDelayed(context, 500L)
                refreshMediaIslandDelayed(context, 900L)
                refreshMediaIslandDelayed(context, 1400L)

                return
            }

            ACTION_MEDIA_PLAY,
            ACTION_MEDIA_PAUSE,
            ACTION_MEDIA_NEXT,
            ACTION_MEDIA_PREVIOUS -> {

                val targetPackage =
                    packageName?.takeIf { it.isNotBlank() }
                        ?: displayedMediaPackage

                performMediaActionForPackage(
                    context = context,
                    action = action,
                    packageName = targetPackage
                )

                refreshMediaIslandDelayed(context, 200L)
                refreshMediaIslandDelayed(context, 500L)
                refreshMediaIslandDelayed(context, 900L)

                return
            }
        }
    }
    private fun scheduleMediaRefresh(
        context: Context,
        delay: Long
    ) {

        mainHandler.postDelayed(
            {

                if (
                    liveRunning &&
                    currentLiveMode ==
                    LiveMode.MEDIA
                ) {

                    updateMediaIsland(context)
                }

            },
            delay
        )
    }

    private fun applicationContext(): Context {

        return MediaSessionListenerService
            .instance
            ?.applicationContext
            ?: throw IllegalStateException(
                "MediaSessionListenerService unavailable"
            )
    }

    private fun formatMediaTime(
        ms: Long
    ): String {

        val totalSeconds =
            ms / 1000L

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    // ============================================================
    // CLOCK
    // ============================================================

    fun startClockUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        val runnable =
            object : Runnable {

                override fun run() {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.CLOCK
                    ) {
                        return
                    }

                    updateClockIsland(context)

                    clockRunnable = this

                    mainHandler.postDelayed(
                        this,
                        1000L
                    )
                }
            }

        clockRunnable = runnable

        mainHandler.post(runnable)
    }

    private fun updateClockIsland(
        context: Context
    ) {

        val now =
            java.util.Calendar.getInstance()

        val time =
            String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                now.get(
                    java.util.Calendar.HOUR_OF_DAY
                ),
                now.get(
                    java.util.Calendar.MINUTE
                ),
                now.get(
                    java.util.Calendar.SECOND
                )
            )

        val date =
            String.format(
                Locale.getDefault(),
                "%02d/%02d/%04d",
                now.get(
                    java.util.Calendar.DAY_OF_MONTH
                ),
                now.get(
                    java.util.Calendar.MONTH
                ) + 1,
                now.get(
                    java.util.Calendar.YEAR
                )
            )

        showLiveIsland(
            context,
            time,
            date,
            "clock_live"
        )
    }

    // ============================================================
    // TIMER
    // ============================================================

    private const val TIMER_DURATION_MS =
        5L * 60L * 1000L

    @Volatile
    private var timerRemainingMs =
        TIMER_DURATION_MS

    @Volatile
    private var timerPaused = false

    private var timerLastTickMs = 0L

    fun startTimerUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        if (timerRemainingMs <= 0L) {
            timerRemainingMs =
                TIMER_DURATION_MS
        }

        timerPaused = false

        timerLastTickMs =
            SystemClock.elapsedRealtime()

        val runnable =
            object : Runnable {

                override fun run() {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.TIMER
                    ) {
                        return
                    }

                    val now =
                        SystemClock.elapsedRealtime()

                    if (!timerPaused) {

                        val elapsed =
                            (
                                    now -
                                            timerLastTickMs
                                    ).coerceAtLeast(0L)

                        timerRemainingMs =
                            (
                                    timerRemainingMs -
                                            elapsed
                                    ).coerceAtLeast(0L)
                    }

                    timerLastTickMs = now

                    showTimerIsland(context)

                    if (
                        timerRemainingMs > 0L
                    ) {

                        timerRunnable = this

                        mainHandler.postDelayed(
                            this,
                            250L
                        )

                    } else {

                        timerPaused = true
                        timerRunnable = null
                    }
                }
            }

        timerRunnable = runnable

        mainHandler.post(runnable)
    }

    private fun showTimerIsland(
        context: Context
    ) {

        val remaining =
            formatTimerTime(
                timerRemainingMs
            )

        val buttonTitle =
            if (timerPaused) {
                "Resume"
            } else {
                "Pause"
            }

        val pauseIntent =
            Intent(
                context,
                com.kuromify.kuromix.receiver
                    .KuroMixLiveActionReceiver::class.java
            ).apply {

                action =
                    if (timerPaused) {
                        ACTION_TIMER_RESUME
                    } else {
                        ACTION_TIMER_PAUSE
                    }
            }

        val resetIntent =
            Intent(
                context,
                com.kuromify.kuromix.receiver
                    .KuroMixLiveActionReceiver::class.java
            ).apply {

                action =
                    ACTION_TIMER_RESET
            }

        val builder =
            buildLiveBuilder(
                context = context,
                business = "timer_live",
                title = "5 Min Timer",
                content =
                    if (
                        timerRemainingMs == 0L
                    ) {
                        "Finished"
                    } else {
                        remaining
                    }
            )

        val actions =
            listOf(

                FocusAction(
                    key = "timer_toggle",
                    title = buttonTitle,
                    pendingIntent =
                        PendingIntent.getBroadcast(
                            context,
                            7001,
                            pauseIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                    PendingIntent.FLAG_IMMUTABLE
                        ),
                    iconRes =
                        if (timerPaused) {
                            mediaIcon("play")
                        } else {
                            mediaIcon("pause")
                        }
                ),

                FocusAction(
                    key = "timer_reset",
                    title = "Reset",
                    pendingIntent =
                        PendingIntent.getBroadcast(
                            context,
                            7002,
                            resetIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                    PendingIntent.FLAG_IMMUTABLE
                        ),
                    iconRes =
                        android.R.drawable.ic_popup_sync
                )
            )

        notifyHyperIsland(
            context,
            "5 Min Timer",
            LIVE_NOTIFICATION_ID,
            builder,
            focusActions = actions,
            includeClose = true
        )
    }

    private fun formatTimerTime(
        ms: Long
    ): String {

        val totalSeconds =
            (ms / 1000L)
                .coerceAtLeast(0L)

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    fun pauseTimer(
        context: Context
    ) {

        if (
            currentLiveMode !=
            LiveMode.TIMER
        ) {
            return
        }

        timerPaused = true

        timerLastTickMs =
            SystemClock.elapsedRealtime()

        showTimerIsland(context)
    }

    fun resumeTimer(
        context: Context
    ) {

        if (
            currentLiveMode !=
            LiveMode.TIMER
        ) {
            return
        }

        if (timerRemainingMs <= 0L) {

            timerRemainingMs =
                TIMER_DURATION_MS
        }

        timerPaused = false

        timerLastTickMs =
            SystemClock.elapsedRealtime()

        if (timerRunnable == null) {

            startTimerUpdates(context)

        } else {

            showTimerIsland(context)
        }
    }

    fun resetTimer(
        context: Context
    ) {

        timerRemainingMs =
            TIMER_DURATION_MS

        timerPaused = false

        timerLastTickMs =
            SystemClock.elapsedRealtime()

        if (
            currentLiveMode !=
            LiveMode.TIMER
        ) {

            currentLiveMode =
                LiveMode.TIMER

            liveRunning = true
        }

        if (timerRunnable == null) {

            startTimerUpdates(context)

        } else {

            showTimerIsland(context)
        }
    }

    // ============================================================
    // NETWORK
    // ============================================================

    fun startNetworkUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        lastRxBytes =
            android.net.TrafficStats
                .getTotalRxBytes()

        lastTxBytes =
            android.net.TrafficStats
                .getTotalTxBytes()

        lastNetworkSampleMs =
            SystemClock.elapsedRealtime()

        val runnable =
            object : Runnable {

                override fun run() {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.NETWORK
                    ) {
                        return
                    }

                    updateNetworkIsland(context)

                    networkRunnable = this

                    mainHandler.postDelayed(
                        this,
                        1000L
                    )
                }
            }

        networkRunnable = runnable

        mainHandler.post(runnable)
    }

    private fun updateNetworkIsland(
        context: Context
    ) {

        val nowRx =
            android.net.TrafficStats
                .getTotalRxBytes()

        val nowTx =
            android.net.TrafficStats
                .getTotalTxBytes()

        val nowMs =
            SystemClock.elapsedRealtime()

        if (
            nowRx ==
            android.net.TrafficStats
                .UNSUPPORTED.toLong() ||
            nowTx ==
            android.net.TrafficStats
                .UNSUPPORTED.toLong()
        ) {

            showLiveIsland(
                context,
                "Network",
                "TrafficStats unavailable",
                "network_live"
            )

            return
        }

        val elapsedMs =
            maxOf(
                1L,
                nowMs - lastNetworkSampleMs
            )

        val rxBps =
            (
                    (
                            nowRx -
                                    lastRxBytes
                            ).coerceAtLeast(0L) *
                            1000L
                    ) / elapsedMs

        val txBps =
            (
                    (
                            nowTx -
                                    lastTxBytes
                            ).coerceAtLeast(0L) *
                            1000L
                    ) / elapsedMs

        lastRxBytes = nowRx
        lastTxBytes = nowTx
        lastNetworkSampleMs = nowMs

        showLiveIsland(
            context,
            getConnectionType(context),
            "↓ ${formatRate(rxBps)}  " +
                    "↑ ${formatRate(txBps)}",
            "network_live"
        )
    }

    private fun formatRate(
        bytesPerSecond: Long
    ): String {

        val bits =
            bytesPerSecond * 8.0

        return when {

            bits >= 1_000_000_000.0 ->

                String.format(
                    Locale.US,
                    "%.1f Gbps",
                    bits /
                            1_000_000_000.0
                )

            bits >= 1_000_000.0 ->

                String.format(
                    Locale.US,
                    "%.1f Mbps",
                    bits /
                            1_000_000.0
                )

            bits >= 1_000.0 ->

                String.format(
                    Locale.US,
                    "%.1f Kbps",
                    bits /
                            1_000.0
                )

            else ->
                "$bytesPerSecond B/s"
        }
    }

    private fun getConnectionType(
        context: Context
    ): String {

        return try {

            val connectivity =
                context.getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as android.net.ConnectivityManager

            val network =
                connectivity.activeNetwork
                    ?: return "Offline"

            val caps =
                connectivity.getNetworkCapabilities(
                    network
                )
                    ?: return "Network"

            when {

                caps.hasTransport(
                    android.net.NetworkCapabilities
                        .TRANSPORT_WIFI
                ) ->
                    "Wi-Fi"

                caps.hasTransport(
                    android.net.NetworkCapabilities
                        .TRANSPORT_CELLULAR
                ) ->
                    "Mobile"

                caps.hasTransport(
                    android.net.NetworkCapabilities
                        .TRANSPORT_ETHERNET
                ) ->
                    "Ethernet"

                else ->
                    "Network"
            }

        } catch (_: Exception) {

            "Network"
        }
    }

    // ============================================================
    // TEMPERATURE
    // ============================================================

    /*
 * Reads the phone's current temperature in degrees Celsius.
 *
 * Strategy:
 *
 *  1. Walk /sys/class/thermal/thermal_zone* and classify
 *     each zone as SOC, BATTERY, or OTHER.
 *
 *  2. For each zone, normalise the raw value to Celsius,
 *     which includes unit detection:
 *       - milli-Celsius    (42000 -> 42.0)
 *       - centi-Celsius    (4200  -> 42.0)
 *       - tenths           (420   -> 42.0)
 *       - Fahrenheit       (95    -> 35.0)
 *       - Celsius          (42    -> 42.0)
 *
 *  3. Pick the hottest SOC zone. If none exists, fall back
 *     to the hottest OTHER zone. Battery zones are only
 *     used as a last resort.
 *
 *  4. If nothing usable is found, fall back to the coarse
 *     PowerManager thermal status.
 */
    private fun readPhoneTemperature(
        context: Context
    ): Float {

        try {

            val thermalRoot = File("/sys/class/thermal")

            val zones = thermalRoot.listFiles { file ->
                file.name.startsWith("thermal_zone")
            }

            if (zones != null && zones.isNotEmpty()) {

                var bestSocC = Float.NaN
                var bestOtherC = Float.NaN
                var bestBatteryC = Float.NaN

                for (zone in zones) {

                    val typeFile = File(zone, "type")
                    val tempFile = File(zone, "temp")

                    if (!tempFile.exists()) continue

                    val typeName =
                        try {
                            typeFile.readText().trim().lowercase(Locale.US)
                        } catch (_: Exception) {
                            ""
                        }

                    if (
                        typeName.contains("current") ||
                        typeName.contains("voltage") ||
                        typeName.contains("power")
                    ) {
                        continue
                    }

                    val rawValue =
                        try {
                            tempFile.readText().trim().toFloatOrNull()
                        } catch (_: Exception) {
                            null
                        } ?: continue

                    val isSoc =
                        typeName.contains("cpu") ||
                                typeName.contains("gpu") ||
                                typeName.contains("soc") ||
                                typeName.contains("tsens") ||
                                typeName.contains("core") ||
                                typeName.contains("cluster") ||
                                typeName.contains("big") ||
                                typeName.contains("little") ||
                                typeName.contains("silver") ||
                                typeName.contains("gold") ||
                                typeName.contains("prime") ||
                                typeName.contains("ap") ||
                                typeName.contains("s5p") ||
                                typeName.contains("mtk") ||
                                typeName.contains("xo-therm")

                    val isBattery =
                        typeName.contains("battery") ||
                                typeName.contains("batt") ||
                                typeName.contains("charger") ||
                                typeName.contains("usb") ||
                                typeName.contains("skin") ||
                                typeName.contains("quiet")

                    /*
                     * Detect Fahrenheit for battery/skin zones
                     * BEFORE normalising. A battery/skin reading
                     * of 90..110 is definitely Fahrenheit, because
                     * no phone chassis sits at 95 °C.
                     */
                    val celsius =
                        if (
                            (isBattery || typeName.contains("pa")) &&
                            rawValue in 90f..110f
                        ) {
                            (rawValue - 32f) * 5f / 9f
                        } else {
                            normaliseToCelsius(rawValue)
                        }

                    if (celsius.isNaN()) continue

                    when {

                        isSoc -> {
                            if (bestSocC.isNaN() || celsius > bestSocC) {
                                bestSocC = celsius
                            }
                        }

                        isBattery -> {
                            if (bestBatteryC.isNaN() || celsius > bestBatteryC) {
                                bestBatteryC = celsius
                            }
                        }

                        else -> {
                            if (bestOtherC.isNaN() || celsius > bestOtherC) {
                                bestOtherC = celsius
                            }
                        }
                    }

                    Log.d(
                        TAG,
                        "Thermal zone '$typeName' " +
                                "raw=$rawValue -> ${celsius}°C " +
                                "(soc=$isSoc, battery=$isBattery)"
                    )
                }

                /*
                 * Prefer SOC. Only fall back to OTHER, then
                 * BATTERY. Never let a battery zone win over a
                 * valid SOC zone.
                 */
                when {

                    !bestSocC.isNaN() ->
                        return bestSocC

                    !bestOtherC.isNaN() ->
                        return bestOtherC

                    !bestBatteryC.isNaN() ->
                        return bestBatteryC
                }
            }

        } catch (e: Exception) {

            Log.w(TAG, "Unable to read sysfs thermal zones", e)
        }

        /*
         * Coarse PowerManager fallback.
         */
        try {

            val pm = context.getSystemService(PowerManager::class.java)

            if (pm != null) {

                val status = pm.currentThermalStatus
                lastThermalStatus = status

                return when (status) {

                    PowerManager.THERMAL_STATUS_NONE      -> Float.NaN
                    PowerManager.THERMAL_STATUS_LIGHT     -> 38f
                    PowerManager.THERMAL_STATUS_MODERATE  -> 42f
                    PowerManager.THERMAL_STATUS_SEVERE    -> 46f
                    PowerManager.THERMAL_STATUS_CRITICAL  -> 50f
                    PowerManager.THERMAL_STATUS_EMERGENCY -> 55f
                    PowerManager.THERMAL_STATUS_SHUTDOWN  -> 60f
                    else -> Float.NaN
                }
            }

        } catch (e: Exception) {

            Log.w(TAG, "PowerManager thermal status unavailable", e)
        }

        return Float.NaN
    }

    /*
     * Converts a raw sysfs temperature value to Celsius.
     *
     * Unit detection is purely by magnitude, and the caller is
     * responsible for handling Fahrenheit battery zones before
     * calling this.
     *
     *   >= 100_000 -> milli-Kelvin   (e.g. 315_000 = 42 °C)
     *   >= 10_000  -> milli-Celsius  (e.g. 42_000  = 42 °C)
     *   >= 1_000   -> centi-Celsius  (e.g. 4_200   = 42 °C)
     *   >= 200     -> tenths         (e.g. 420     = 42 °C)
     *   >= 90      -> Fahrenheit     (e.g. 95      = 35 °C)
     *   else       -> Celsius
     *
     * Values that end up outside -20 … 85 °C are rejected, so a
     * stuck sensor or a Fahrenheit value that snuck past the
     * caller's battery check can't produce a bogus reading.
     */
    private fun normaliseToCelsius(
        raw: Float
    ): Float {

        if (raw.isNaN() || raw <= -273f) return Float.NaN

        val celsius =
            when {

                raw >= 100_000f ->
                    raw / 1000f - 273.15f

                raw >= 10_000f ->
                    raw / 1000f

                raw >= 1_000f ->
                    raw / 100f

                raw >= 200f ->
                    raw / 10f

                raw >= 90f ->
                    (raw - 32f) * 5f / 9f

                else ->
                    raw
            }

        if (
            celsius.isNaN() ||
            celsius < -20f ||
            celsius > 85f
        ) {
            return Float.NaN
        }

        return celsius
    }

    private fun thermalStatusLabel(
        status: Int
    ): String {

        return when (status) {

            PowerManager.THERMAL_STATUS_NONE ->
                "Normal"

            PowerManager.THERMAL_STATUS_LIGHT ->
                "Light"

            PowerManager.THERMAL_STATUS_MODERATE ->
                "Moderate"

            PowerManager.THERMAL_STATUS_SEVERE ->
                "Severe"

            PowerManager.THERMAL_STATUS_CRITICAL ->
                "Critical"

            PowerManager.THERMAL_STATUS_EMERGENCY ->
                "Emergency"

            PowerManager.THERMAL_STATUS_SHUTDOWN ->
                "Shutdown"

            else ->
                "Unknown"
        }
    }

    fun startTemperatureUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        lastTemperatureC = Float.NaN
        lastThermalStatus =
            PowerManager.THERMAL_STATUS_NONE

        val runnable =
            object : Runnable {

                override fun run() {

                    if (
                        !liveRunning ||
                        currentLiveMode !=
                        LiveMode.TEMP
                    ) {
                        return
                    }

                    updateTemperatureIsland(context)

                    temperatureRunnable = this

                    /*
                     * Thermal sensors change slowly, so 2s is
                     * plenty and saves battery.
                     */
                    mainHandler.postDelayed(
                        this,
                        2000L
                    )
                }
            }

        temperatureRunnable = runnable

        mainHandler.post(runnable)
    }

    private fun updateTemperatureIsland(
        context: Context
    ) {

        val tempC =
            readPhoneTemperature(context)

        if (!tempC.isNaN()) {
            lastTemperatureC = tempC
        }

        /*
         * Re-read thermal status for the subtitle line.
         */
        try {

            val pm =
                context.getSystemService(
                    PowerManager::class.java
                )

            if (pm != null) {
                lastThermalStatus =
                    pm.currentThermalStatus
            }

        } catch (_: Exception) {
        }

        val displayTemp =
            if (!lastTemperatureC.isNaN()) {

                String.format(
                    Locale.US,
                    "%.1f°C",
                    lastTemperatureC
                )

            } else {

                "N/A"
            }

        val statusLabel =
            thermalStatusLabel(
                lastThermalStatus
            )

        showLiveIsland(
            context,
            "Temperature",
            "$displayTemp • $statusLabel",
            "temperature_live"
        )
    }

    // ============================================================
    // COMMON HYPERISLAND BUILDER
    // ============================================================

    private fun buildLiveBuilder(
        context: Context,
        business: String,
        title: String,
        content: String
    ): HyperIslandNotification {

        val pictureKey =
            "kuromix_live_$business"

        val picture =
            HyperPicture(
                pictureKey,
                context,
                R.drawable.kuromify_logo_w
            )

        val leftInfo =
            ImageTextInfoLeft(
                type = 1,
                picInfo =
                    PicInfo(
                        type = 1,
                        pic = pictureKey
                    ),
                textInfo =
                    TextInfo(
                        title = title
                    )
            )

        val rightInfo =
            ImageTextInfoRight(
                type = 2,
                textInfo =
                    TextInfo(
                        title = content
                    )
            )

        return HyperIslandNotification
            .Builder(
                context,
                business,
                title
            )
            .setSmallWindowTarget(
                "${context.packageName}.MainActivity"
            )
            .addPicture(picture)
            .setChatInfo(
                title = title,
                content = content,
                pictureKey = pictureKey
            )
            .setSmallIsland(
                pictureKey
            )
            .setBigIslandInfo(
                left = leftInfo,
                right = rightInfo
            )
            .setEnableFloat(false)
            .setShowNotification(true)
    }

    // ============================================================
    // SHOW LIVE ISLAND
    // ============================================================

    private fun showLiveIsland(
        context: Context,
        title: String,
        content: String,
        business: String,
        includeClose: Boolean = true
    ) {

        try {

            if (
                !HyperIslandNotification
                    .isSupported(context)
            ) {

                Log.w(
                    TAG,
                    "HyperIsland unsupported"
                )

                return
            }

            val builder =
                buildLiveBuilder(
                    context,
                    business,
                    title,
                    content
                )

            notifyHyperIsland(
                context,
                title,
                LIVE_NOTIFICATION_ID,
                builder,
                includeClose = includeClose
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to update live HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun notifyHyperIsland(
        context: Context,
        title: String,
        notificationId: Int,
        builder: HyperIslandNotification,
        focusActions: List<FocusAction> = emptyList(),
        includeClose: Boolean = true
    ) {

        try {

            val baseJson =
                builder.buildJsonParam()

            val json =
                addFocusActionsToJson(
                    context,
                    baseJson,
                    focusActions,
                    includeClose
                )

            Log.d(
                TAG,
                "HYPERISLAND_JSON = $json"
            )

            val notificationBuilder =
                NotificationCompat.Builder(
                    context,
                    CHANNEL_ID
                )
                    .setSmallIcon(
                        R.drawable.kuromify_logo_w
                    )
                    .setContentTitle(title)
                    .setContentIntent(
                        createOpenAppPendingIntent(
                            context
                        )
                    )
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setOngoing(
                        notificationId !=
                                LIVE_NOTIFICATION_ID
                    )
                    .setCategory(
                        NotificationCompat
                            .CATEGORY_STATUS
                    )
                    .addExtras(
                        builder.buildResourceBundle()
                    )

            /*
             * Normal Android notification actions.
             */
            if (
                (
                        notificationId ==
                                LIVE_NOTIFICATION_ID ||
                                notificationId ==
                                DOWNLOAD_NOTIFICATION_ID
                        ) &&
                focusActions.isNotEmpty()
            ) {

                focusActions
                    .take(3)
                    .forEach { item ->

                        notificationBuilder
                            .addAction(
                                NotificationCompat
                                    .Action.Builder(
                                        item.iconRes,
                                        item.title,
                                        item.pendingIntent
                                    )
                                    .build()
                            )
                    }
            }

            if (
                (
                        notificationId ==
                                LIVE_NOTIFICATION_ID ||
                                notificationId ==
                                DOWNLOAD_NOTIFICATION_ID
                        ) &&
                includeClose
            ) {

                notificationBuilder
                    .setDeleteIntent(
                        createClosePendingIntent(
                            context,
                            notificationId
                        )
                    )
            }

            val notification =
                notificationBuilder.build()

            /*
             * Register Focus actions.
             */
            if (
                (
                        notificationId ==
                                LIVE_NOTIFICATION_ID ||
                                notificationId ==
                                DOWNLOAD_NOTIFICATION_ID
                        ) &&
                focusActions.isNotEmpty()
            ) {

                val focusActionBundle =
                    Bundle()

                focusActions
                    .take(3)
                    .forEach { item ->

                        val action =
                            Notification.Action.Builder(
                                android.graphics.drawable
                                    .Icon
                                    .createWithResource(
                                        context,
                                        item.iconRes
                                    ),
                                item.title,
                                item.pendingIntent
                            ).build()

                        focusActionBundle.putParcelable(
                            "miui.focus.action_${item.key}",
                            action
                        )

                        Log.d(
                            TAG,
                            "Registered Focus action: " +
                                    "miui.focus.action_${item.key}"
                        )
                    }

                notification.extras.putBundle(
                    EXTRA_FOCUS_ACTIONS,
                    focusActionBundle
                )
            }

            /*
             * Close action.
             *
             * Registered for BOTH the live notification and the
             * download notification. Earlier versions only
             * allowed LIVE_NOTIFICATION_ID, which is why the
             * download Close button silently did nothing.
             *
             * The PendingIntent now uses a per-notification
             * request code (CLOSE_REQUEST_CODE_BASE xor
             * notificationId) so HyperOS cannot cache the live
             * island's Close PI and reuse it for download.
             */
            if (
                (
                        notificationId ==
                                LIVE_NOTIFICATION_ID ||
                                notificationId ==
                                DOWNLOAD_NOTIFICATION_ID
                        ) &&
                includeClose
            ) {

                val closePendingIntent =
                    createClosePendingIntent(
                        context,
                        notificationId
                    )

                val closeAction =
                    Notification.Action.Builder(
                        android.graphics.drawable
                            .Icon
                            .createWithResource(
                                context,
                                closeIcon()
                            ),
                        "Close",
                        closePendingIntent
                    ).build()

                val focusActionBundle =
                    notification.extras
                        .getBundle(
                            EXTRA_FOCUS_ACTIONS
                        )
                        ?: Bundle()

                focusActionBundle.putParcelable(
                    "miui.focus.action_close",
                    closeAction
                )

                notification.extras.putBundle(
                    EXTRA_FOCUS_ACTIONS,
                    focusActionBundle
                )
            }

            notification.extras.putString(
                EXTRA_FOCUS_PARAM,
                json
            )

            context.getSystemService(
                NotificationManager::class.java
            ).notify(
                notificationId,
                notification
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to notify HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // FOCUS JSON
    // ============================================================

    private fun addFocusActionsToJson(
        context: Context,
        json: String,
        actions: List<FocusAction>,
        includeClose: Boolean
    ): String {

        return try {

            val root =
                JSONObject(json)

            val paramV2 =
                root.optJSONObject(
                    "param_v2"
                )
                    ?: return json

            /*
             * ====================================================
             * MEDIA BOTTOM MODE
             * ====================================================
             *
             * Media is the only caller that sets
             * includeClose == false while still passing focus
             * actions. In that case, we move EVERY media button
             * (previous, play/pause, next) into the bottom
             * textButton slot, and leave the top "actions" row
             * empty.
             *
             * This gives a single horizontal control strip at
             * the bottom of the island, replacing the close
             * button entirely.
             */

            val isMediaBottomMode =
                !includeClose &&
                        actions.any {
                            it.key == "media_toggle"
                        }

            if (isMediaBottomMode) {

                /*
                 * Re-order so the visual layout is:
                 *
                 *     [Previous] [Play/Pause] [Next]
                 */
                val orderedKeys =
                    listOf(
                        "previous",
                        "media_toggle",
                        "next"
                    )

                val orderedActions =
                    orderedKeys.mapNotNull { wantedKey ->
                        actions.firstOrNull {
                            it.key == wantedKey
                        }
                    }

                val bottomArray =
                    JSONArray()

                orderedActions.forEach { item ->

                    val bottomObject =
                        JSONObject().apply {

                            put(
                                "type",
                                2
                            )

                            put(
                                "actionTitle",
                                item.title
                            )

                            put(
                                "action",
                                "miui.focus.action_${item.key}"
                            )

                            put(
                                "actionIntentType",
                                2
                            )

                            put(
                                "actionIntent",
                                createMediaIntentForJson(
                                    context,
                                    item
                                ).toFocusIntentUri()
                            )
                        }

                    bottomArray.put(
                        bottomObject
                    )

                    Log.d(
                        TAG,
                        "Focus JSON (bottom): " +
                                "miui.focus.action_${item.key}"
                    )
                }

                if (bottomArray.length() > 0) {

                    paramV2.put(
                        "textButton",
                        bottomArray
                    )
                }

                /*
                 * Explicitly clear the top action row so no
                 * buttons appear on the right side of the
                 * island.
                 */
                paramV2.remove("actions")

                Log.d(
                    TAG,
                    "Focus JSON: media bottom mode " +
                            "(${bottomArray.length()} buttons)"
                )

                return root.toString()
            }

            /*
             * ====================================================
             * NON-MEDIA MODE (legacy behaviour)
             * ====================================================
             */

            if (actions.isNotEmpty()) {

                val actionArray =
                    JSONArray()

                actions
                    .take(3)
                    .forEach { item ->

                        val actionObject =
                            JSONObject().apply {

                                put(
                                    "action",
                                    "miui.focus.action_${item.key}"
                                )
                            }

                        actionArray.put(
                            actionObject
                        )

                        Log.d(
                            TAG,
                            "Focus JSON action: " +
                                    "miui.focus.action_${item.key}"
                        )
                    }

                paramV2.put(
                    "actions",
                    actionArray
                )
            }

            if (includeClose) {

                val closeIntent =
                    Intent(
                        context,
                        KuroMixCloseReceiver::class.java
                    ).apply {

                        action =
                            ACTION_CLOSE_LIVE
                    }

                val closeArray =
                    JSONArray()

                val closeObject =
                    JSONObject().apply {

                        put(
                            "type",
                            2
                        )

                        put(
                            "actionTitle",
                            "Close"
                        )

                        put(
                            "action",
                            "miui.focus.action_close"
                        )

                        put(
                            "actionIntentType",
                            2
                        )

                        put(
                            "actionIntent",
                            closeIntent
                                .toFocusIntentUri()
                        )
                    }

                closeArray.put(
                    closeObject
                )

                paramV2.put(
                    "textButton",
                    closeArray
                )
            }

            root.toString()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to append Focus actions",
                e
            )

            json
        }
    }

    /*
     * Rebuilds the Intent URI for a media FocusAction so it can
     * be embedded in the bottom textButton JSON.
     *
     * PendingIntent does not expose its wrapped Intent, so we
     * reconstruct the equivalent Intent from the action key and
     * the currently displayed media package.
     */
    private fun createMediaIntentForJson(
        context: Context,
        action: FocusAction
    ): Intent {

        val mediaPackage =
            displayedMediaPackage ?: ""

        return when (action.key) {

            "media_toggle" ->
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    this.action =
                        ACTION_MEDIA_TOGGLE

                    putExtra(
                        "packageName",
                        mediaPackage
                    )

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }

            "previous" ->
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    this.action =
                        ACTION_MEDIA_PREVIOUS

                    putExtra(
                        "packageName",
                        mediaPackage
                    )

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }

            "next" ->
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    this.action =
                        ACTION_MEDIA_NEXT

                    putExtra(
                        "packageName",
                        mediaPackage
                    )

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }

            else ->
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    this.action =
                        ACTION_MEDIA_TOGGLE

                    putExtra(
                        "packageName",
                        mediaPackage
                    )

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }
        }
    }

    private fun Intent.toFocusIntentUri(): String {

        return android.net.Uri.parse(
            toUri(
                Intent.URI_INTENT_SCHEME
            )
        ).toString()
    }

    /*
     * Creates the Close PendingIntent.
     *
     * The request code is XORed with the notification ID so
     * the live island and the download island get genuinely
     * different PendingIntents. Without this, HyperOS caches
     * the first Close PI it ever saw and reuses it for every
     * later notification in the same slot, which is why the
     * download Close button used to do nothing.
     */
    private fun createClosePendingIntent(
        context: Context,
        notificationId: Int
    ): PendingIntent {

        val intent =
            Intent(
                context,
                KuroMixCloseReceiver::class.java
            ).apply {

                action =
                    ACTION_CLOSE_LIVE

                /*
                 * Tell the receiver which notification the
                 * close belongs to. The current receiver
                 * ignores this extra, but it is useful for
                 * future per-notification teardown.
                 */
                putExtra(
                    "notificationId",
                    notificationId
                )
            }

        val requestCode =
            CLOSE_REQUEST_CODE_BASE xor notificationId

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

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
    // COMPATIBILITY API
    // ============================================================

    fun showChargingTest(
        context: Context,
        battery: Int = 0,
        power: Int = 0
    ) {

        startLiveUpdates(
            context,
            "charging"
        )
    }

    fun showMediaTest(
        context: Context,
        artist: String = "",
        title: String = ""
    ) {

        startLiveUpdates(
            context,
            "media"
        )
    }

    fun showTimerTest(
        context: Context,
        remaining: String = ""
    ) {

        timerRemainingMs =
            TIMER_DURATION_MS

        timerPaused = false

        startLiveUpdates(
            context,
            "timer"
        )
    }

    fun showNetworkTest(
        context: Context,
        network: String = "",
        speed: String = ""
    ) {

        startLiveUpdates(
            context,
            "network"
        )
    }

    /*
     * showGameTest now routes to temperature mode.
     *
     * The old signature is preserved so existing callers keep
     * compiling; the fps/temperature params are ignored.
     */
    fun showGameTest(
        context: Context,
        fps: Int = 0,
        temperature: Int = 0
    ) {

        startLiveUpdates(
            context,
            "temperature"
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

            createNotificationChannel(context)

            val stopIntent =
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    action =
                        KuroMixReceiver
                            .ACTION_STOP_MIRROR

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
                NotificationCompat
                    .Builder(
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
                        NotificationCompat
                            .CATEGORY_SERVICE
                    )
                    .addAction(
                        NotificationCompat
                            .Action.Builder(
                                R.drawable
                                    .kuromify_logo_w,
                                "Stop",
                                stopPendingIntent
                            )
                            .build()
                    )
                    .build()

            context.getSystemService(
                NotificationManager::class.java
            ).notify(
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

            context.getSystemService(
                NotificationManager::class.java
            ).cancel(
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
    // TEST / CANCEL
    // ============================================================

    fun showTestIsland(
        context: Context,
        title: String = "KuroMix",
        content: String = "HyperIsland Test",
        business: String = TEST_BUSINESS_ID
    ) {

        showLiveIsland(
            context,
            title,
            content,
            business
        )
    }

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
                LIVE_NOTIFICATION_ID
            )

            manager.cancel(
                DOWNLOAD_NOTIFICATION_ID
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel HyperIsland",
                e
            )
        }
    }

    // ============================================================
    // DOWNLOAD
    // ============================================================

    @Volatile
    private var downloadTestRunning = false

    @Volatile
    private var downloadTestPaused = false

    @Volatile
    private var downloadTestProgress = 0

    @Volatile
    private var downloadTestThread:
            Thread? = null

    fun showDownloadTest(
        context: Context,
        progress: Int = 0,
        isFirstUpdate: Boolean = false
    ) {

        try {

            createNotificationChannel(context)

            if (
                !HyperIslandNotification
                    .isSupported(context)
            ) {
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

            val progressInfo =
                io.github.d4viddf
                    .hyperisland_kit
                    .models
                    .ProgressTextInfo(
                        io.github.d4viddf
                            .hyperisland_kit
                            .models
                            .CircularProgressInfo(
                                progress =
                                    safeProgress,
                                colorReach =
                                    "#007AFF",
                                isCCW = true
                            )
                    )

            val builder =
                HyperIslandNotification
                    .Builder(
                        context,
                        DOWNLOAD_BUSINESS_ID,
                        "Download"
                    )
                    .setSmallWindowTarget(
                        "${context.packageName}.MainActivity"
                    )
                    .addPicture(picture)
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
                                        pic =
                                            pictureKey
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
                    .setShowNotification(true)

            if (
                safeProgress >= 100
            ) {

                builder.setTimeout(
                    5000L
                )
            }

            val toggleIntent =
                Intent(
                    context,
                    com.kuromify.kuromix.receiver
                        .KuroMixLiveActionReceiver::class.java
                ).apply {

                    action =
                        if (downloadTestPaused) {
                            ACTION_DOWNLOAD_RESUME
                        } else {
                            ACTION_DOWNLOAD_PAUSE
                        }
                }

            val resetIntent =
                Intent(
                    context,
                    com.kuromify.kuromix.receiver
                        .KuroMixLiveActionReceiver::class.java
                ).apply {

                    action =
                        ACTION_DOWNLOAD_RESET
                }

            val togglePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    8001,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            val resetPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    8002,
                    resetIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            notifyHyperIsland(
                context,
                "KuroMix.apk",
                DOWNLOAD_NOTIFICATION_ID,
                builder,
                focusActions =
                    listOf(

                        FocusAction(
                            key = "download_toggle",
                            title =
                                if (
                                    downloadTestPaused
                                ) {
                                    "Resume"
                                } else {
                                    "Pause"
                                },
                            pendingIntent =
                                togglePendingIntent,
                            iconRes =
                                if (
                                    downloadTestPaused
                                ) {
                                    mediaIcon("play")
                                } else {
                                    mediaIcon("pause")
                                }
                        ),

                        FocusAction(
                            key = "download_reset",
                            title = "Reset",
                            pendingIntent =
                                resetPendingIntent,
                            iconRes =
                                android.R.drawable
                                    .ic_popup_sync
                        )
                    ),
                includeClose = true
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to show Download HyperIsland",
                e
            )
        }
    }

    fun isDownloadTestRunning():
            Boolean {
        return downloadTestRunning
    }

    fun startDownloadTest(
        context: Context
    ) {

        if (downloadTestRunning) {
            return
        }

        if (
            !isHyperIslandHookEnabled(context)
        ) {
            return
        }

        if (
            !HyperIslandNotification
                .isSupported(context)
        ) {
            return
        }

        downloadTestProgress = 0
        downloadTestPaused = false
        downloadTestRunning = true

        downloadTestThread =
            Thread {

                try {

                    while (
                        downloadTestRunning &&
                        downloadTestProgress < 100
                    ) {

                        if (
                            downloadTestPaused
                        ) {

                            Thread.sleep(100L)
                            continue
                        }

                        val progress =
                            downloadTestProgress

                        mainHandler.post {

                            if (
                                downloadTestRunning
                            ) {

                                showDownloadTest(
                                    context,
                                    progress,
                                    progress == 0
                                )
                            }
                        }

                        if (
                            progress >= 100
                        ) {
                            break
                        }

                        Thread.sleep(500L)

                        downloadTestProgress =
                            (
                                    downloadTestProgress +
                                            1
                                    ).coerceAtMost(100)
                    }

                    mainHandler.post {

                        if (
                            downloadTestRunning
                        ) {

                            showDownloadTest(
                                context,
                                downloadTestProgress,
                                downloadTestProgress == 0
                            )
                        }
                    }

                } catch (
                    e: InterruptedException
                ) {

                    Thread.currentThread()
                        .interrupt()

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Download test failed",
                        e
                    )

                } finally {

                    if (
                        downloadTestProgress >= 100
                    ) {

                        downloadTestRunning = false
                        downloadTestPaused = false
                    }

                    downloadTestThread = null
                }

            }.also {
                it.start()
            }
    }

    fun pauseDownloadTest(
        context: Context
    ) {

        if (!downloadTestRunning) {
            return
        }

        downloadTestPaused = true

        showDownloadTest(
            context,
            downloadTestProgress
        )
    }

    fun resumeDownloadTest(
        context: Context
    ) {

        if (!downloadTestRunning) {

            startDownloadTest(context)

            return
        }

        downloadTestPaused = false

        showDownloadTest(
            context,
            downloadTestProgress
        )
    }

    fun resetDownloadTest(
        context: Context
    ) {

        downloadTestThread?.interrupt()

        downloadTestThread = null
        downloadTestProgress = 0
        downloadTestPaused = false
        downloadTestRunning = false

        startDownloadTest(context)
    }

    fun stopDownloadTest(
        context: Context
    ) {

        downloadTestRunning = false
        downloadTestPaused = false
        downloadTestProgress = 0

        downloadTestThread?.interrupt()

        downloadTestThread = null

        try {

            context.getSystemService(
                NotificationManager::class.java
            ).cancel(
                DOWNLOAD_NOTIFICATION_ID
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel download notification",
                e
            )
        }
    }

    // ============================================================
    // FOCUS ACTION COMPATIBILITY
    // ============================================================

    fun addFocusAction(
        context: Context,
        builder: NotificationCompat.Builder,
        actionKey: String,
        title: String,
        intent: Intent,
        iconRes: Int =
            android.R.drawable.ic_menu_info_details
    ) {

        try {

            val requestCode =
                actionKey.hashCode() and
                        0x7fffffff

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            builder.addAction(
                NotificationCompat
                    .Action.Builder(
                        iconRes,
                        title,
                        pendingIntent
                    )
                    .build()
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


/*
 * ================================================================
 * MEDIA SESSION LISTENER
 * ================================================================
 *
 * NotificationListener-backed media detector.
 *
 * MediaController is preferred because it gives us the actual
 * PlaybackState instead of trusting the notification button title.
 *
 * Notification PendingIntents remain available as fallbacks for
 * players that expose media controls but don't provide a usable
 * MediaController.
 */
class MediaSessionListenerService :
    android.service.notification.NotificationListenerService() {

    data class MediaAction(
        val key: String,
        val title: String,
        val pendingIntent: PendingIntent
    )

    data class MediaSnapshot(
        val packageName: String,
        val appName: String,
        val title: String,
        val artist: String,
        val position: Long,
        val duration: Long,
        val playing: Boolean,
        val playbackState: Int,
        val actions: List<MediaAction>,
        val controller: MediaController?
    )

    companion object {

        private const val TAG =
            "MediaSessionListenerService"

        @Volatile
        var instance:
                MediaSessionListenerService? =
            null
            private set
    }

    override fun onListenerConnected() {

        super.onListenerConnected()

        instance = this

        Log.d(
            TAG,
            "NotificationListener connected"
        )

        Log.d(
            TAG,
            "Active notifications = " +
                    try {
                        activeNotifications.size
                    } catch (_: Exception) {
                        -1
                    }
        )

        notifyMediaSessionChanged()
    }

    override fun onListenerDisconnected() {

        Log.d(
            TAG,
            "NotificationListener disconnected"
        )

        if (instance === this) {
            instance = null
        }

        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(
        sbn:
        android.service.notification
        .StatusBarNotification
    ) {

        inspectNotification(sbn)
    }

    override fun onNotificationPosted(
        sbn:
        android.service.notification
        .StatusBarNotification,
        rankingMap: RankingMap
    ) {

        inspectNotification(sbn)
    }

    override fun onNotificationRemoved(
        sbn:
        android.service.notification
        .StatusBarNotification
    ) {

        Log.d(
            TAG,
            "Notification removed: " +
                    sbn.packageName
        )

        notifyMediaSessionChanged()
    }

    private fun inspectNotification(
        sbn:
        android.service.notification
        .StatusBarNotification
    ) {

        val notification =
            sbn.notification
                ?: return

        val token =
            getMediaSessionToken(
                notification
            )

        val actions =
            extractMediaActions(
                notification
            )

        if (
            token != null ||
            actions.isNotEmpty() ||
            notification.category ==
            Notification.CATEGORY_TRANSPORT
        ) {

            Log.d(
                TAG,
                "Possible media notification: " +
                        "package=${sbn.packageName}, " +
                        "token=${token != null}, " +
                        "actions=${actions.size}"
            )

            notifyMediaSessionChanged()
        }
    }

    // ============================================================
    // MEDIA SESSION TOKEN
    // ============================================================

    private fun getMediaSessionToken(
        notification: Notification
    ): MediaSession.Token? {

        return try {

            val extras =
                notification.extras

            if (Build.VERSION.SDK_INT >= 33) {

                extras.getParcelable(
                    Notification.EXTRA_MEDIA_SESSION,
                    MediaSession.Token::class.java
                )

            } else {

                @Suppress("DEPRECATION")

                extras.getParcelable(
                    Notification.EXTRA_MEDIA_SESSION
                ) as? MediaSession.Token
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to read media session token",
                e
            )

            null
        }
    }

    // ============================================================
    // MEDIA ACTION EXTRACTION
    // ============================================================

    private fun extractMediaActions(
        notification: Notification
    ): List<MediaAction> {

        val result =
            mutableListOf<MediaAction>()

        val notificationActions =
            notification.actions
                ?: return result

        for (
        (index, action)
        in notificationActions.withIndex()
        ) {

            val title =
                action.title
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val lower =
                title.lowercase(Locale.US)

            val key =
                when {

                    lower.contains("previous") ||
                            lower.contains("prev") ||
                            lower.contains("back") ||
                            lower.contains("before") ||
                            lower.contains("ก่อนหน้า") ||
                            lower.contains("上一") ->

                        "previous"

                    lower.contains("next") ||
                            lower.contains("skip") ||
                            lower.contains("forward") ||
                            lower.contains("ถัดไป") ||
                            lower.contains("下一") ->

                        "next"

                    lower.contains("pause") ||
                            lower.contains("หยุด") ||
                            lower.contains("暂停") ->

                        "pause"

                    lower.contains("play") ||
                            lower.contains("resume") ||
                            lower.contains("เล่น") ||
                            lower.contains("播放") ->

                        "play"

                    title.isBlank() ->

                        when (index) {

                            0 ->
                                "previous"

                            1 ->
                                "play"

                            2 ->
                                "next"

                            else ->
                                null
                        }

                    else ->
                        null
                }

            if (
                key != null &&
                action.actionIntent != null
            ) {

                Log.d(
                    TAG,
                    "Media action detected: " +
                            "$key / $title"
                )

                result.add(
                    MediaAction(
                        key = key,
                        title = title,
                        pendingIntent =
                            action.actionIntent
                    )
                )
            }
        }

        return result
    }

    // ============================================================
    // FIND MEDIA SNAPSHOT
    // ============================================================

    fun findBestMediaSnapshot():
            MediaSnapshot? {

        return findBestMediaSnapshot(
            preferredPackage = null
        )
    }

    /*
     * preferredPackage is important.
     *
     * When HyperIsland currently displays Spotify, for example,
     * we should continue controlling Spotify even if YouTube Music
     * or another media notification is also present.
     */
    fun findBestMediaSnapshot(
        preferredPackage: String?
    ): MediaSnapshot? {

        val notifications =
            try {

                activeNotifications

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Unable to read active notifications",
                    e
                )

                return null
            }

        var bestPreferredPlaying:
                MediaSnapshot? = null

        var bestPreferredPaused:
                MediaSnapshot? = null

        var bestPreferredUnknown:
                MediaSnapshot? = null

        var bestPlaying:
                MediaSnapshot? = null

        var bestPaused:
                MediaSnapshot? = null

        var bestUnknown:
                MediaSnapshot? = null

        for (sbn in notifications) {

            val notification =
                sbn.notification
                    ?: continue

            val token =
                getMediaSessionToken(
                    notification
                )

            val actions =
                extractMediaActions(
                    notification
                )

            val isTransportNotification =
                notification.category ==
                        Notification.CATEGORY_TRANSPORT

            if (
                token == null &&
                actions.isEmpty() &&
                !isTransportNotification
            ) {
                continue
            }

            /*
             * ----------------------------------------------------
             * CONTROLLER
             * ----------------------------------------------------
             */

            val controller =
                if (token != null) {

                    try {

                        MediaController(
                            this,
                            token
                        )

                    } catch (e: Exception) {

                        Log.w(
                            TAG,
                            "Unable to create MediaController for " +
                                    sbn.packageName,
                            e
                        )

                        null
                    }

                } else {
                    null
                }

            /*
             * Ignore a broken controller belonging to a package
             * that isn't actually the notification package.
             *
             * This helps avoid mismatched session notifications.
             */
            if (
                controller != null &&
                controller.packageName !=
                sbn.packageName
            ) {

                Log.w(
                    TAG,
                    "Controller package mismatch: " +
                            "notification=${sbn.packageName}, " +
                            "controller=${controller.packageName}"
                )
            }

            val metadata =
                controller?.metadata

            val state =
                controller?.playbackState

            /*
             * ----------------------------------------------------
             * METADATA
             * ----------------------------------------------------
             */

            val title =
                metadata?.getString(
                    android.media.MediaMetadata
                        .METADATA_KEY_TITLE
                )
                    ?: notification.extras
                        .getCharSequence(
                            Notification.EXTRA_TITLE
                        )
                        ?.toString()
                    ?: "Unknown track"

            val artist =
                metadata?.getString(
                    android.media.MediaMetadata
                        .METADATA_KEY_ARTIST
                )
                    ?: metadata?.getString(
                        android.media.MediaMetadata
                            .METADATA_KEY_ALBUM_ARTIST
                    )
                    ?: notification.extras
                        .getCharSequence(
                            Notification.EXTRA_TEXT
                        )
                        ?.toString()
                    ?: ""

            val duration =
                metadata?.getLong(
                    android.media.MediaMetadata
                        .METADATA_KEY_DURATION
                )
                    ?: 0L

            val position =
                state?.position
                    ?.coerceAtLeast(0L)
                    ?: 0L

            /*
             * ----------------------------------------------------
             * PLAYBACK STATE
             * ----------------------------------------------------
             */

            val playbackState =
                state?.state
                    ?: android.media.session
                        .PlaybackState
                        .STATE_NONE

            val playing =
                playbackState ==
                        android.media.session
                            .PlaybackState
                            .STATE_PLAYING

            val paused =
                playbackState ==
                        android.media.session
                            .PlaybackState
                            .STATE_PAUSED

            val stateDescription =
                when {

                    playing ->
                        "PLAYING"

                    paused ->
                        "PAUSED"

                    playbackState ==
                            android.media.session
                                .PlaybackState
                                .STATE_BUFFERING ->
                        "BUFFERING"

                    playbackState ==
                            android.media.session
                                .PlaybackState
                                .STATE_CONNECTING ->
                        "CONNECTING"

                    playbackState ==
                            android.media.session
                                .PlaybackState
                                .STATE_ERROR ->
                        "ERROR"

                    playbackState ==
                            android.media.session
                                .PlaybackState
                                .STATE_STOPPED ->
                        "STOPPED"

                    else ->
                        "UNKNOWN"
                }

            /*
             * ----------------------------------------------------
             * APP NAME
             * ----------------------------------------------------
             */

            val appName =
                try {

                    packageManager
                        .getApplicationLabel(
                            packageManager
                                .getApplicationInfo(
                                    sbn.packageName,
                                    0
                                )
                        )
                        .toString()

                } catch (_: Exception) {

                    sbn.packageName
                }

            val snapshot =
                MediaSnapshot(
                    packageName =
                        sbn.packageName,
                    appName =
                        appName,
                    title =
                        title,
                    artist =
                        artist,
                    position =
                        position,
                    duration =
                        duration,
                    playing =
                        playing,
                    playbackState =
                        playbackState,
                    actions =
                        actions,
                    controller =
                        controller
                )

            val isPreferred =
                !preferredPackage.isNullOrBlank() &&
                        sbn.packageName ==
                        preferredPackage

            Log.d(
                TAG,
                "Media candidate: " +
                        "package=${snapshot.packageName}, " +
                        "preferred=$isPreferred, " +
                        "title=${snapshot.title}, " +
                        "artist=${snapshot.artist}, " +
                        "state=$stateDescription, " +
                        "position=${snapshot.position}, " +
                        "duration=${snapshot.duration}, " +
                        "token=${token != null}, " +
                        "actions=${snapshot.actions.size}"
            )

            /*
             * ----------------------------------------------------
             * PREFERRED PACKAGE
             * ----------------------------------------------------
             */

            if (isPreferred) {

                if (playing) {

                    if (
                        bestPreferredPlaying == null
                    ) {
                        bestPreferredPlaying =
                            snapshot
                    }

                } else if (paused) {

                    if (
                        bestPreferredPaused == null
                    ) {
                        bestPreferredPaused =
                            snapshot
                    }

                } else {

                    if (
                        bestPreferredUnknown == null
                    ) {
                        bestPreferredUnknown =
                            snapshot
                    }
                }
            }

            /*
             * ----------------------------------------------------
             * GENERAL CANDIDATES
             * ----------------------------------------------------
             */

            if (playing) {

                if (
                    bestPlaying == null
                ) {
                    bestPlaying = snapshot
                }

                continue
            }

            if (paused) {

                if (
                    bestPaused == null
                ) {
                    bestPaused = snapshot
                }

                continue
            }

            if (
                bestUnknown == null
            ) {
                bestUnknown = snapshot
            }
        }

        /*
         * Preferred package ALWAYS wins.
         *
         * Only if it no longer exists do we fall back to the
         * normal PLAYING -> PAUSED -> UNKNOWN selection.
         */
        return bestPreferredPlaying
            ?: bestPreferredPaused
            ?: bestPreferredUnknown
            ?: bestPlaying
            ?: bestPaused
            ?: bestUnknown
    }

    // ============================================================
    // MEDIA CONTROLLER
    // ============================================================

    fun findBestMediaController():
            MediaController? {

        return findBestMediaSnapshot()
            ?.controller
    }

    fun findBestMediaController(
        preferredPackage: String?
    ): MediaController? {

        return findBestMediaSnapshot(
            preferredPackage
        )?.controller
    }

    // ============================================================
    // MEDIA ACTION
    // ============================================================

    fun performCachedMediaAction(
        action: String
    ): Boolean {

        return performCachedMediaAction(
            action,
            null
        )
    }

    fun performCachedMediaAction(
        action: String,
        preferredPackage: String?
    ): Boolean {

        /*
         * Never use notification PendingIntents for Play/Pause
         * when a MediaController path is available.
         *
         * The stable toggle handles those.
         */
        if (
            action ==
            SuperIslandManager.ACTION_MEDIA_PLAY ||
            action ==
            SuperIslandManager.ACTION_MEDIA_PAUSE
        ) {

            return false
        }

        val snapshot =
            findBestMediaSnapshot(
                preferredPackage
            )
                ?: return false

        val key =
            actionToKey(action)

        val mediaAction =
            snapshot.actions.firstOrNull {
                it.key == key
            }
                ?: return false

        return try {

            mediaAction.pendingIntent.send()

            Log.d(
                TAG,
                "Sent ORIGINAL media PendingIntent: " +
                        "${snapshot.packageName} / $key"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Unable to send original media action: " +
                        key,
                e
            )

            false
        }
    }

    private fun actionToKey(
        action: String
    ): String {

        return when (action) {

            SuperIslandManager.ACTION_MEDIA_PLAY ->
                "play"

            SuperIslandManager.ACTION_MEDIA_PAUSE ->
                "pause"

            SuperIslandManager.ACTION_MEDIA_NEXT ->
                "next"

            SuperIslandManager.ACTION_MEDIA_PREVIOUS ->
                "previous"

            else ->
                action
        }
    }

    // ============================================================
    // REFRESH
    // ============================================================

    private fun notifyMediaSessionChanged() {

        try {

            android.os.Handler(
                android.os.Looper.getMainLooper()
            ).postDelayed(
                {

                    if (
                        SuperIslandManager
                            .isLiveUpdatesRunning()
                    ) {

                        SuperIslandManager
                            .refreshMediaNow(
                                applicationContext
                            )
                    }

                },
                250L
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to refresh media session",
                e
            )
        }
    }
}