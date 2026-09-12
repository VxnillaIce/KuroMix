package com.kuromify.kuromix.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppRepository {

    /** Any app with a launcher entry — this is what makes KuroMix "global"
     *  instead of a hardcoded whitelist like the stock rear-screen switcher. */
    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = activityInfo.packageName,
                    componentName = "${activityInfo.packageName}/${activityInfo.name}",
                    icon = null, // Optimized: Load icons lazily in the UI
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
