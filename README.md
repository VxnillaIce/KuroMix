# KuroMix

**KuroMix** is an advanced HyperOS enhancement utility designed to unlock the full potential of Xiaomi devices with dual displays (like the Mix Flip/Fold series) and provide deep system remapping for global users.

Combining **Root-level shell access** with an **LSPosed/Xposed module**, KuroMix bridges the gap between official limitations and a truly "global" user experience.

---

## 🚀 Key Features

### 📺 Intelligent Rear Display Mirroring (Quick Cast)
*   **Global App Mirroring:** Unlike the stock "Subscreen" restricted list, KuroMix allows you to mirror **any** installed application to the rear display.
*   **Precision Layouts:** Customize per-app **DPI** and **Lens Offsets** to ensure content is perfectly framed around camera lenses.
*   **Soft Wake Persistence:** A specialized background service that keeps the rear display alive using high-frequency wake broadcasts and CPU wake locks, preventing aggressive system sleep.

### 💳 Google Wallet (GPay) Hardware Remapping
*   **Native Integration:** Seamlessly replaces the restricted "Mi Pay" entry in *Settings > Gesture Shortcuts > Double press power button* with **Google Wallet**.
*   **Hardware Trigger:** Hijacks the native Mi Pay hardware shortcut to launch Google Wallet instantly from anywhere, including the lock screen.
*   **Deep Rebranding:** Hooks system resources and UI components to replace all "Mi Pay/Transport Card" strings with "Google Wallet" for a native feel.

### 🏝️ Super Island (Focus Notification) Integration
*   **Live Status:** Triggers a dynamic HyperOS "Super Island" (pill) UI at the top of your front screen when mirroring is active.
*   **Quick Control:** Expand the island to see which app is currently mirrored and access an instant "Stop Mirroring" button.
*   **Intelligent Monitoring:** A background monitor ensures the island status is always in sync with the actual state of the rear display.

### 🛡️ Aggressive Background Persistence
*   **Process Anti-Kill:** Hooks HyperOS `ProcessPolicy` to whitelist KuroMix and your mirrored apps from being frozen or killed by the system's aggressive battery management.
*   **Home Guard:** Prevents the sub-screen launcher from interrupting your mirrored apps.

---

## 🛠️ Requirements

*   **Device:** Xiaomi device running **HyperOS** or **MIUI** (Optimized for dual-display devices).
*   **Access:** **Root Access** (Magisk / KernelSU / APatch).
*   **Framework:** **LSPosed** (or compatible Xposed framework).

---

## 📦 Installation & Setup

1.  **Install the APK** and open the KuroMix app.
2.  **Grant Root Access** when prompted (or tap the status card in the dashboard to retry).
3.  **Enable the Module** in LSPosed Manager.
4.  **Configure Scope:** Ensure the following are checked in the LSPosed scope for KuroMix:
    *   System Framework (android)
    *   System UI (com.android.systemui)
    *   Settings (com.android.settings)
    *   Security Core (com.miui.securitycore)
    *   Mi Input (com.miui.miinput)
    *   NFC Service (com.android.nfc)
    *   TSM Client / Mi Pay (com.miui.tsmclient)
5.  **Reboot your device.**
6.  Use the **Restart Scoped Apps** (↻) button in the KuroMix dashboard after making changes to avoid full reboots.

---

## 📜 Credits & References

*   **UI Toolkit:** [Miuix KMP](https://github.com/compose-miuix-ui/miuix) by YuKongA.
*   **Root Shell:** [libsu](https://github.com/topjohnwu/libsu) by topjohnwu.
*   **Inspiration:** ZHITool and HyperCeiler for their pioneering work on HyperOS modification.

---

## ⚖️ Disclaimer

KuroMix is a powerful system modification tool. Use it at your own risk. The developer is not responsible for any damage, data loss, or bricked devices resulting from the use of this software.
