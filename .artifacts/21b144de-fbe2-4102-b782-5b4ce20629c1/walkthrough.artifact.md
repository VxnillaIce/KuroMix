# Walkthrough - Refined Google Wallet Hook & Scope Expansion

I have refined the Google Wallet (GPay) remapping logic to strictly follow the provided script. This includes deeper integration with system settings and expansion of the Xposed scope to cover NFC and payment services.

## Changes

### [strings.xml](file:///D:/KuroMix/app/src/main/res/values/strings.xml)

I expanded the `xposed_scope` to include critical system and payment packages. This ensures that the hooks are applied to the NFC service and various Mi Pay-related components.

```xml
<item>com.android.nfc</item>
<item>com.miui.tsmclient</item>
<item>com.unionpay.tsmservice.mi</item>
<item>com.miui.nextpay</item>
```

### [KuroMixHook.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/hook/KuroMixHook.kt)

I integrated the refined logic for rebranding "Mi Pay" as "Google Wallet" and ensuring proper UI synchronization:

- **Deep Rebranding:** Updated the `Resources#getString` and `TextView#setText` hooks to replace "Mi Pay" and related terms globally in targeted packages.
- **UI Synchronization (`syncSelection`):** Implemented a hook for `DoubleClickPowerKeySettingsActivity` that ensures the "Google Wallet" row (formerly "Mi Pay") correctly reflects and updates the hardware shortcut setting.
- **Improved Redirection:** Enhanced the `ActivityTaskManagerService` and `Activity#onResume` hooks to intercept payment app launches and redirect them to Google Wallet (`com.google.android.apps.walletnfcrel`).

```kotlin
// UI Synchronization logic
private fun syncSelection(activity: android.app.Activity) {
    val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
    val gWalletRow = findRowByText(root, "Google Wallet") ?: return
    // ... handles click and radio button state
}
```

## Verification Results

### Automated Tests
- Ran `:app:assembleDebug`: **SUCCESS**

### Manual Verification Required
> [!IMPORTANT]
> **LSPosed Scope:** Please ensure that **NFC Service**, **Mi Pay**, and **UnionPay** are selected in the KuroMix scope within the LSPosed Manager.
> **REBOOT REQUIRED:** These changes affect core system and payment services. A reboot is necessary for all hooks to activate correctly.

1. Verify that "Mi Pay" is rebranded as "Google Wallet" in the power button gesture settings.
2. Verify that double-clicking the power button launches Google Wallet.
3. Verify that the "Restart Scoped Apps" button in the Dashboard correctly restarts the updated scope.
