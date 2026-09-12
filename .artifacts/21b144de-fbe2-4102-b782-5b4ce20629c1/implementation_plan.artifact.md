# Implementation Plan - Final Super Island Fix

This plan implements the absolute definitive fix for Xiaomi Super Island (Focus Notifications) by combining deep Xposed whitelisting with correctly structured "Live Activity" payloads.

## User Review Required

> [!IMPORTANT]
> **Whitelisting Strategy:** I will hook `com.miui.systemui.notification.FocusNotificationManager` in the `SystemUI` process to force-allow KuroMix. This bypasses the strict Xiaomi signature/whitelist check that was causing it to show as a "Normal Notification".

## Proposed Changes

### [KuroMixHook.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/hook/KuroMixHook.kt)

#### [MODIFY] [KuroMixHook.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/hook/KuroMixHook.kt)
- **New Hook:** `com.miui.systemui.notification.FocusNotificationManager#isFocusNotificationAllowed`.
    - Intercept this method in `com.android.systemui`.
    - Force-return `true` if the calling package is `com.kuromify.kuromix`.
- This ensures the system recognizes KuroMix as an authorized "Live Activity" provider.

### [SuperIslandManager.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/notification/SuperIslandManager.kt)

#### [MODIFY] [SuperIslandManager.kt](file:///D:/KuroMix/app/src/main/java/com/kuromify/kuromix/notification/SuperIslandManager.kt)
- **Pill Logic:** Use `business: "1002"` (the standard ID for Live Activities like ride-hailing).
- **Update Logic:** Ensure `orderId` is constant (`"mirror_task"`) to allow seamless updates and cancellation.
- **Cancellation Fix:**
    - Update `cancelMirrorNotification` to send a final notification update with `"cancel": true` in the JSON.
    - This is the secret to making the pill "fly away" immediately when mirroring stops.
- **Extra Flags:** Add `miui.is_focus_notification = true` to the notification bundle.

## Verification Plan

### Automated Tests
- Build the project.

### Manual Verification
> [!IMPORTANT]
> **REBOOT REQUIRED:** These system hooks require a reboot to activate.

1.  Mirror an app.
2.  Verify a pill (Super Island) appears around the camera cutout showing the app name.
3.  Unmirror (via tile or button).
4.  Verify the pill vanishes immediately.
