package com.kuromify.kuromix.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry point for KuroMix.
 *
 * This module hooks system_server and SubScreenCenter to keep the rear display alive
 * and improve its background persistence on Xiaomi HyperOS.
 */
class KuroMixHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "KuroMixHook"
        private const val REAR_DISPLAY_ID = 1
        private const val GOOGLE_WALLET_PKG = "com.google.android.apps.walletnfcrel"
        private const val SETTING_KEY = "double_click_power_key"
        private const val MI_PAY_VALUE = "launch_mi_pay"
        private val MI_PAY_TEXTS = arrayOf("Mi Pay", "Key card", "小米支付", "门卡", "智能刷卡", "Transport card", "小米钱包", "卡券", "支付", "钱包")
        private val TARGET_PKGS = setOf("com.miui.tsmclient", "com.unionpay.tsmservice.mi", "com.miui.nextpay", "com.android.nfc")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> hookSystemServer(lpparam)
            "com.android.systemui" -> hookSystemUI(lpparam)
            "com.miui.miinput", "com.miui.securitycore", "com.android.settings" -> {
                hookMiInput(lpparam)
                hookSettingsUI(lpparam)
            }
            "com.xiaomi.subscreencenter" -> hookSubScreenCenter(lpparam)
            in TARGET_PKGS -> hookMiPayProcesses(lpparam)
            "com.kuromify.kuromix" -> hookSelf(lpparam)
        }
    }

    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
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

    private fun hookMiPayProcesses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isReplaceEnabled = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.replace_mipay", "0") == "1"
        } catch (_: Throwable) { false }

        if (!isReplaceEnabled) return

        try {
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
        } catch (_: Throwable) {}
    }

    private fun launchGoogleWallet(context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(GOOGLE_WALLET_PKG)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (_: Throwable) {}
    }

    private fun hookSettingsUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isReplaceEnabled = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.replace_mipay", "0") == "1"
        } catch (_: Throwable) { false }

        if (!isReplaceEnabled) return

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as android.app.Activity
                        if (activity.javaClass.name.contains("DoubleClickPowerKeySettingsActivity")) {
                            val decorView = activity.window.decorView
                            decorView.postDelayed({
                                try {
                                    syncSelection(activity)
                                } catch (_: Throwable) {}
                            }, 500)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun syncSelection(activity: android.app.Activity) {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val gWalletRow = findRowByText(root, "Google Wallet") ?: return
        
        val currentSetting = android.provider.Settings.System.getString(activity.contentResolver, SETTING_KEY)
        val isSelected = (currentSetting == MI_PAY_VALUE || currentSetting == "mi_pay")
        
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
        if (row is android.widget.Checkable) row.isChecked = selected
        if (row is android.view.ViewGroup) {
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                if (child is android.widget.Checkable) child.isChecked = selected
                else if (child is android.view.ViewGroup) updateSelectionState(child, selected)
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
        XposedBridge.log("$TAG: Hooking MiInput (${lpparam.packageName})")
        
        val isReplaceEnabled = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.replace_mipay", "0") == "1"
        } catch (_: Throwable) { false }

        if (isReplaceEnabled) {
            try {
                // Hook Resources to rename "Mi Pay" strings
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

                // Hook TextView to rename labels dynamically
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
            } catch (_: Throwable) {}
        }

        try {
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
                        
                        // Check if Google Wallet already exists in the list (added by us or natively)
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
        } catch (_: Throwable) {}
    }

    private fun hookSystemUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Hooking SystemUI")
        try {
            // Force spotlight (Super Island) eligibility for KuroMix
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
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 5. Hook NotificationFilterHelper to ensure KuroMix is treated as Important/Focus
        try {
            val filterHelperClass = XposedHelpers.findClass("miui.util.NotificationFilterHelper", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isImportantNotification",
                "android.content.Context",
                "java.lang.String",
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[1] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isSystemApp",
                "java.lang.String",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 6. Hook MiuiPhoneWindowManager for double-click power button action
        try {
            val windowManagerClass = "com.android.server.policy.MiuiPhoneWindowManager"
            XposedHelpers.findAndHookMethod(
                windowManagerClass,
                lpparam.classLoader,
                "powerPress",
                Long::class.javaPrimitiveType, // eventTime
                Boolean::class.javaPrimitiveType, // interactive
                Int::class.javaPrimitiveType, // count
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val count = param.args[2] as Int
                        if (count == 2) {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? android.content.Context ?: return
                            val action = android.provider.Settings.System.getString(context.contentResolver, "double_click_power_key")
                            
                            if (action == "google_wallet") {
                                XposedBridge.log("$TAG: Launching Google Wallet via power double-click")
                                
                                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.walletnfcrel")
                                if (intent != null) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    
                                    // Consume the event to prevent default behavior
                                    param.result = null
                                }
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: Hooking system_server")

        val isKeepAwake = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.keep_awake", "0") == "1"
        } catch (_: Throwable) { false }

        val isAntiKill = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.anti_kill", "0") == "1"
        } catch (_: Throwable) { false }

        val isReplaceEnabled = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.replace_mipay", "0") == "1"
        } catch (_: Throwable) { false }

        // 0. Redirection logic for Mi Pay intents
        if (isReplaceEnabled) {
            try {
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
                                    param.result = 0 // START_SUCCESS
                                }
                            }
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        // 1. Prevent task recall on screen off
        try {
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
        } catch (_: Throwable) {}

        // 2. Hook SubScreenManagerService for power state and gesture control
        try {
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

            // WAKE-LOCK: Override power mode OFF -> ON
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

            // DISABLE DOUBLE-TAP GESTURE
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
        } catch (_: Throwable) {}

        // 3. Hook ActivityStarterImpl for whitelisting
        try {
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
        } catch (_: Throwable) {}

        // 4. Hook ProcessPolicy for background whitelisting (Dynamic White List)
        try {
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
                            map["com.kuromify.kuromix"] = true
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                processPolicyClass,
                lpparam.classLoader,
                "systemReady",
                "android.content.Context",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isAntiKill) {
                            XposedHelpers.callMethod(param.thisObject, "updateApplicationLockedState", "com.kuromify.kuromix", -100, true)
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 5. Hook NotificationFilterHelper to ensure KuroMix is treated as Important/Focus
        try {
            val filterHelperClass = XposedHelpers.findClass("miui.util.NotificationFilterHelper", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isImportantNotification",
                "android.content.Context",
                "java.lang.String",
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[1] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isSystemApp",
                "java.lang.String",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 6. Hook MiuiPhoneWindowManager for double-click power button action
        try {
            val windowManagerClass = "com.android.server.policy.MiuiPhoneWindowManager"
            XposedHelpers.findAndHookMethod(
                windowManagerClass,
                lpparam.classLoader,
                "powerPress",
                Long::class.javaPrimitiveType, // eventTime
                Boolean::class.javaPrimitiveType, // interactive
                Int::class.javaPrimitiveType, // count
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val count = param.args[2] as Int
                        if (count == 2) {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? android.content.Context ?: return
                            val action = android.provider.Settings.System.getString(context.contentResolver, "double_click_power_key")
                            
                            if (action == "google_wallet") {
                                XposedBridge.log("$TAG: Launching Google Wallet via power double-click")
                                launchGoogleWallet(context)
                                param.result = null
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }

    private fun hookSubScreenCenter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val isAntiKill = try {
            val sysPropClass = XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader)
            XposedHelpers.callStaticMethod(sysPropClass, "get", "persist.kuromix.anti_kill", "0") == "1"
        } catch (_: Throwable) { false }
        
        try {
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

            XposedHelpers.findAndHookMethod(
                statusManagerClass,
                lpparam.classLoader,
                "getSubScreenDisplayTime",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 24 * 60 * 60 * 1000 // 24 hours
                    }
                }
            )

            // HOME GUARD: Block transition to launcher on AOD
            XposedHelpers.findAndHookMethod(
                statusManagerClass,
                lpparam.classLoader,
                "moveHomeToFront",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isAntiKill) {
                            val reason = param.args[0] as? String
                            if (reason == "aod" || reason == "turning_off") param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 5. Hook NotificationFilterHelper to ensure KuroMix is treated as Important/Focus
        try {
            val filterHelperClass = XposedHelpers.findClass("miui.util.NotificationFilterHelper", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isImportantNotification",
                "android.content.Context",
                "java.lang.String",
                "android.app.Notification",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[1] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                filterHelperClass,
                "isSystemApp",
                "java.lang.String",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String
                        if (pkg == "com.kuromify.kuromix") {
                            param.result = true
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // 6. Hook MiuiPhoneWindowManager for double-click power button action
        try {
            val windowManagerClass = "com.android.server.policy.MiuiPhoneWindowManager"
            XposedHelpers.findAndHookMethod(
                windowManagerClass,
                lpparam.classLoader,
                "powerPress",
                Long::class.javaPrimitiveType, // eventTime
                Boolean::class.javaPrimitiveType, // interactive
                Int::class.javaPrimitiveType, // count
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val count = param.args[2] as Int
                        if (count == 2) {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? android.content.Context ?: return
                            val action = android.provider.Settings.System.getString(context.contentResolver, "double_click_power_key")
                            
                            if (action == "google_wallet") {
                                XposedBridge.log("$TAG: Launching Google Wallet via power double-click")
                                
                                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.walletnfcrel")
                                if (intent != null) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    
                                    // Consume the event to prevent default behavior
                                    param.result = null
                                }
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}
    }
}
