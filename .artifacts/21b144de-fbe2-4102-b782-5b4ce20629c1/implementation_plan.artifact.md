# Implementation Plan - Precision GPay Hook & Scope Expansion

This plan refines the Google Wallet (GPay) remapping to strictly follow the provided script's logic, ensuring deep rebranding, UI synchronization, and expanding the Xposed scope to include NFC and payment services.

## User Review Required

> [!IMPORTANT]
> **Scope Expansion:** For the GPay hook to work perfectly, you will need to enable **NFC Service**, **Mi Pay**, and **UnionPay** in the KuroMix scope within LSPosed.

## Proposed Changes

### [strings.xml](file:///D:/KuroMix/app/src/main/res/values/strings.xml)

#### [MODIFY] [strings.xml](file:///D:/KuroMix/app/src/main/res/values/strings.xml)
- Add the following packages to the `xposed_scope` array:
    - `com.android.nfc` (NFC Service)
    - `com.miui.tsmclient` (Mi Pay / TSM Client)
    - `com.unionpay.tsmservice.mi` (UnionPay Service)
    - `com.miui.nextpay` (Next Pay)

### [KuroMixHook.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/hook/KuroMixHook.kt)

#### [MODIFY] [KuroMixHook.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/hook/KuroMixHook.kt)
- **Deep Rebranding Logic:**
    - Update `Resources#getString` hook to replace any text matching the "Mi Pay" keywords (`Mi Pay`, `Transport card`, `Key card`, etc.) with "Google Wallet".
    - Update `TextView#setText` hook to perform the same replacement dynamically in the UI.
- **UI Synchronization (`syncSelection`):**
    - Hook `Activity#onResume` for `DoubleClickPowerKeySettingsActivity`.
    - Implement a post-delayed task to find the "Google Wallet" row (formerly "Mi Pay") and:
        - Sync its selection state with the `double_click_power_key` system setting.
        - Add a custom `OnClickListener` to the row to update the system setting to `launch_mi_pay` and refresh the radio button UI when clicked.
- **Improved Redirection:**
    - Hook `ActivityTaskManagerService#startActivity` (in `system_server`) to intercept any Intent targeting the payment packages and launch Google Wallet instead.
    - Hook `Activity#onResume` in the payment packages (`com.miui.tsmclient`, etc.) as a secondary layer to redirect to Google Wallet and finish the task.

## Verification Plan

### Automated Tests
- Build the project with `./gradlew :app:assembleDebug`.

### Manual Verification
1.  **Scope Check:** In LSPosed, verify that all new packages are visible and selectable for KuroMix.
2.  **Settings UI:** Navigate to the gesture settings; verify that "Mi Pay" is rebranded as "Google Wallet" and that selecting it correctly updates the radio buttons.
3.  **Hardware Test:** Double-press the power button; verify it triggers GPay without showing any Mi Pay screens.
