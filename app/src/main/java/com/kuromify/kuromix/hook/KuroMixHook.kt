package com.kuromify.kuromix.hook

import android.service.notification.StatusBarNotification
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class KuroMixHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KuroMixHook"
        private const val LOG = "[KUROMIX_HOOK]"
        private const val REAR_DISPLAY_ID = 1
        private const val GOOGLE_WALLET_PKG = "com.google.android.apps.walletnfcrel"
        private const val SETTING_KEY = "double_click_power_key"
        private const val MI_PAY_VALUE = "launch_mi_pay"
        private const val KUROMIX_PKG = "com.kuromify.kuromix"
        private val MI_PAY_TEXTS = arrayOf("Mi Pay", "Key card", "小米支付", "门卡", "智能刷卡", "Transport card", "小米钱包", "卡券", "支付", "钱包")
        private val TARGET_PKGS = setOf("com.miui.tsmclient", "com.unionpay.tsmservice.mi", "com.miui.nextpay", "com.android.nfc")
    }

    private fun tryHook(label: String, block: () -> Unit) {
        try {
            block()
            XposedBridge.log("$TAG: $LOG OK   -> $label")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $LOG FAILED -> $label :: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Reflects on a class and logs every declared method's name, param
     * types, and return type. Used to discover the real API of internal
     * OEM classes instead of guessing.
     */
    @Suppress("SameParameterValue")
    private fun dumpClassMethods(lpparam: XC_LoadPackage.LoadPackageParam, className: String) {
        try {
            val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
            XposedBridge.log("$TAG: $LOG ==== Methods of $className (${lpparam.packageName}) ====")
            clazz.declaredMethods
                .sortedBy { it.name }
                .forEach { m ->
                    val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                    XposedBridge.log("$TAG: $LOG   ${m.name}($params): ${m.returnType.simpleName}")
                }
            XposedBridge.log("$TAG: $LOG ==== End methods of $className ====")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $LOG FAILED to dump $className :: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: $LOG handleLoadPackage: ${lpparam.packageName}")
        when (lpparam.packageName) {
            "android" -> hookSystemServer(lpparam)
            "com.android.systemui", "com.miui.notification", "com.miui.securitycenter" -> hookSystemUI(lpparam)
            "com.miui.miinput", "com.miui.securitycore", "com.android.settings", "com.android.nfc" -> {
                hookMiInput(lpparam)
                hookSettingsUI(lpparam)
            }
            "com.xiaomi.subscreencenter" -> hookSubScreenCenter(lpparam)
            in TARGET_PKGS -> {
                hookMiPayProcesses(lpparam)
                hookSettingsUI(lpparam)
            }
            KUROMIX_PKG -> hookSelf(lpparam)
            else -> {}
        }
    }

    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        tryHook("RootShell.isModuleActive") {
            XposedHelpers.findAndHookMethod(
                "com.kuromify.kuromix.root.RootShell",
                lpparam.classLoader,
                "isModuleActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        }
        tryHook("RootShell.getXposedApiLevel") {
            XposedHelpers.findAndHookMethod(
                "com.kuromify.kuromix.root.RootShell",
                lpparam.classLoader,
                "getXposedApiLevel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = XposedBridge.getXposedVersion()
                    }
                }
            )
        }
    }

    private fun getSysProp(lpparam: XC_LoadPackage.LoadPackageParam, key: String): Boolean {
        return try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", key, "0") == "1"
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $LOG FAILED -> SystemProperties.get($key) :: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun hookMiPayProcesses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isReplaceEnabled = getSysProp(lpparam, "persist.kuromix.replace_mipay")
        if (!isReplaceEnabled) return

        tryHook("Activity.onResume (MiPay redirect)") {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as android.app.Activity
                        val setting = android.provider.Settings.System.getString(activity.contentResolver, SETTING_KEY)
                        if (setting == MI_PAY_VALUE || setting == "mi_pay") {
                            launchGoogleWallet(activity)
                            activity.finishAndRemoveTask()
                        }
                    }
                }
            )
        }
    }

    private fun launchGoogleWallet(context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(GOOGLE_WALLET_PKG)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                XposedBridge.log("$TAG: $LOG launchGoogleWallet: no launch intent found for $GOOGLE_WALLET_PKG (not installed?)")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $LOG FAILED -> launchGoogleWallet :: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // =====================================================================
    // NotificationFilterHelper — DIAGNOSTIC MODE.
    // Dumps the real method list first; the hooks below are the OLD
    // guessed names and are expected to still fail until you send back
    // the dump output and I rewrite these against the real signatures.
    // =====================================================================
    private fun hookNotificationFilterHelper(lpparam: XC_LoadPackage.LoadPackageParam) {
        dumpClassMethods(lpparam, "miui.util.NotificationFilterHelper")

        val filterHelperClass = try {
            XposedHelpers.findClass("miui.util.NotificationFilterHelper", lpparam.classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: $LOG FAILED -> findClass(miui.util.NotificationFilterHelper) in ${lpparam.packageName} :: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        val hookImportant = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val pkg = if (param.args[0] is String) param.args[0] as String else param.args[1] as? String
                if (pkg == KUROMIX_PKG) param.result = true
            }
        }

        tryHook("NotificationFilterHelper.isImportantNotification(ctx,pkg)") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isImportantNotification", "android.content.Context", String::class.java, hookImportant)
        }
        tryHook("NotificationFilterHelper.isImportantNotification(ctx,pkg,notif)") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isImportantNotification", "android.content.Context", String::class.java, "android.app.Notification", hookImportant)
        }
        tryHook("NotificationFilterHelper.isAllowedShowFocus") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isAllowedShowFocus", "android.content.Context", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[1] == KUROMIX_PKG) param.result = true
                }
            })
        }
        tryHook("NotificationFilterHelper.isSupportFocus") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isSupportFocus", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == KUROMIX_PKG) param.result = true
                }
            })
        }
        tryHook("NotificationFilterHelper.isSystemApp") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isSystemApp", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == KUROMIX_PKG) param.result = true
                }
            })
        }
        tryHook("NotificationFilterHelper.isAllowedShowResidentNotification") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isAllowedShowResidentNotification", "android.content.Context", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[1] == KUROMIX_PKG) param.result = true
                }
            })
        }
        tryHook("NotificationFilterHelper.isSupportResidentNotification") {
            XposedHelpers.findAndHookMethod(filterHelperClass, "isSupportResidentNotification", String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == KUROMIX_PKG) param.result = true
                }
            })
        }
    }

    private fun hookFocusNotificationManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        tryHook("FocusNotificationManager.isFocusNotificationAllowed") {
            val focusManagerClass = "com.miui.systemui.notification.FocusNotificationManager"
            XposedHelpers.findAndHookMethod(
                focusManagerClass,
                lpparam.classLoader,
                "isFocusNotificationAllowed",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String
                        if (pkg == KUROMIX_PKG) {
                            param.result = true
                        }
                    }
                }
            )
        }
    }

    private fun hookPowerKeyDoubleClick(lpparam: XC_LoadPackage.LoadPackageParam) {
        tryHook("MiuiPhoneWindowManager.powerPress") {
            val windowManagerClass = "com.android.server.policy.MiuiPhoneWindowManager"
            XposedHelpers.findAndHookMethod(
                windowManagerClass,
                lpparam.classLoader,
                "powerPress",
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val count = param.args[2] as Int
                        if (count == 2) {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? android.content.Context ?: return
                            val action = android.provider.Settings.System.getString(context.contentResolver, "double_click_power_key")
                            if (action == "google_wallet") {
                                XposedBridge.log("$TAG: $LOG Launching Google Wallet via power double-click")
                                launchGoogleWallet(context)
                                param.result = null
                            }
                        }
                    }
                }
            )
        }
    }

    private fun hookSettingsUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isReplaceEnabled = getSysProp(lpparam, "persist.kuromix.replace_mipay")

        hookNotificationFilterHelper(lpparam)
        // NotificationSettingsHelper removed — ClassNotFoundException on this build.

        if (!isReplaceEnabled) return

        tryHook("Resources.getString (Mi Pay rename)") {
            XposedHelpers.findAndHookMethod(
                "android.content.res.Resources",
                lpparam.classLoader,
                "getString",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? String ?: return
                        for (miText in MI_PAY_TEXTS) {
                            if (result.contains(miText, ignoreCase = true)) {
                                param.result = "Google Wallet"
                                break
                            }
                        }
                    }
                }
            )
        }

        tryHook("TextView.setText (Mi Pay rename)") {
            XposedHelpers.findAndHookMethod(
                "android.widget.TextView",
                lpparam.classLoader,
                "setText",
                CharSequence::class.java,
                "android.widget.TextView.BufferType",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val text = param.args[0]?.toString() ?: return
                        for (miText in MI_PAY_TEXTS) {
                            if (text.contains(miText, ignoreCase = true) && !text.contains("Google Wallet")) {
                                param.args[0] = "Google Wallet"
                                break
                            }
                        }
                    }
                }
            )
        }

        tryHook("Activity.onResume (DoubleClickPowerKeySettingsActivity sync)") {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as android.app.Activity
                        if (activity.javaClass.name.contains("DoubleClickPowerKeySettingsActivity")) {
                            val decorView = activity.window.decorView
                            decorView.postDelayed(
                                {
                                    try {
                                        syncSelection(activity)
                                    } catch (e: Throwable) {
                                        XposedBridge.log("$TAG: $LOG FAILED -> syncSelection :: ${e.javaClass.simpleName}: ${e.message}")
                                    }
                                },
                                500
                            )
                        }
                    }
                }
            )
        }
    }

    private fun syncSelection(activity: android.app.Activity) {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val gWalletRow = findRowByText(root, "Google Wallet") ?: return

        val currentSetting = android.provider.Settings.System.getString(activity.contentResolver, SETTING_KEY)
        val isSelected = (currentSetting == MI_PAY_VALUE) || (currentSetting == "mi_pay")

        updateSelectionState(gWalletRow, selected = isSelected)

        gWalletRow.setOnClickListener {
            android.provider.Settings.System.putString(activity.contentResolver, SETTING_KEY, MI_PAY_VALUE)
            updateSelectionState(gWalletRow, selected = true)
            val parent = gWalletRow.parent as? android.view.ViewGroup
            parent?.let {
                for (i in 0 until it.childCount) {
                    val child = it.getChildAt(i)
                    if (child != gWalletRow) updateSelectionState(child, selected = false)
                }
            }
        }
    }

    private fun updateSelectionState(row: android.view.View, selected: Boolean) {
        (row as? android.widget.Checkable)?.isChecked = selected
        (row as? android.view.ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                if (child is android.widget.Checkable) {
                    child.isChecked = selected
                } else if (child is android.view.ViewGroup) {
                    updateSelectionState(child, selected)
                }
            }
        }
    }

    private fun findRowByText(root: android.view.View, text: String): android.view.View? {
        if (root is android.widget.TextView && root.text?.toString()?.equals(text, ignoreCase = true) == true) {
            return findClickableAncestor(root) ?: root
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findRowByText(root.getChildAt(i), text)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findClickableAncestor(view: android.view.View): android.view.View? {
        var current: android.view.View? = view
        while (current != null) {
            if (current.isClickable) return current
            val parent = current.parent
            current = if (parent is android.view.View) parent else null
        }
        return null
    }

    private fun hookMiInput(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: $LOG Hooking MiInput (${lpparam.packageName})")

        val isReplaceEnabled = getSysProp(lpparam, "persist.kuromix.replace_mipay")

        if (isReplaceEnabled) {
            tryHook("Resources.getString (MiInput Mi Pay rename)") {
                XposedHelpers.findAndHookMethod(
                    "android.content.res.Resources",
                    lpparam.classLoader,
                    "getString",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val result = param.result as? String ?: return
                            for (miText in MI_PAY_TEXTS) {
                                if (result.contains(miText, ignoreCase = true)) {
                                    param.result = "Google Wallet"
                                    break
                                }
                            }
                        }
                    }
                )
            }

            tryHook("TextView.setText (MiInput Mi Pay rename)") {
                XposedHelpers.findAndHookMethod(
                    "android.widget.TextView",
                    lpparam.classLoader,
                    "setText",
                    CharSequence::class.java,
                    "android.widget.TextView.BufferType",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val text = param.args[0]?.toString() ?: return
                            for (miText in MI_PAY_TEXTS) {
                                if (text.contains(miText, ignoreCase = true) && !text.contains("Google Wallet")) {
                                    param.args[0] = "Google Wallet"
                                    break
                                }
                            }
                        }
                    }
                )
            }
        }

        tryHook("DoubleClickPowerKeySettingsActivity.getItems") {
            val activityClass = "com.miui.miinput.gesture.powerkey.DoubleClickPowerKeySettingsActivity"
            val itemClass = "com.miui.miinput.gesture.model.GestureItem"

            XposedHelpers.findAndHookMethod(
                activityClass,
                lpparam.classLoader,
                "getItems",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val items = param.result as? MutableList<Any> ?: return

                        val exists = items.any {
                            XposedHelpers.getObjectField(it, "mId") == "google_wallet" ||
                                    (XposedHelpers.getObjectField(it, "mName") as? String)?.contains("Google Wallet") == true
                        }

                        if (!exists) {
                            val walletItem = XposedHelpers.newInstance(
                                XposedHelpers.findClass(itemClass, lpparam.classLoader),
                                "google_wallet",
                                "Google Wallet",
                                0,
                                "launch_google_wallet"
                            )
                            items.add(walletItem)
                        }
                    }
                }
            )
        }
    }

    private fun hookSystemUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: $LOG Hooking SystemUI (${lpparam.packageName})")

        tryHook("SpotlightController.isSpotlightAvailable") {
            val spotlightControllerClass = "com.miui.systemui.notification.SpotlightController"
            XposedHelpers.findAndHookMethod(
                spotlightControllerClass,
                lpparam.classLoader,
                "isSpotlightAvailable",
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val entry = param.args[0]
                        val sbn = XposedHelpers.callMethod(entry, "getSbn")
                        val pkg = XposedHelpers.callMethod(sbn, "getPackageName") as? String
                        if (pkg == KUROMIX_PKG) {
                            param.result = true
                        }
                    }
                }
            )
        }

        tryHook("MiuiNotificationHelper.isResidentNotification") {
            val miuiNotificationHelperClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.MiuiNotificationHelper", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(miuiNotificationHelperClass, "isResidentNotification", "android.service.notification.StatusBarNotification", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sbn = param.args[0] as? StatusBarNotification
                    if (sbn?.packageName == KUROMIX_PKG) param.result = true
                }
            })
        }

        hookFocusNotificationManager(lpparam)
        hookNotificationFilterHelper(lpparam)
        hookPowerKeyDoubleClick(lpparam)
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: $LOG Hooking system_server")

        val isKeepAwake = getSysProp(lpparam, "persist.kuromix.keep_awake")
        val isAntiKill = getSysProp(lpparam, "persist.kuromix.anti_kill")
        val isReplaceEnabled = getSysProp(lpparam, "persist.kuromix.replace_mipay")

        if (isReplaceEnabled) {
            tryHook("ActivityTaskManagerService.startActivity (MiPay redirect)") {
                val atmClass = "com.android.server.wm.ActivityTaskManagerService"
                XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(atmClass, lpparam.classLoader),
                    "startActivity",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args[3] as? android.content.Intent ?: return
                            val pkg = intent.component?.packageName ?: intent.`package` ?: return
                            if (TARGET_PKGS.contains(pkg)) {
                                val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as android.content.Context
                                val setting = android.provider.Settings.System.getString(context.contentResolver, SETTING_KEY)
                                if (setting == MI_PAY_VALUE || setting == "mi_pay") {
                                    launchGoogleWallet(context)
                                    param.result = 0
                                }
                            }
                        }
                    }
                )
            }
        }

        tryHook("RootWindowContainer.shouldRecallTask") {
            val rootWindowContainerClass = "com.android.server.wm.RootWindowContainer"
            XposedHelpers.findAndHookMethod(
                rootWindowContainerClass,
                lpparam.classLoader,
                "shouldRecallTask",
                "com.android.server.wm.Task",
                "com.android.server.wm.DisplayContent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isKeepAwake) {
                            val displayContent = param.args[1]
                            if (displayContent != null) {
                                val displayId = XposedHelpers.getIntField(displayContent, "mDisplayId")
                                if (displayId == REAR_DISPLAY_ID) {
                                    param.result = false
                                }
                            }
                        }
                    }
                }
            )
        }

        tryHook("SubScreenManagerService.isSupportSubScreen") {
            val subScreenServiceClass = "com.miui.server.SubScreenManagerService"
            XposedHelpers.findAndHookMethod(
                subScreenServiceClass,
                lpparam.classLoader,
                "isSupportSubScreen",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        }

        tryHook("SubScreenManagerService.setSubDisplayPowerMode") {
            val subScreenServiceClass = "com.miui.server.SubScreenManagerService"
            XposedHelpers.findAndHookMethod(
                subScreenServiceClass,
                lpparam.classLoader,
                "setSubDisplayPowerMode",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isKeepAwake && param.args[0] == 0) {
                            param.args[0] = 2
                        }
                    }
                }
            )
        }

        tryHook("SubScreenManagerService.handleSubScreenDoubleTap") {
            val subScreenServiceClass = "com.miui.server.SubScreenManagerService"
            XposedHelpers.findAndHookMethod(
                subScreenServiceClass,
                lpparam.classLoader,
                "handleSubScreenDoubleTap",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isKeepAwake) param.result = null
                    }
                }
            )
        }

        tryHook("ActivityStarterImpl.isAllowedToStartOnRearDisplay") {
            val starterClass = "com.android.server.wm.ActivityStarterImpl"
            XposedHelpers.findAndHookMethod(
                starterClass,
                lpparam.classLoader,
                "isAllowedToStartOnRearDisplay",
                "com.android.server.wm.ActivityRecord",
                "com.android.server.wm.Task",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isKeepAwake || isAntiKill) param.result = true
                    }
                }
            )
        }

        tryHook("ProcessPolicy.updateDynamicWhiteList") {
            val processPolicyClass = "com.android.server.am.ProcessPolicy"
            XposedHelpers.findAndHookMethod(
                processPolicyClass,
                lpparam.classLoader,
                "updateDynamicWhiteList",
                "android.content.Context",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isAntiKill) {
                            val map = param.result as? MutableMap<String, Boolean> ?: return
                            map[KUROMIX_PKG] = true
                        }
                    }
                }
            )
        }

        tryHook("ProcessPolicy.systemReady") {
            val processPolicyClass = "com.android.server.am.ProcessPolicy"
            XposedHelpers.findAndHookMethod(
                processPolicyClass,
                lpparam.classLoader,
                "systemReady",
                "android.content.Context",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isAntiKill) {
                            XposedHelpers.callMethod(param.thisObject, "updateApplicationLockedState", KUROMIX_PKG, -100, true)
                        }
                    }
                }
            )
        }

        hookFocusNotificationManager(lpparam)
        hookNotificationFilterHelper(lpparam)
        hookPowerKeyDoubleClick(lpparam)
    }

    private fun hookSubScreenCenter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isAntiKill = getSysProp(lpparam, "persist.kuromix.anti_kill")

        tryHook("SubScreenStatusManager.notifySubScreenOff") {
            val statusManagerClass = "com.xiaomi.subscreencenter.SubScreenStatusManager"
            XposedHelpers.findAndHookMethod(
                statusManagerClass,
                lpparam.classLoader,
                "notifySubScreenOff",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isAntiKill) param.result = null
                    }
                }
            )
        }

        tryHook("SubScreenStatusManager.getSubScreenDisplayTime") {
            val statusManagerClass = "com.xiaomi.subscreencenter.SubScreenStatusManager"
            XposedHelpers.findAndHookMethod(
                statusManagerClass,
                lpparam.classLoader,
                "getSubScreenDisplayTime",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 24 * 60 * 60 * 1000
                    }
                }
            )
        }

        hookFocusNotificationManager(lpparam)
        hookNotificationFilterHelper(lpparam)
        hookPowerKeyDoubleClick(lpparam)
    }
}