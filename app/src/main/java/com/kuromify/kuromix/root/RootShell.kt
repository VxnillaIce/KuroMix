package com.kuromify.kuromix.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thin wrapper around libsu for the handful of privileged operations KuroMix
 * needs. Every call here requires the app to have been granted root by
 * Magisk/KernelSU/APatch — libsu handles the su prompt + caching of the shell.
 */
object RootShell {

    data class Result(val ok: Boolean, val out: List<String>, val err: List<String>)
    data class TaskInfo(val taskId: Int, val displayId: Int)

    // ------------------------------------------------------------------
    // Mi Pay / Google Wallet replacement
    // ------------------------------------------------------------------

    /**
     * Property the LSPosed hook reads to decide whether to redirect Mi Pay.
     * RootShell.setReplaceMipayProp writes this; KuroMixHook reads it.
     */
    private const val PROP_REPLACE_MIPAY = "persist.kuromix.replace_mipay"

    /**
     * MIUI's Settings.System key for the double-click-power gesture.
     * Confirmed on device: value for Mi Pay is the string "mi_pay".
     */
    private const val SETTING_DOUBLE_CLICK_POWER = "double_click_power_key"

    /** MIUI's native value for "Mi Pay" on this device. */
    private const val MI_PAY_VALUE = "mi_pay"

    private const val PREFS_NAME = "kuromix_prefs"
    private const val PREF_ORIGINAL_POWER_KEY = "original_double_click_power_key"

    private fun run(cmd: String): Result {
        val r = Shell.cmd(cmd).exec()
        return Result(r.isSuccess, r.out, r.err)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRootAvailable(): Boolean {
        val shell = Shell.getShell()
        val isRoot = shell.isRoot

        val idResult = Shell.cmd("id").exec()
        val actuallyRoot = idResult.isSuccess && idResult.out.any { it.contains("uid=0") }

        android.util.Log.d(
            "RootShell",
            "[KUROMIX_LOG] isRootAvailable check: libsu_isRoot=$isRoot, actuallyRoot=$actuallyRoot"
        )
        return actuallyRoot
    }

    /** Returns true if the Xposed module is successfully hooked. Hooked by KuroMixHook. */
    fun isModuleActive(): Boolean = false

    /** Force-stops a package using root. */
    fun forceStopPackage(packageName: String): Result {
        return run("am force-stop $packageName")
    }

    /** Moves a specific task to a display. */
    fun moveTaskToDisplay(taskId: Int, displayId: Int): Result {
        val res = run("service call activity_task 50 i32 $taskId i32 $displayId")
        if (res.ok && (displayId != 0)) {
            run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
        }
        return res
    }

    @Suppress("unused")
    fun getTopTaskInfo(): TaskInfo? {
        val r = run("am stack list")
        if (r.ok) {
            var currentDisplayId = -1
            for (line in r.out) {
                val displayMatch = Regex("""displayId=(\d+)""").find(line)
                displayMatch?.let {
                    currentDisplayId = it.groupValues[1].toInt()
                }

                if (line.contains("visible=true")) {
                    val taskIdMatch = Regex("""taskId=(\d+)""").find(line)
                    if ((taskIdMatch != null) && (currentDisplayId != -1)) {
                        return TaskInfo(taskIdMatch.groupValues[1].toInt(), currentDisplayId)
                    }
                }
            }
        }
        return null
    }

    fun getTopTaskId(ignorePackages: List<String> = emptyList()): Int? {
        android.util.Log.d("RootShell", "[KUROMIX_LOG] getTopTaskId: ignorePackages=$ignorePackages")

        val r1 = run("dumpsys activity activities")
        if (r1.ok) {
            var topResumedPkg: String? = null
            var topResumedTaskId: Int? = null

            for (line in r1.out) {
                if (line.contains("topResumedActivity=")) {
                    val match = Regex("""ActivityRecord\{.* ([\w.]+)/.* t(\d+)\}""").find(line)
                    if (match != null) {
                        topResumedPkg = match.groupValues[1]
                        topResumedTaskId = match.groupValues[2].toInt()
                        break
                    }
                }
            }

            if (topResumedPkg != null && !ignorePackages.contains(topResumedPkg)) {
                android.util.Log.d("RootShell", "[KUROMIX_LOG] Found via topResumedActivity: $topResumedTaskId ($topResumedPkg)")
                return topResumedTaskId
            }

            var currentDisplay = -1
            for (line in r1.out) {
                if (line.contains("Display #")) {
                    val m = Regex("""Display #(\d+)""").find(line)
                    currentDisplay = m?.groupValues?.get(1)?.toInt() ?: -1
                }

                if (currentDisplay == 0) {
                    val arMatch = Regex("""\* ActivityRecord\{.* ([^/ ]+)/.* t(\d+)\}""").find(line)
                    if (arMatch != null) {
                        val pkg = arMatch.groupValues[1]
                        val taskId = arMatch.groupValues[2].toInt()
                        if (!ignorePackages.contains(pkg)) {
                            android.util.Log.d("RootShell", "[KUROMIX_LOG] Found via dumpsys look-behind: $taskId ($pkg)")
                            return taskId
                        }
                    }
                }
            }
        }

        val r2 = run("am stack list")
        if (r2.ok) {
            var currentDisplayId = -1
            for (line in r2.out) {
                val displayMatch = Regex("""displayId=(\d+)""").find(line)
                if (displayMatch != null) currentDisplayId = displayMatch.groupValues[1].toInt()

                if (currentDisplayId == 0 && line.contains("taskId=")) {
                    val taskMatch = Regex("""taskId=(\d+): ([^/ ]+)""").find(line)
                    if (taskMatch != null) {
                        val taskId = taskMatch.groupValues[1].toInt()
                        val pkg = taskMatch.groupValues[2]
                        if (!ignorePackages.contains(pkg)) {
                            android.util.Log.d("RootShell", "[KUROMIX_LOG] Found via am stack list: $taskId ($pkg)")
                            return taskId
                        }
                    }
                }
            }
        }

        return null
    }

    @Suppress("unused")
    fun launchOnDisplay(packageName: String, componentName: String, displayId: Int): Result {
        val taskId = getTaskIdForPackage(packageName)
        return if (taskId != null) {
            val res = run("service call activity_task 50 i32 $taskId i32 $displayId")
            if (res.ok) run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
            res
        } else {
            run("am start -n $componentName --display $displayId")
        }
    }

    fun getPackageNameForTask(taskId: Int): String? {
        val r = run("am stack list")
        if (r.ok) {
            for (line in r.out) {
                if (line.contains("taskId=$taskId:")) {
                    val match = Regex("""taskId=$taskId: ([\w.]+)""").find(line)
                    if (match != null) return match.groupValues[1]
                }
            }
        }
        val r2 = run("dumpsys activity tasks")
        if (r2.ok) {
            for (line in r2.out) {
                if (line.contains("taskId=$taskId:")) {
                    val match = Regex("""taskId=$taskId: ([\w.]+)""").find(line)
                    if (match != null) return match.groupValues[1]
                }
            }
        }
        return null
    }

    fun getTaskIdForPackage(packageName: String): Int? {
        val r = run("am stack list")
        if (r.ok) {
            for (line in r.out) {
                if (line.contains(packageName) && line.contains("taskId=")) {
                    val match = Regex("""taskId=(\d+)""").find(line)
                    if (match != null) return match.groupValues[1].toInt()
                }
            }
        }
        return null
    }

    fun getTaskIdOnDisplay(displayId: Int): Int? {
        val r = run("am stack list")
        if (r.ok && r.out.isNotEmpty()) {
            var currentDisplayId = -1
            for (line in r.out) {
                if (line.contains("displayId=")) {
                    val match = Regex("displayId=(\\d+)").find(line)
                    match?.let {
                        currentDisplayId = it.groupValues[1].toInt()
                    } ?: run {
                        currentDisplayId = -1
                    }
                }
                if (currentDisplayId == displayId && line.contains("taskId=") && line.contains("visible=true")) {
                    val match = Regex("taskId=(\\d+)").find(line)
                    val taskId = match?.groupValues?.get(1)?.toInt()
                    if (taskId != null) return taskId
                }
            }
        }
        return null
    }

    fun getTopPackageOnDisplay(displayId: Int): String? {
        val r = run("am stack list")
        if (r.ok && r.out.isNotEmpty()) {
            var currentDisplayId = -1
            for (line in r.out) {
                if (line.contains("displayId=")) {
                    val match = Regex("displayId=(\\d+)").find(line)
                    match?.let {
                        currentDisplayId = it.groupValues[1].toInt()
                    } ?: run {
                        currentDisplayId = -1
                    }
                }
                if (currentDisplayId == displayId && line.contains("taskId=") && line.contains("visible=true")) {
                    val pkgMatch = Regex("""taskId=\d+: ([\w.]+)""").find(line)
                    if (pkgMatch != null) return pkgMatch.groupValues[1]
                }
            }
        }
        return null
    }

    @Suppress("unused")
    suspend fun waitForTaskId(packageName: String, timeoutMs: Long = 3000): Int? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val taskId = getTaskIdForPackage(packageName)
            if (taskId != null) return taskId
            kotlinx.coroutines.delay(200.milliseconds)
        }
        return null
    }

    @Suppress("unused")
    fun getTopActivity(): String? {
        val r = run("dumpsys activity activities | grep -m1 'topResumedActivity'")
        val line = r.out.firstOrNull() ?: return null
        val regex = Regex("""([\w.]+/[\w.$]+)""")
        return regex.find(line)?.value
    }

    fun setDisplayDpi(displayId: Int, dpi: Int?): Result =
        if (dpi == null) run("wm density reset -d $displayId")
        else run("wm density $dpi -d $displayId")

    @Suppress("unused")
    fun setDisplayRotation(displayId: Int, rotationDeg: Int): Result {
        val surfaceRotation = when (rotationDeg) {
            90 -> 1
            180 -> 2
            270 -> 3
            else -> 0
        }
        return run("wm user-rotation -d $displayId lock $surfaceRotation")
    }

    @Suppress("unused")
    fun screenshotDisplay(displayId: Int, outPath: String): Result =
        run("screencap -d $displayId $outPath")

    fun getMarketName(): String {
        val r = Shell.cmd("getprop ro.product.marketname").exec()
        return if (r.isSuccess && r.out.isNotEmpty()) r.out[0] else android.os.Build.MODEL
    }

    fun getHyperOSVersion(): String {
        val r = Shell.cmd("getprop ro.mi.os.version.incremental").exec()
        if (r.isSuccess && r.out.isNotEmpty()) return r.out[0]
        val r2 = Shell.cmd("getprop ro.miui.ui.version.name").exec()
        return if (r2.isSuccess && r2.out.isNotEmpty()) r2.out[0] else "Unknown"
    }

    fun setDisplayArea(displayId: Int, left: Int, top: Int, right: Int, bottom: Int): Result =
        run("wm folded-area -d $displayId $left,$top,$right,$bottom")

    fun getDisplaySize(displayId: Int): Pair<Int, Int>? {
        val r = run("wm size -d $displayId")
        if (r.ok && r.out.isNotEmpty()) {
            val line = r.out.last()
            val match = Regex("(\\d+)x(\\d+)").find(line)
            if (match != null) {
                return Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
        }
        return null
    }

    fun applyDisplayOffset(displayId: Int, offset: Int): Result {
        if (offset == 0) return resetDisplayArea(displayId)
        val size = getDisplaySize(displayId)
            ?: return Result(ok = false, out = emptyList(), err = listOf("Could not determine display size"))
        val (width, height) = size
        return setDisplayArea(displayId, offset, 0, width, height)
    }

    fun resetDisplayArea(displayId: Int): Result =
        run("wm folded-area -d $displayId reset")

    fun setKeepAwakeProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop persist.kuromix.keep_awake $valStr")
    }

    fun setAntiKillProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop persist.kuromix.anti_kill $valStr")
    }

    /** Forcibly stops the system's sub-screen launcher to prevent interference. */
    fun suppressSubScreenLauncher(): Result {
        return run("am force-stop com.xiaomi.subscreencenter")
    }

    fun disableSubScreenDoubleTap(): Result {
        return run("settings put system subscreen_double_tap_wake 0")
    }

    fun setSubScreenTimeout(seconds: Int): Result {
        val ms = seconds * 1000
        return run("settings put system subscreen_display_time $ms")
    }

    fun killProcess(pkg: String): Result {
        val pidResult = run("pidof $pkg")
        val pid = pidResult.out.firstOrNull()?.trim()?.split(" ")?.firstOrNull()

        if (pid.isNullOrBlank()) {
            android.util.Log.d(
                "RootShell",
                "[KUROMIX_LOG] killProcess: pidof found no PID for $pkg, falling back to ps"
            )
            val psResult = run("ps -A -o PID,NAME")
            val line = psResult.out.firstOrNull { it.trim().endsWith(pkg) }
            val fallbackPid = line?.trim()?.split(Regex("\\s+"))?.firstOrNull()

            if (fallbackPid.isNullOrBlank()) {
                android.util.Log.d(
                    "RootShell",
                    "[KUROMIX_LOG] killProcess: no PID found for $pkg via ps either. ps out=${psResult.out}"
                )
                return Result(
                    ok = false,
                    out = emptyList(),
                    err = listOf("No running process found for $pkg")
                )
            }
            val killRes = run("kill -9 $fallbackPid")
            android.util.Log.d(
                "RootShell",
                "[KUROMIX_LOG] killProcess($pkg) via ps pid=$fallbackPid -> ok=${killRes.ok} err=${killRes.err}"
            )
            return killRes
        }

        val killRes = run("kill -9 $pid")
        android.util.Log.d(
            "RootShell",
            "[KUROMIX_LOG] killProcess($pkg) via pidof pid=$pid -> ok=${killRes.ok} err=${killRes.err}"
        )
        return killRes
    }

    // ------------------------------------------------------------------
    // Mi Pay -> Google Wallet toggle
    // ------------------------------------------------------------------

    /**
     * Enables or disables the Mi Pay -> Google Wallet redirect.
     *
     * IMPORTANT: This does NOT touch Settings.System.double_click_power_key.
     * MIUI keeps its native value ("mi_pay") and launches Mi Pay normally;
     * KuroMixHook intercepts the resulting activity / power-key event and
     * redirects to Google Wallet only when this property is "1".
     *
     * Because we never write the setting, disabling is instant and there is
     * no state to restore — the device can never end up stuck on "none".
     */
    fun setReplaceMipayProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop $PROP_REPLACE_MIPAY $valStr")
        android.util.Log.d(
            "RootShell",
            "[KUROMIX_LOG] setReplaceMipayProp($enabled) -> $PROP_REPLACE_MIPAY=$valStr"
        )
    }

    /**
     * Recovery helper: forces double_click_power_key back to Mi Pay.
     * Useful if an older build of KuroMix left the setting on a value MIUI
     * doesn't recognise (e.g. "none" or "launch_mi_pay").
     */
    fun restoreMiPayPowerKey() {
        run("settings put system $SETTING_DOUBLE_CLICK_POWER $MI_PAY_VALUE")
        android.util.Log.d("RootShell", "[KUROMIX_LOG] Restored power key to $MI_PAY_VALUE")
    }

    /** Diagnostic: read the current value. */
    fun getDoubleClickPowerKey(): String? =
        run("settings get system $SETTING_DOUBLE_CLICK_POWER")
            .out
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "null" }
}