# Changelog

## [KuroMix Update]

### Added

- **Super Island live cards** for Charging, Timer, Network Speed, and Temperature.
  - Each module has a **Split Second** (5s auto-dismiss) or **Always On** mode, selectable per module.
  - **Timer** supports a custom duration picker (minutes + seconds) that restarts the countdown on save.
  - **Timer** is always persistent — no auto-dismiss, regardless of the mode selection.
- **Rear Display mirror island.**
  - Mirroring state is now surfaced as a HyperIsland card instead of a plain notification.
  - The card shows the mirrored app's name and includes a **Stop** action.
  - Falls back to a plain notification if HyperIsland is unavailable.
- **HyperIsland notification section** in the Rear Screen settings screen, showing whether HyperIsland is enabled and explaining the consequence for mirrored apps.
- **GitHub links** in the About screen.
  - Developer, Contributors, and project rows open their respective GitHub pages on tap.
  - Uses `ArrowPreference` so the navigational affordance matches the rest of the app.
- **QS tile gating.** Both Mirror and Unmirror tiles now refuse to act when the mirror module is off, and show a toast: *"Please enable Mirroring in the app"*.
- **`SuperIslandManager.ensureChannel()`** for creating the notification channel at app startup.

### Changed

- **`RootShell.setReplaceMipayProp`** now takes a `Context` and no longer writes `double_click_power_key`. The toggle only flips `persist.kuromix.replace_mipay`; MIUI's own gesture setting is left untouched so disabling can never strand the device on `none`.
- **Mi Pay redirect** now reads `persist.kuromix.replace_mipay` instead of the old `persist.sys.kuromix.googlewallet` property.
- **HyperIsland screen.** Test Events card replaced with a Modules card; Media and Downloads entries removed.
- **About screen scroll.** Removed `fillParentMaxHeight()` from the content column, which was silently cutting off the REFERENCES section.
- **Dashboard.** Media Player entry removed; HyperIsland summary now names its actual modules.

### Fixed

- Notification channel `kuromix_hyperisland` was being created lazily and racing the first `notify()` call, producing `No channel found` errors.
- Focus actions and the close button weren't rendering on the mirror island because `notifyHyperIsland` only attached them for `LIVE_NOTIFICATION_ID` and `DOWNLOAD_NOTIFICATION_ID`. Extended the filter to include the mirror notification id.
- Duplicate close affordance on HyperIsland cards — the icon close works, the big text-button close did not. Removed the text-button close from `addFocusActionsToJson`.

### Removed

- **`RearDisplayService`** — deleted entirely. Rear display persistence is now handled by the LSPosed hook reacting to `persist.sys.kuromix.rear_keepawake`, so the foreground wake-lock service, its 250 ms broadcast loop, and its persistent notification are all gone.
- **`MediaControlTweak`** — removed. The SystemUI hook targeted class names and method signatures from HyperOS 2 that no longer exist on HyperOS 4, and the visual style it applied conflicts with Xiaomi's new Liquid Glass direction.
- **Media Player island** — the Media module has been removed from `SuperIslandManager` entirely.
  - `MediaSessionListenerService` deleted.
  - `LiveMode.MEDIA`, `startMediaUpdates`, `updateMediaIsland`, `addMediaAction`, `performMediaToggle`, `performMediaAction`, and their supporting helpers removed.
  - The `"media"` / `"music"` / `"player"` branches in `parseMode` removed.
  - The media-specific JSON branch in `addFocusActionsToJson` removed.
- **Squiggly Progress toggle** in the Media screen — no longer functional after the tweak removal.
- **Media Player row** in the Dashboard's Modules card.

## [0.1.0] - 2026-09-18

### Added

- Rear display mirroring via Quick Settings tiles.
- Per-app DPI and lens offset configuration.
- Google Wallet remapping for Mi Pay double-click.
- Aggressive background persistence (anti-kill, keep-awake).

### Fixed

- Restart scoped apps logic for SystemUI.

## [0.1.0] - 2026-09-12

### Added

- Initial release.
- Root shell wrapper around libsu.
- Xposed module entry point with HyperOS scope registration.
- Dashboard with module status card and scoped-app restart dialog.
- About screen with device info and project references.
