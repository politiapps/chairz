# Shortcut Tray bug audit

Scope: the custom tray, its Home/gesture integration, app-store lifecycle, search, and system-bar handling. This is a source audit, not an exhaustive audit of the inherited launcher.

## Fixed

1. **High: startup race makes apps and search stay empty.** A one-time remembered app-store snapshot never observes the loader finishing. The tray now subscribes to updates and unregisters on disposal.
2. **Medium: white notification icons disappear on the light tray.** A dedicated system-UI state requests dark status icons while the tray is attached and restores the underlying state on removal.
3. **Medium: overlapping close/reopen animations.** A closing tray is no longer considered open but can remain attached. Opening a replacement now removes the closing instance first, preventing its later cleanup from clearing the new tray's status state. Closing also cancels existing animations.
4. **Low: asynchronous tab loading appears as an empty result.** Pending tabs now show progress. Tab rankings refresh across lifecycle transitions, and pausing no longer falsely reports usage access as denied.
5. **Tray apps could not be dragged to the home screen.** Long press now starts the workspace drag with an `AppInfo` source, so dropping an app creates a persistent shortcut.

## Remaining findings

- **Medium: hidden-app preferences are bypassed.** The tray consumes the raw app store and does not apply the drawer's hidden-app filter or the normal search provider's `hiddenAppsInSearch` policy. Hidden apps can therefore appear in tray tabs and search. The tray needs the same preference-aware filtering, before package deduplication.
- **Medium: matching apps can be omitted from search.** One match in the current tab suppresses all matching apps outside that tab; the grid also caps results at 14 and disables scrolling. This is explicitly encoded behavior but makes some installed apps inaccessible through broad search queries. A decision is needed about retaining the compact two-row design versus showing all matches.
- **Resolved: profile copies were deliberately collapsed.** The tray now deduplicates by package and user profile, so personal and work copies can both appear while duplicate activities within one profile remain collapsed.

## Device regression checks

- Cold-start/recreate the launcher, open the tray before model loading completes, and type an app name. Results should populate without pressing Home again.
- Install/remove an app while the tray exists, then return and search for it; the result should track the store update.
- Open over a wallpaper with white status icons: icons should turn dark over the tray, then return to the wallpaper's appearance after Home, Back, outside tap, and app launch.
- Reopen during the close animation; verify only one tray remains and dark status icons persist.
- Return from usage-access settings with access granted/revoked; verify prompt and tabs update, while installed-app search remains available.
- Check slow tab loading: progress should precede either results or a genuine empty state.

## Validation performed

- Passed `./gradlew :compileLawnWithQuickstepGithubDebugKotlin :spotlessKotlinCheck` after correcting nullable controller access.
- Passed eight targeted assertions using the production Java `SystemUiController` with a minimal stub `View`: tray contrast, navigation preservation, scrim priority, and restoration to the current underlying state.
- Added three regression cases to the existing `SystemUiControllerTest` and updated its state-count expectations. These Android/AOSP tests were not executed by the Gradle compile task.
- Passed `git diff --check`.
- Installed and launched `Chairz.16.Dev.(0f2c3cd).play.debug.apk` on the connected Samsung device. The drag gesture and drop result still need on-device interaction verification.
