package com.kuromify.kuromix.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kuromify.kuromix.R
import com.kuromify.kuromix.receiver.KuroMixCloseReceiver
import com.kuromify.kuromix.receiver.KuroMixReceiver
import com.topjohnwu.superuser.Shell
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
    private const val DOWNLOAD_NOTIFICATION_ID = 204
    private const val LIVE_NOTIFICATION_ID = 205
    private const val MIRROR_LIVE_NOTIFICATION_ID = 207

    private const val DOWNLOAD_BUSINESS_ID =
        "download"

    private const val MIRROR_LIVE_BUSINESS_ID =
        "mirror_live"

    private const val EXTRA_FOCUS_PARAM =
        "miui.focus.param"

    private const val EXTRA_FOCUS_ACTIONS =
        "miui.focus.actions"

    private const val HYPERISLAND_SETTING =
        "kuromix_hyperisland_hook"

    private const val SPLIT_SECOND_TIMEOUT_MS = 5000L

    // Kept for receiver compatibility. Not used internally anymore.
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

    private const val CLOSE_REQUEST_CODE_BASE = 0x4B55524F

    private val mainHandler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    @Volatile
    private var liveRunning = false

    private var currentLiveMode =
        LiveMode.CLOCK

    @Volatile
    private var currentDisplayMode: String = "always"

    private var splitTimeoutRunnable: Runnable? = null

    private var batteryReceiver:
            BroadcastReceiver? = null

    private var clockRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var networkRunnable: Runnable? = null
    private var temperatureRunnable: Runnable? = null

    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastNetworkSampleMs = 0L

    private var lastTemperatureC = Float.NaN
    private var lastThermalStatus =
        PowerManager.THERMAL_STATUS_NONE

    private enum class LiveMode {
        CHARGING,
        CLOCK,
        TIMER,
        NETWORK,
        TEMP
    }

    private data class FocusAction(
        val key: String,
        val title: String,
        val pendingIntent: PendingIntent,
        val iconRes: Int
    )

    private fun closeIcon(): Int {
        return android.R.drawable.ic_menu_close_clear_cancel
    }

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

    fun isHyperIslandHookEnabled(
        context: Context
    ): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                HYPERISLAND_SETTING,
                0
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun setHyperIslandHookEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val value =
            if (enabled) 1 else 0

        val result =
            Shell.cmd(
                "settings put global " +
                        "$HYPERISLAND_SETTING $value"
            ).exec()

        Log.d(
            TAG,
            "HyperIsland hook setting = " +
                    "$enabled, success=${result.isSuccess}"
        )

        if (!enabled) {
            stopLiveUpdates(context)
            stopDownloadTest(context)
            cancelMirrorIsland(context)
        }
    }

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

    /**
     * Ensure the notification channel exists. Call once at app startup so
     * the very first notify() does not race channel creation.
     */
    fun ensureChannel(context: Context) {
        createNotificationChannel(context)
    }

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

        applyDisplayModeTimeout(context)
    }

    fun switchLiveMode(
        context: Context,
        mode: String,
        displayMode: String = "always"
    ) {
        currentDisplayMode = displayMode

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

        startCurrentLiveMode(context)

        applyDisplayModeTimeout(context)
    }

    private fun startCurrentLiveMode(
        context: Context
    ) {

        when (currentLiveMode) {

            LiveMode.CHARGING ->
                startChargingUpdates(context)

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

        splitTimeoutRunnable?.let(
            mainHandler::removeCallbacks
        )
        splitTimeoutRunnable = null

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

        temperatureRunnable?.let(
            mainHandler::removeCallbacks
        )

        clockRunnable = null
        timerRunnable = null
        networkRunnable = null
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

    private fun applyDisplayModeTimeout(
        context: Context
    ) {

        splitTimeoutRunnable?.let(
            mainHandler::removeCallbacks
        )
        splitTimeoutRunnable = null

        if (currentLiveMode == LiveMode.TIMER) return

        if (currentDisplayMode != "split") return

        val runnable =
            Runnable {

                if (!liveRunning) return@Runnable

                try {

                    context.getSystemService(
                        NotificationManager::class.java
                    ).cancel(
                        LIVE_NOTIFICATION_ID
                    )

                } catch (_: Exception) {
                }
            }

        splitTimeoutRunnable = runnable

        mainHandler.postDelayed(
            runnable,
            SPLIT_SECOND_TIMEOUT_MS
        )
    }

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

    @Volatile
    private var timerDurationMs: Long =
        5L * 60L * 1000L

    @Volatile
    private var timerRemainingMs: Long =
        timerDurationMs

    @Volatile
    private var timerPaused = false

    private var timerLastTickMs = 0L

    fun setTimerDuration(
        minutes: Int,
        seconds: Int
    ) {
        val total =
            (minutes.coerceAtLeast(0) * 60L +
                    seconds.coerceAtLeast(0)) * 1000L

        timerDurationMs =
            if (total <= 0L) 60_000L else total

        timerRemainingMs = timerDurationMs
        timerPaused = false
        timerLastTickMs = SystemClock.elapsedRealtime()
    }

    fun startTimerUpdates(
        context: Context
    ) {

        stopLiveRunnablesOnly(context)

        if (timerRemainingMs <= 0L) {
            timerRemainingMs =
                timerDurationMs
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
                title = "Timer",
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
                            android.R.drawable.ic_media_play
                        } else {
                            android.R.drawable.ic_media_pause
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
            "Timer",
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
                timerDurationMs
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
            timerDurationMs

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

            applyDisplayModeTimeout(context)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to update live HyperIsland",
                e
            )
        }
    }

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

            val wantsFocusExtras =
                notificationId == LIVE_NOTIFICATION_ID ||
                        notificationId == DOWNLOAD_NOTIFICATION_ID ||
                        notificationId == MIRROR_LIVE_NOTIFICATION_ID

            if (
                wantsFocusExtras &&
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
                wantsFocusExtras &&
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

            if (
                wantsFocusExtras &&
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

            if (
                wantsFocusExtras &&
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

    private fun Intent.toFocusIntentUri(): String {

        return android.net.Uri.parse(
            toUri(
                Intent.URI_INTENT_SCHEME
            )
        ).toString()
    }

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

    // ─── Mirror notification (plain, legacy) ────────────────────

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

    // ─── Mirror island (HyperIsland card) ──────────────────────

    fun showMirrorIsland(
        context: Context,
        packageName: String
    ) {

        if (!isHyperIslandHookEnabled(context)) return

        try {

            createNotificationChannel(context)

            if (!HyperIslandNotification.isSupported(context)) {
                showMirrorNotification(context, packageName)
                return
            }

            val appLabel = try {
                context.packageManager
                    .getApplicationLabel(
                        context.packageManager
                            .getApplicationInfo(packageName, 0)
                    )
                    .toString()
            } catch (_: Exception) {
                packageName
            }

            val pictureKey =
                "kuromix_live_$MIRROR_LIVE_BUSINESS_ID"

            val picture =
                HyperPicture(
                    pictureKey,
                    context,
                    R.drawable.kuromify_logo_w
                )

            val builder =
                HyperIslandNotification
                    .Builder(
                        context,
                        MIRROR_LIVE_BUSINESS_ID,
                        "Rear Display"
                    )
                    .setSmallWindowTarget(
                        "${context.packageName}.MainActivity"
                    )
                    .addPicture(picture)
                    .setChatInfo(
                        title = appLabel,
                        content = "Mirroring on rear display",
                        pictureKey = pictureKey
                    )
                    .setSmallIsland(pictureKey)
                    .setBigIslandInfo(
                        left = ImageTextInfoLeft(
                            type = 1,
                            picInfo = PicInfo(type = 1, pic = pictureKey),
                            textInfo = TextInfo(title = appLabel)
                        ),
                        right = ImageTextInfoRight(
                            type = 2,
                            textInfo = TextInfo(title = "Mirroring")
                        )
                    )
                    .setEnableFloat(false)
                    .setShowNotification(true)

            val stopIntent =
                Intent(
                    context,
                    KuroMixReceiver::class.java
                ).apply {

                    action =
                        KuroMixReceiver.ACTION_STOP_MIRROR

                    putExtra("packageName", packageName)

                    addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND
                    )
                }

            val stopPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    9101,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            val actions = listOf(
                FocusAction(
                    key = "mirror_stop",
                    title = "Stop",
                    pendingIntent = stopPendingIntent,
                    iconRes = closeIcon()
                )
            )

            notifyHyperIsland(
                context = context,
                title = "Rear Display",
                notificationId = MIRROR_LIVE_NOTIFICATION_ID,
                builder = builder,
                focusActions = actions,
                includeClose = true
            )

            Log.d(TAG, "Mirror island posted for $packageName")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show mirror island", e)
        }
    }

    fun cancelMirrorIsland(
        context: Context
    ) {

        try {

            context.getSystemService(
                NotificationManager::class.java
            ).cancel(
                MIRROR_LIVE_NOTIFICATION_ID
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to cancel mirror island",
                e
            )
        }
    }

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
                                    android.R.drawable.ic_media_play
                                } else {
                                    android.R.drawable.ic_media_pause
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
                    _: InterruptedException
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
}