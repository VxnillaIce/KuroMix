package com.kuromify.kuromix.root

import com.topjohnwu.superuser.Shell
import java.io.DataOutputStream

/**
 * Thin wrapper around libsu for the handful of privileged operations KuroMix
 * needs. Every call here requires the app to have been granted root by
 * Magisk/KernelSU/APatch — libsu handles the su prompt + caching of the shell.
 */
object RootShell {

    data class Result(val ok: Boolean, val out: List<String>, val err: List<String>)
    data class TaskInfo(val taskId: Int, val displayId: Int)

    private fun run(cmd: String): Result {
        val r = Shell.cmd(cmd).exec()
        return Result(r.isSuccess, r.out, r.err)
    }

    fun isRootAvailable(): Boolean {
        // Force get a shell, potentially triggering a prompt if no cached shell exists
        val shell = Shell.getShell()
        val isRoot = shell.isRoot
        
        // Final sanity check: try running 'id'
        val idResult = Shell.cmd("id").exec()
        val actuallyRoot = idResult.isSuccess && idResult.out.any { it.contains("uid=0") }
        
        android.util.Log.d("RootShell", "[KUROMIX_LOG] isRootAvailable check: libsu_isRoot=$isRoot, actuallyRoot=$actuallyRoot")
        return actuallyRoot
    }

    /** Returns true if the Xposed module is successfully hooked. Hooked by KuroMixHook. */
    fun isModuleActive(): Boolean = false

    /** Force-stops a package using root. */
    fun forceStopPackage(packageName: String): Result {
        return run("am force-stop $packageName")
    }

    fun restartSystemUi(): Result {
        return runCatching {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()

            DataOutputStream(process.outputStream).use { output ->
                output.writeBytes("PIDS=\$(pidof com.android.systemui)\n")
                output.writeBytes("if [ -z \"\$PIDS\" ]; then exit 1; fi\n")
                output.writeBytes("kill -9 \$PIDS\n")
                output.writeBytes("exit\n")
                output.flush()
            }

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText() }

            val exitCode = process.waitFor()

            Result(
                ok = exitCode == 0,
                out = output.lines().filter { it.isNotBlank() },
                err = emptyList()
            )
        }.getOrElse {
            Result(
                ok = false,
                out = emptyList(),
                err = listOf(it.message ?: it.javaClass.simpleName)
            )
        }
    }

    /** Moves a specific task to a display. */
    fun moveTaskToDisplay(taskId: Int, displayId: Int): Result {
        val res = run("service call activity_task 50 i32 $taskId i32 $displayId")
        if (res.ok && (displayId != 0)) {
            // Wake display if moving to non-zero display
            run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
        }
        return res
    }

    /** Moves an app's current top task onto another display (e.g. the rear/secondary display). */
    fun moveCurrentTaskToDisplay(displayId: Int): Result {
        val taskId = getTopTaskId() ?: return Result(ok = false, out = emptyList(), err = listOf("Could identify foreground taskId"))
        // Transaction code 50 is common for moveTaskToDisplay in HyperOS/Android 16
        val res = run("service call activity_task 50 i32 $taskId i32 $displayId")
        if (res.ok) {
            // Wake display
            run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
        }
        return res
    }

    /** Parses the current top task ID and its display from activity manager. */
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

    /** Parses the current top task ID from activity manager, ignoring specified packages.
     *  Highly robust implementation designed for HyperOS/Android 13+ multi-display. */
    fun getTopTaskId(ignorePackages: List<String> = emptyList()): Int? {
        android.util.Log.d("RootShell", "[KUROMIX_LOG] getTopTaskId: ignorePackages=$ignorePackages")
        
        // Strategy 1: dumpsys activity activities (Most reliable for top resumed)
        val r1 = run("dumpsys activity activities")
        if (r1.ok) {
            // Find the top resumed activity first
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

            // Strategy 2: Scan all activities on Display 0 in order (Look behind)
            var currentDisplay = -1
            for (line in r1.out) {
                if (line.contains("Display #")) {
                    val m = Regex("""Display #(\d+)""").find(line)
                    currentDisplay = m?.groupValues?.get(1)?.toInt() ?: -1
                }
                
                if (currentDisplay == 0) {
                    // Extract activity record. Note: we use [^/ ]+ to handle hyphens in package names.
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

        // Strategy 3: am stack list fallback
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

    /** Simpler, more reliable path for specific apps: relaunch the app's launcher activity directly onto a display id.
     *  If the app is already running, we try to move its task instead of starting a new one. */
    @Suppress("unused")
    fun launchOnDisplay(packageName: String, componentName: String, displayId: Int): Result {
        val taskId = getTaskIdForPackage(packageName)
        return if (taskId != null) {
            // App is running, move its task
            val res = run("service call activity_task 50 i32 $taskId i32 $displayId")
            if (res.ok) run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
            res
        } else {
            // App not running, start it fresh on the target display
            run("am start -n $componentName --display $displayId")
        }
    }

    /** Finds the packageName for a specific taskId. */
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
        // Fallback to dumpsys activity tasks
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

    /** Finds the taskId for a specific package if it has a visible or background task. */
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

    /** Finds the ID of any visible task currently residing on a specific display. */
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

    /** Finds the package name of the top visible task on a specific display. */
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

    /** Polls for a taskId for a package until it appears or timeout is reached. */
    @Suppress("unused")
    suspend fun waitForTaskId(packageName: String, timeoutMs: Long = 3000): Int? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val taskId = getTaskIdForPackage(packageName)
            if (taskId != null) return taskId
            kotlinx.coroutines.delay(200)
        }
        return null
    }

    /** Pulls the currently focused/top activity's component name via dumpsys. */
    @Suppress("unused")
    fun getTopActivity(): String? {
        val r = run("dumpsys activity activities | grep -m1 'topResumedActivity'")
        val line = r.out.firstOrNull() ?: return null
        // Example: topResumedActivity=ActivityRecord{... u0 com.foo/.MainActivity t123}
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

    /** Forces the rear display to wake or stay on. */
    fun wakeRear(): Result {
        return run("am broadcast -a com.xiaomi.action.REAR_DISPLAY_SWITCH --ez state true")
    }

    /** Fetches the marketing name of the device. */
    fun getMarketName(): String {
        val r = Shell.cmd("getprop ro.product.marketname").exec()
        return if (r.isSuccess && r.out.isNotEmpty()) r.out[0] else android.os.Build.MODEL
    }

    /** Fetches the HyperOS version string from system properties. */
    fun getHyperOSVersion(): String {
        val r = Shell.cmd("getprop ro.mi.os.version.incremental").exec()
        if (r.isSuccess && r.out.isNotEmpty()) return r.out[0]
        val r2 = Shell.cmd("getprop ro.miui.ui.version.name").exec()
        return if (r2.isSuccess && r2.out.isNotEmpty()) r2.out[0] else "Unknown"
    }

    /** Sets the logical area of the display. Useful for avoiding notches/lenses.
     *  Format: [LEFT,TOP,RIGHT,BOTTOM] */
    fun setDisplayArea(displayId: Int, left: Int, top: Int, right: Int, bottom: Int): Result =
        run("wm folded-area -d $displayId $left,$top,$right,$bottom")

    /** Fetches the size of a specific display. Returns Pair(width, height). */
    fun getDisplaySize(displayId: Int): Pair<Int, Int>? {
        val r = run("wm size -d $displayId")
        if (r.ok && r.out.isNotEmpty()) {
            val line = r.out.last() // Use last line to handle overrides correctly
            val match = Regex("(\\d+)x(\\d+)").find(line)
            if (match != null) {
                return Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
        }
        return null
    }

    /** Applies a horizontal offset to a display while keeping its full bounds valid. */
    fun applyDisplayOffset(displayId: Int, offset: Int): Result {
        if (offset == 0) return resetDisplayArea(displayId)
        val size = getDisplaySize(displayId) ?: return Result(ok = false, out = emptyList(), err = listOf("Could not determine display size"))
        val (width, height) = size
        // We set the right and bottom bounds to the actual display size to avoid black screen.
        // Content will be pushed from the left by 'offset'.
        return setDisplayArea(displayId, offset, 0, width, height)
    }

    /** Resets the display area to full screen. */
    fun resetDisplayArea(displayId: Int): Result =
        run("wm folded-area -d $displayId reset")

    /** Sets a system property to signal the LSPosed hook. */
    fun setKeepAwakeProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop persist.kuromix.keep_awake $valStr")
        // Also force a refresh of the hook by toggling a non-critical property if needed
        // but setprop usually triggers a reread if the hook is watching or called per-frame
    }

    /** Sets a system property to signal the LSPosed anti-kill hook. */
    fun setAntiKillProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop persist.kuromix.anti_kill $valStr")
    }

    /** Sets a system property to signal the LSPosed Mi Pay replacement hook. */
    fun setReplaceMipayProp(enabled: Boolean) {
        val valStr = if (enabled) "1" else "0"
        run("setprop persist.kuromix.replace_mipay $valStr")
    }

    /** Forcibly stops the system's sub-screen launcher to prevent interference. */
    fun suppressSubScreenLauncher(): Result {
        return run("am force-stop com.xiaomi.subscreencenter")
    }

    /** Disables the double-tap to sleep/wake on subscreen. */
    fun disableSubScreenDoubleTap(): Result {
        return run("settings put system subscreen_double_tap_wake 0")
    }

    /** Sets the subscreen timeout. 0 usually means never or system default.
     *  We use a very large value to simulate 'Never'. */
    fun setSubScreenTimeout(seconds: Int): Result {
        val ms = seconds * 1000
        return run("settings put system subscreen_display_time $ms")
    }
}
