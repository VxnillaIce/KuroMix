package com.kuromify.kuromix.hook

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.kuromify.kuromix.root.RootShell

class KuroMixHook : IXposedHookLoadPackage {

    companion object {

        private const val TAG = "KuroMixHook"
        private const val LOG = "[KUROMIX_HOOK]"

        // ------------------------------------------------------------
        // Rear display
        // ------------------------------------------------------------

        private const val REAR_DISPLAY_ID = 1

        // ------------------------------------------------------------
        // Google Wallet
        // ------------------------------------------------------------

        private const val GOOGLE_WALLET_PKG =
            "com.google.android.apps.walletnfcrel"

        private const val SETTING_KEY =
            "double_click_power_key"

        private const val MI_PAY_VALUE =
            "launch_mi_pay"

        private const val GOOGLE_WALLET_VALUE =
            "google_wallet"

        // ------------------------------------------------------------
        // KuroMix
        // ------------------------------------------------------------

        private const val KUROMIX_PKG =
            "com.kuromify.kuromix"

        // ------------------------------------------------------------
        // Mi Pay / NFC packages
        // ------------------------------------------------------------

        private val MI_PAY_STRINGS = arrayOf(
            "Mi Pay",
            "MiPay",
            "小米钱包",
            "小米支付",
            "Mi Pay快捷支付"
        )

        private val TARGET_PKGS = setOf(
            "com.miui.tsmclient",
            "com.unionpay.tsmservice.mi",
            "com.miui.nextpay",
            "com.android.nfc"
        )
    }

    // =================================================================
    // Generic safe hook helper
    // =================================================================

    private fun tryHook(
        name: String,
        block: () -> Unit
    ) {
        try {
            block()

            XposedBridge.log(
                "$TAG: $LOG Hook installed: $name"
            )
        } catch (t: Throwable) {
            XposedBridge.log(
                "$TAG: $LOG Failed to hook $name: " +
                        "${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }

    // =================================================================
    // Load package
    // =================================================================

    override fun handleLoadPackage(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        when (lpparam.packageName) {

            // ---------------------------------------------------------
            // Android framework / system_server
            // ---------------------------------------------------------

            "android" -> {
                hookSystemServer(lpparam)
            }

            // ---------------------------------------------------------
            // SystemUI / MIUI notification stack
            // ---------------------------------------------------------

            "com.android.systemui",
            "com.miui.notification",
            "com.miui.securitycenter" -> {
                hookSystemUI(lpparam)
            }

            // ---------------------------------------------------------
            // Mi Input / Settings / NFC
            // ---------------------------------------------------------

            "com.miui.miinput",
            "com.miui.securitycore",
            "com.android.settings",
            "com.android.nfc" -> {
                hookMiInput(lpparam)
                hookSettingsUI(lpparam)
            }

            // ---------------------------------------------------------
            // Xiaomi rear display service
            // ---------------------------------------------------------

            "com.xiaomi.subscreencenter" -> {
                hookSubScreenCenter(lpparam)
            }

            // ---------------------------------------------------------
            // Mi Pay / payment services
            // ---------------------------------------------------------

            in TARGET_PKGS -> {
                hookMiPayProcesses(lpparam)
                hookSettingsUI(lpparam)
            }

            // ---------------------------------------------------------
            // KuroMix itself
            // ---------------------------------------------------------

            KUROMIX_PKG -> {
                hookSelf(lpparam)
            }
        }
    }

    // =================================================================
    // KuroMix self hooks
    // =================================================================

    private fun hookSelf(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedBridge.log(
            "$TAG: $LOG KuroMix process loaded"
        )

        tryHook("RootShell.isModuleActive") {
            XposedHelpers.findAndHookMethod(
                RootShell::class.java,
                "isModuleActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        param.result = true
                    }
                }
            )
        }
    }

    // =================================================================
    // System property helper
    // =================================================================

    private fun getSysProp(
        key: String
    ): Boolean {
        return try {
            val clazz = Class.forName(
                "android.os.SystemProperties"
            )

            val method = clazz.getMethod(
                "get",
                String::class.java,
                String::class.java
            )

            method.invoke(
                null,
                key,
                "0"
            ) == "1"

        } catch (_: Throwable) {
            false
        }
    }

    // =================================================================
    // Mi Pay process redirection
    // =================================================================

    private fun hookMiPayProcesses(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        tryHook("MiPay Activity.onResume") {

            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {

                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        val activity =
                            param.thisObject as? Activity
                                ?: return

                        val context =
                            activity.applicationContext

                        val selected =
                            try {
                                Settings.System.getString(
                                    context.contentResolver,
                                    SETTING_KEY
                                )
                            } catch (_: Throwable) {
                                null
                            }

                        if (
                            selected == MI_PAY_VALUE ||
                            selected == "mi_pay"
                        ) {
                            XposedBridge.log(
                                "$TAG: $LOG " +
                                        "Redirecting Mi Pay -> Google Wallet"
                            )

                            launchGoogleWallet(activity)
                        }
                    }
                }
            )
        }
    }

    // =================================================================
    // Launch Google Wallet
    // =================================================================

    private fun launchGoogleWallet(
        context: Context
    ) {
        try {
            val intent =
                context.packageManager
                    .getLaunchIntentForPackage(
                        GOOGLE_WALLET_PKG
                    )

            if (intent == null) {
                XposedBridge.log(
                    "$TAG: $LOG Google Wallet launch intent not found"
                )
                return
            }

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            context.startActivity(intent)

        } catch (t: Throwable) {
            XposedBridge.log(
                "$TAG: $LOG Failed launching Google Wallet: " +
                        "${t.message}"
            )
        }
    }

    // =================================================================
    // Dynamic Island / Focus notification whitelist
    //
    // Based on XiaomiHelper IslandWhitelist:
    //
    // SignatureChecker.checkSignatures(String) -> true
    // NotificationSettingsManager.canShowFocus() -> true
    // NotificationSettingsManager.canCustomFocus() -> true
    // NotificationSettingsManager.mediaIslandSupportMiniWindow() -> true
    //
    // IMPORTANT:
    // canShowFocus / canCustomFocus / mediaIslandSupportMiniWindow
    // intentionally use XposedBridge.hookAllMethods() because Xiaomi
    // can change their parameter signatures between HyperOS builds.
    // =================================================================

    private fun hookDynamicIslandWhitelist(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedBridge.log(
            "$TAG: $LOG Installing Dynamic Island whitelist hooks"
        )

        // -------------------------------------------------------------
        // SignatureChecker.checkSignatures(String)
        // -------------------------------------------------------------

        tryHook(
            "DynamicIsland.SignatureChecker.checkSignatures"
        ) {
            val checkerClass =
                XposedHelpers.findClass(
                    "miui.systemui.notification.focus.SignatureChecker",
                    lpparam.classLoader
                )

            XposedHelpers.findAndHookMethod(
                checkerClass,
                "checkSignatures",
                String::class.java,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        XposedBridge.log(
                            "$TAG: $LOG " +
                                    "SignatureChecker.checkSignatures(" +
                                    "${param.args.getOrNull(0)}) -> true"
                        )

                        param.result = true
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // NotificationSettingsManager
        // -------------------------------------------------------------

        tryHook(
            "DynamicIsland.NotificationSettingsManager.canShowFocus"
        ) {
            val managerClass =
                XposedHelpers.findClass(
                    "miui.systemui.notification.NotificationSettingsManager",
                    lpparam.classLoader
                )

            XposedBridge.hookAllMethods(
                managerClass,
                "canShowFocus",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        XposedBridge.log(
                            "$TAG: $LOG " +
                                    "NotificationSettingsManager." +
                                    "canShowFocus() -> true"
                        )

                        param.result = true
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // canCustomFocus
        // -------------------------------------------------------------

        tryHook(
            "DynamicIsland.NotificationSettingsManager.canCustomFocus"
        ) {
            val managerClass =
                XposedHelpers.findClass(
                    "miui.systemui.notification.NotificationSettingsManager",
                    lpparam.classLoader
                )

            XposedBridge.hookAllMethods(
                managerClass,
                "canCustomFocus",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        XposedBridge.log(
                            "$TAG: $LOG " +
                                    "NotificationSettingsManager." +
                                    "canCustomFocus() -> true"
                        )

                        param.result = true
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Media Island mini window
        // -------------------------------------------------------------

        tryHook(
            "DynamicIsland.NotificationSettingsManager.mediaIslandSupportMiniWindow"
        ) {
            val managerClass =
                XposedHelpers.findClass(
                    "miui.systemui.notification.NotificationSettingsManager",
                    lpparam.classLoader
                )

            XposedBridge.hookAllMethods(
                managerClass,
                "mediaIslandSupportMiniWindow",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        XposedBridge.log(
                            "$TAG: $LOG " +
                                    "NotificationSettingsManager." +
                                    "mediaIslandSupportMiniWindow() -> true"
                        )

                        param.result = true
                    }
                }
            )
        }
    }

    // =================================================================
    // NotificationFilterHelper
    // =================================================================

    private fun hookNotificationFilterHelper(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        tryHook("NotificationFilterHelper diagnostics") {

            val clazz =
                XposedHelpers.findClass(
                    "miui.systemui.notification.NotificationFilterHelper",
                    lpparam.classLoader
                )

            dumpClassMethods(
                clazz,
                "NotificationFilterHelper"
            )

            // ---------------------------------------------------------
            // isImportantNotification(Context, String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isImportantNotification",
                    Context::class.java,
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(1)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "isImportantNotification -> true"
                                )
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isImportantNotification(Context, String, Notification)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isImportantNotification",
                    Context::class.java,
                    String::class.java,
                    Notification::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(1)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "isImportantNotification(Context,String,Notification) -> true"
                                )
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isAllowedShowFocus(Context, String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isAllowedShowFocus",
                    Context::class.java,
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(1)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "isAllowedShowFocus -> true"
                                )
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isSupportFocus(String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isSupportFocus",
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(0)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "isSupportFocus -> true"
                                )
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isSystemApp(String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isSystemApp",
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(0)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isAllowedShowResidentNotification(Context, String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isAllowedShowResidentNotification",
                    Context::class.java,
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(1)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "isAllowedShowResidentNotification -> true"
                                )
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // isSupportResidentNotification(String)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "isSupportResidentNotification",
                    String::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val pkg =
                                param.args
                                    .getOrNull(0)
                                    ?.toString()

                            if (pkg == KUROMIX_PKG) {
                                param.result = true
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }
    }

    // =================================================================
    // Dump methods
    // =================================================================

    private fun dumpClassMethods(
        clazz: Class<*>,
        label: String
    ) {
        try {
            XposedBridge.log(
                "$TAG: $LOG ===== $label ====="
            )

            clazz.declaredMethods
                .sortedBy { it.name }
                .forEach { method ->

                    XposedBridge.log(
                        "$TAG: $LOG $label -> " +
                                method.toGenericString()
                    )
                }

            XposedBridge.log(
                "$TAG: $LOG ===== END $label ====="
            )

        } catch (t: Throwable) {
            XposedBridge.log(
                "$TAG: $LOG Failed dumping $label: " +
                        "${t.message}"
            )
        }
    }

    // =================================================================
    // FocusNotificationManager
    // =================================================================

    private fun hookFocusNotificationManager(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        tryHook(
            "FocusNotificationManager.isFocusNotificationAllowed"
        ) {

            val clazz =
                XposedHelpers.findClass(
                    "com.miui.systemui.notification.FocusNotificationManager",
                    lpparam.classLoader
                )

            XposedHelpers.findAndHookMethod(
                clazz,
                "isFocusNotificationAllowed",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        val pkg =
                            param.args
                                .getOrNull(0)
                                ?.toString()

                        if (pkg == KUROMIX_PKG) {
                            XposedBridge.log(
                                "$TAG: $LOG " +
                                        "FocusNotificationManager -> true"
                            )

                            param.result = true
                        }
                    }
                }
            )
        }
    }

    // =================================================================
    // Power key double click
    // =================================================================

    private fun hookPowerKeyDoubleClick(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        tryHook(
            "MiuiPhoneWindowManager.powerPress"
        ) {

            val clazz =
                XposedHelpers.findClass(
                    "com.android.server.policy.MiuiPhoneWindowManager",
                    lpparam.classLoader
                )

            XposedHelpers.findAndHookMethod(
                clazz,
                "powerPress",
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        val count =
                            (param.args
                                .getOrNull(2) as? Int)
                                ?: return

                        if (count != 2) {
                            return
                        }

                        val context =
                            try {
                                XposedHelpers.getObjectField(
                                    param.thisObject,
                                    "mContext"
                                ) as? Context
                            } catch (_: Throwable) {
                                null
                            } ?: return

                        val selected =
                            try {
                                Settings.System.getString(
                                    context.contentResolver,
                                    SETTING_KEY
                                )
                            } catch (_: Throwable) {
                                null
                            }

                        if (
                            selected == GOOGLE_WALLET_VALUE
                        ) {
                            XposedBridge.log(
                                "$TAG: $LOG " +
                                        "Power double-click -> Google Wallet"
                            )

                            launchGoogleWallet(context)
                        }
                    }
                }
            )
        }
    }

    // =================================================================
    // Settings UI
    // =================================================================

    private fun hookSettingsUI(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        // Keep notification diagnostics available in Settings /
        // related MIUI processes.
        hookNotificationFilterHelper(lpparam)

        // -------------------------------------------------------------
        // Replace Mi Pay text
        // -------------------------------------------------------------

        tryHook("Settings Mi Pay text replacement") {

            if (!isMiPayReplacementEnabled(lpparam)) {
                return@tryHook
            }

            try {
                XposedHelpers.findAndHookMethod(
                    android.content.res.Resources::class.java,
                    "getString",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {

                        override fun afterHookedMethod(
                            param: MethodHookParam
                        ) {
                            val result =
                                param.result as? String
                                    ?: return

                            if (
                                MI_PAY_STRINGS.any {
                                    result.contains(
                                        it,
                                        ignoreCase = true
                                    )
                                }
                            ) {
                                param.result = "Google Wallet"
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }

            // ---------------------------------------------------------
            // TextView.setText(CharSequence)
            // ---------------------------------------------------------

            try {
                XposedHelpers.findAndHookMethod(
                    TextView::class.java,
                    "setText",
                    CharSequence::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val text =
                                param.args
                                    .getOrNull(0)
                                    ?.toString()
                                    ?: return

                            if (
                                MI_PAY_STRINGS.any {
                                    text.contains(
                                        it,
                                        ignoreCase = true
                                    )
                                }
                            ) {
                                param.args[0] =
                                    "Google Wallet"
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }

        // -------------------------------------------------------------
        // DoubleClickPowerKeySettingsActivity
        // -------------------------------------------------------------

        tryHook(
            "DoubleClickPowerKeySettingsActivity.onResume"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.settings.gesture.DoubleClickPowerKeySettingsActivity",
                    lpparam.classLoader
                )
                    ?: XposedHelpers.findClassIfExists(
                        "com.android.settings.gesture.DoubleClickPowerKeySettingsActivity",
                        lpparam.classLoader
                    )
                    ?: return@tryHook

            XposedHelpers.findAndHookMethod(
                clazz,
                "onResume",
                object : XC_MethodHook() {

                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        val activity =
                            param.thisObject as? Activity
                                ?: return

                        syncSelection(activity)
                    }
                }
            )
        }
    }

    // =================================================================
    // Mi Pay replacement enabled
    // =================================================================

    private fun isMiPayReplacementEnabled(
        lpparam: XC_LoadPackage.LoadPackageParam
    ): Boolean {
        return try {
            getSysProp(
                "persist.sys.kuromix.googlewallet"
            )
        } catch (_: Throwable) {
            false
        }
    }

    // =================================================================
    // Sync Google Wallet selection
    // =================================================================

    private fun syncSelection(
        activity: Activity
    ) {
        try {
            val root =
                activity.window?.decorView
                    ?: return

            val row =
                findRowByText(
                    root,
                    "Google Wallet"
                )
                    ?: return

            updateSelectionState(
                row,
                true
            )

            Settings.System.putString(
                activity.contentResolver,
                SETTING_KEY,
                MI_PAY_VALUE
            )

            XposedBridge.log(
                "$TAG: $LOG " +
                        "Google Wallet selection synced"
            )

        } catch (t: Throwable) {
            XposedBridge.log(
                "$TAG: $LOG syncSelection failed: " +
                        "${t.message}"
            )
        }
    }

    // =================================================================
    // Update row state
    // =================================================================

    private fun updateSelectionState(
        view: View,
        selected: Boolean
    ) {
        try {
            view.isSelected = selected
        } catch (_: Throwable) {
        }

        try {
            view.isActivated = selected
        } catch (_: Throwable) {
        }

        try {
            view.isFocusable = true
        } catch (_: Throwable) {
        }

        try {
            view.isClickable = true
        } catch (_: Throwable) {
        }
    }

    // =================================================================
    // Find row by text
    // =================================================================

    private fun findRowByText(
        root: View,
        target: String
    ): View? {

        if (root is TextView) {
            val text =
                root.text?.toString()

            if (
                text != null &&
                text.contains(
                    target,
                    ignoreCase = true
                )
            ) {
                return findClickableAncestor(root)
            }
        }

        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {

                val result =
                    findRowByText(
                        root.getChildAt(i),
                        target
                    )

                if (result != null) {
                    return result
                }
            }
        }

        return null
    }

    // =================================================================
    // Find clickable ancestor
    // =================================================================

    private fun findClickableAncestor(
        view: View
    ): View {

        var current: View? = view

        repeat(8) {

            val candidate =
                current
                    ?: return view

            if (
                candidate.isClickable ||
                candidate.isFocusable
            ) {
                return candidate
            }

            current =
                candidate.parent as? View
        }

        return view
    }

    // =================================================================
    // MiInput
    // =================================================================

    private fun hookMiInput(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        // -------------------------------------------------------------
        // Mi Pay text replacement
        // -------------------------------------------------------------

        tryHook("MiInput Mi Pay text replacement") {

            if (!isMiPayReplacementEnabled(lpparam)) {
                return@tryHook
            }

            try {
                XposedHelpers.findAndHookMethod(
                    TextView::class.java,
                    "setText",
                    CharSequence::class.java,
                    object : XC_MethodHook() {

                        override fun beforeHookedMethod(
                            param: MethodHookParam
                        ) {
                            val text =
                                param.args
                                    .getOrNull(0)
                                    ?.toString()
                                    ?: return

                            if (
                                MI_PAY_STRINGS.any {
                                    text.contains(
                                        it,
                                        ignoreCase = true
                                    )
                                }
                            ) {
                                param.args[0] =
                                    "Google Wallet"
                            }
                        }
                    }
                )
            } catch (_: Throwable) {
            }
        }

        // -------------------------------------------------------------
        // Add Google Wallet gesture item
        // -------------------------------------------------------------

        tryHook(
            "DoubleClickPowerKeySettingsActivity.getItems"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.settings.gesture.DoubleClickPowerKeySettingsActivity",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getItems",
                    object : XC_MethodHook() {

                        override fun afterHookedMethod(
                            param: MethodHookParam
                        ) {

                            val result =
                                param.result

                            if (
                                result !is MutableList<*>
                            ) {
                                return
                            }

                            try {
                                val itemClass =
                                    XposedHelpers.findClass(
                                        "com.android.settings.gesture.GestureItem",
                                        lpparam.classLoader
                                    )

                                val constructor =
                                    itemClass.constructors
                                        .firstOrNull {
                                            it.parameterTypes.size == 4
                                        }
                                        ?: return

                                val item =
                                    constructor.newInstance(
                                        "google_wallet",
                                        "Google Wallet",
                                        0,
                                        "launch_google_wallet"
                                    )

                                @Suppress("UNCHECKED_CAST")
                                (result as MutableList<Any>)
                                    .add(item)

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "Added Google Wallet gesture item"
                                )

                            } catch (t: Throwable) {
                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "Failed adding Google Wallet item: " +
                                            "${t.message}"
                                )
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(
                    "$TAG: $LOG getItems hook failed: " +
                            "${t.message}"
                )
            }
        }
    }

    // =================================================================
    // SystemUI
    // =================================================================

    private fun hookSystemUI(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedBridge.log(
            "$TAG: $LOG SystemUI loaded: " +
                    lpparam.packageName
        )

        // -------------------------------------------------------------
        // NEW:
        // Xiaomi Dynamic Island whitelist bypass
        // -------------------------------------------------------------

        hookDynamicIslandWhitelist(lpparam)

        // -------------------------------------------------------------
        // SpotlightController
        // -------------------------------------------------------------

        tryHook(
            "SpotlightController.isSpotlightAvailable"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.collection.provider.SpotlightController",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            val entryClass =
                XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedHelpers.findAndHookMethod(
                clazz,
                "isSpotlightAvailable",
                entryClass,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        try {
                            val entry =
                                param.args
                                    .getOrNull(0)

                            val sbn =
                                XposedHelpers.callMethod(
                                    entry,
                                    "getSbn"
                                ) as? android.service.notification.StatusBarNotification

                            if (
                                sbn?.packageName ==
                                KUROMIX_PKG
                            ) {
                                param.result = true

                                XposedBridge.log(
                                    "$TAG: $LOG " +
                                            "Spotlight available -> true"
                                )
                            }

                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Resident notification
        // -------------------------------------------------------------

        tryHook(
            "MiuiNotificationHelper.isResidentNotification"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.MiuiNotificationHelper",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            val sbnClass =
                android.service.notification
                    .StatusBarNotification::class.java

            XposedHelpers.findAndHookMethod(
                clazz,
                "isResidentNotification",
                sbnClass,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        val sbn =
                            param.args
                                .getOrNull(0)
                                    as? android.service.notification.StatusBarNotification
                                ?: return

                        if (
                            sbn.packageName ==
                            KUROMIX_PKG
                        ) {
                            param.result = true

                            XposedBridge.log(
                                "$TAG: $LOG " +
                                        "Resident notification -> true"
                            )
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Existing notification hooks
        // -------------------------------------------------------------

        hookFocusNotificationManager(
            lpparam
        )

        hookNotificationFilterHelper(
            lpparam
        )

        // -------------------------------------------------------------
        // Existing power key support
        // -------------------------------------------------------------

        hookPowerKeyDoubleClick(
            lpparam
        )
    }

    // =================================================================
    // system_server
    // =================================================================

    private fun hookSystemServer(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedBridge.log(
            "$TAG: $LOG system_server loaded"
        )

        // -------------------------------------------------------------
        // Optional Mi Pay -> Google Wallet redirect
        // -------------------------------------------------------------

        tryHook(
            "ActivityTaskManagerService.startActivity"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.ActivityTaskManagerService",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedHelpers.findAndHookMethod(
                clazz,
                "startActivity",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        try {
                            val selected =
                                Settings.System.getString(
                                    null,
                                    SETTING_KEY
                                )

                            if (
                                selected != MI_PAY_VALUE &&
                                selected != "mi_pay"
                            ) {
                                return
                            }

                            val args =
                                param.args

                            for (arg in args) {

                                if (
                                    arg is Intent &&
                                    TARGET_PKGS.contains(
                                        arg.component
                                            ?.packageName
                                    )
                                ) {
                                    arg.component =
                                        contextComponent(
                                            arg
                                        )

                                    XposedBridge.log(
                                        "$TAG: $LOG " +
                                                "Intercepted Mi Pay activity"
                                    )

                                    break
                                }
                            }

                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Rear display keep-awake
        // -------------------------------------------------------------

        tryHook(
            "RootWindowContainer.shouldRecallTask"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.RootWindowContainer",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            val taskClass =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.Task",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            val displayClass =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.DisplayContent",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedHelpers.findAndHookMethod(
                clazz,
                "shouldRecallTask",
                taskClass,
                displayClass,
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        try {
                            val display =
                                param.args
                                    .getOrNull(1)

                            val displayId =
                                XposedHelpers.callMethod(
                                    display,
                                    "getDisplayId"
                                ) as? Int
                                    ?: return

                            if (
                                displayId ==
                                REAR_DISPLAY_ID &&
                                getSysProp(
                                    "persist.sys.kuromix.rear_keepawake"
                                )
                            ) {
                                param.result = false
                            }

                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // SubScreenManagerService.isSupportSubScreen
        // -------------------------------------------------------------

        tryHook(
            "SubScreenManagerService.isSupportSubScreen"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.SubScreenManagerService",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "isSupportSubScreen",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        param.result = true
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // setSubDisplayPowerMode
        // -------------------------------------------------------------

        tryHook(
            "SubScreenManagerService.setSubDisplayPowerMode"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.SubScreenManagerService",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "setSubDisplayPowerMode",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            !getSysProp(
                                "persist.sys.kuromix.rear_keepawake"
                            )
                        ) {
                            return
                        }

                        val mode =
                            param.args
                                .firstOrNull {
                                    it is Int
                                } as? Int
                                ?: return

                        if (mode == 0) {
                            for (
                            i in param.args.indices
                            ) {
                                if (
                                    param.args[i] is Int
                                ) {
                                    param.args[i] = 2
                                }
                            }
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Disable rear display double tap if keep-awake
        // -------------------------------------------------------------

        tryHook(
            "SubScreenManagerService.handleSubScreenDoubleTap"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.SubScreenManagerService",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "handleSubScreenDoubleTap",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            getSysProp(
                                "persist.sys.kuromix.rear_keepawake"
                            )
                        ) {
                            param.result = null
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Rear display activity start
        // -------------------------------------------------------------

        tryHook(
            "ActivityStarterImpl.isAllowedToStartOnRearDisplay"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.wm.ActivityStarterImpl",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "isAllowedToStartOnRearDisplay",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            getSysProp(
                                "persist.sys.kuromix.rear_keepawake"
                            ) ||
                            getSysProp(
                                "persist.sys.kuromix.antikill"
                            )
                        ) {
                            param.result = true
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // ProcessPolicy dynamic whitelist
        // -------------------------------------------------------------

        tryHook(
            "ProcessPolicy.updateDynamicWhiteList"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.am.ProcessPolicy",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "updateDynamicWhiteList",
                object : XC_MethodHook() {

                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            !getSysProp(
                                "persist.sys.kuromix.antikill"
                            )
                        ) {
                            return
                        }

                        try {
                            val result =
                                param.result

                            if (
                                result is MutableMap<*, *>
                            ) {
                                @Suppress("UNCHECKED_CAST")
                                (
                                        result as MutableMap<String, Any>
                                        )[KUROMIX_PKG] = true
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // ProcessPolicy.updateApplicationLockedState
        // -------------------------------------------------------------

        tryHook(
            "ProcessPolicy.updateApplicationLockedState"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.am.ProcessPolicy",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "updateApplicationLockedState",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            !getSysProp(
                                "persist.sys.kuromix.antikill"
                            )
                        ) {
                            return
                        }

                        if (
                            param.args.any {
                                it == KUROMIX_PKG
                            }
                        ) {
                            return
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // systemReady
        // -------------------------------------------------------------

        tryHook(
            "ProcessPolicy.systemReady"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.android.server.am.ProcessPolicy",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "systemReady",
                object : XC_MethodHook() {

                    override fun afterHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            !getSysProp(
                                "persist.sys.kuromix.antikill"
                            )
                        ) {
                            return
                        }

                        try {
                            XposedHelpers.callMethod(
                                param.thisObject,
                                "updateApplicationLockedState",
                                KUROMIX_PKG,
                                -100,
                                true
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Keep existing hooks available in system_server
        // -------------------------------------------------------------

        hookNotificationFilterHelper(
            lpparam
        )

        hookFocusNotificationManager(
            lpparam
        )

        hookPowerKeyDoubleClick(
            lpparam
        )
    }

    // =================================================================
    // Rear display / SubScreenCenter
    // =================================================================

    private fun hookSubScreenCenter(
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        XposedBridge.log(
            "$TAG: $LOG SubScreenCenter loaded"
        )

        // -------------------------------------------------------------
        // notifySubScreenOff
        // -------------------------------------------------------------

        tryHook(
            "SubScreenStatusManager.notifySubScreenOff"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.xiaomi.subscreencenter.SubScreenStatusManager",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "notifySubScreenOff",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            getSysProp(
                                "persist.sys.kuromix.rear_keepawake"
                            )
                        ) {
                            XposedBridge.log(
                                "$TAG: $LOG " +
                                        "Blocked notifySubScreenOff"
                            )

                            param.result = null
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // getSubScreenDisplayTime
        // -------------------------------------------------------------

        tryHook(
            "SubScreenStatusManager.getSubScreenDisplayTime"
        ) {

            val clazz =
                XposedHelpers.findClassIfExists(
                    "com.xiaomi.subscreencenter.SubScreenStatusManager",
                    lpparam.classLoader
                )
                    ?: return@tryHook

            XposedBridge.hookAllMethods(
                clazz,
                "getSubScreenDisplayTime",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(
                        param: MethodHookParam
                    ) {
                        if (
                            getSysProp(
                                "persist.sys.kuromix.rear_keepawake"
                            )
                        ) {
                            // 24 hours in milliseconds.
                            param.result =
                                24L * 60L * 60L * 1000L
                        }
                    }
                }
            )
        }

        // -------------------------------------------------------------
        // Existing notification hooks
        // -------------------------------------------------------------

        hookFocusNotificationManager(
            lpparam
        )

        hookNotificationFilterHelper(
            lpparam
        )

        hookPowerKeyDoubleClick(
            lpparam
        )
    }

    // =================================================================
    // Intent helper
    // =================================================================

    private fun contextComponent(
        original: Intent
    ): android.content.ComponentName? {

        return try {
            android.content.ComponentName(
                GOOGLE_WALLET_PKG,
                "com.google.android.apps.walletnfcrel.MainActivity"
            )
        } catch (_: Throwable) {
            null
        }
    }
}
