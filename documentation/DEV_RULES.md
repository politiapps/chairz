# DEV_RULES.md

Hard-won rules. Each entry: the symptom, the root cause, the fix. Only add rules you have actually hit and actually fixed.

---

## 1. `git fetch upstream` fails on a nested submodule — the fetch still succeeded

**Symptom**

```
Fetching submodule platform_frameworks_libs_systemui
Could not access submodule 'IconLoader' at commit 9bc92b853b
fatal: remote error: upload-pack: not our ref 3e48e8e06a...
Errors during submodule fetch:
	platform_frameworks_libs_systemui
```

**Root cause**

`platform_frameworks_libs_systemui` is a submodule of this repo, and it in turn declares a nested submodule `IconLoader`. Git recurses into it during fetch and asks that nested remote for a commit it no longer advertises (a force-push or GC upstream orphaned the ref). This is a failure *inside a nested submodule of an unrelated dependency* — it has nothing to do with the branches you were fetching.

**Fix / what to do**

Nothing. **The top-level fetch already completed.** Confirm and move on:

```sh
git branch -r --list 'upstream/*'   # branches are there
git submodule status                # no leading -/+ => working submodule is fine
```

Our checkout of `platform_frameworks_libs_systemui` stays pinned at `79cca3c2` and builds fine. Do **not** try to "repair" this by running `git submodule update --init --recursive --force` or by deleting the submodule — you will only churn a dependency that is already in a good state.

If the noise is genuinely in the way, fetch without recursing:

```sh
git fetch upstream --no-tags --recurse-submodules=no
```

---

## 2. There is no `lawnWithQuickstepDebug` variant

**Symptom**

`./gradlew assembleLawnWithQuickstepDebug` → task not found.

**Root cause**

`build.gradle:255` declares **three** flavour dimensions — `app`, `recents`, `channel` — so every variant name carries a channel segment. `channel` ∈ `github` | `nightly` | `play` (`play` is `isDefault`).

**Fix**

Use the full variant name:

```sh
./gradlew assembleLawnWithQuickstepGithubDebug
```

---

## 3. `QsbSearchProvider` is a `sealed class` — it is not an extension point

**Symptom**

You plan to "just subclass `QsbSearchProvider`" from new code and it won't compile.

**Root cause**

`lawnchair/src/app/lawnchair/qsb/providers/QsbSearchProvider.kt:19` declares it `sealed`. Kotlin confines subclasses of a sealed class to the same package and module.

**Fix**

New providers must be added *inside* `lawnchair/src/app/lawnchair/qsb/providers/`. And note this hierarchy models **web search engines**, not app-search UI — treat it as a structural reference for the drawer-overlay work, not as a base class to build on.

---

## 4. Don't check `PACKAGE_USAGE_STATS` with `checkCallingOrSelfPermission` — it always says DENIED for our build

**Symptom**

You copy the existing pattern found in `SuggestionsPreference.kt:21`, `DebugMenuPreferences.kt:143` and `QuickstepModelDelegate.java:317`:

```kotlin
context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PERMISSION_GRANTED
```

…and it reports DENIED even after the user has visibly granted usage access in system settings, so Frequent/Recent lists silently come back empty.

**Root cause**

`PACKAGE_USAGE_STATS` is `signature|privileged|appop`. `checkCallingOrSelfPermission` only reflects the *permission* grant, which a non-privileged (sideloaded) app can never obtain. The user-facing toggle in Settings → Special app access → Usage access flips an **appop**, not the permission. The call sites above are inherited AOSP code that assumes the launcher is a privileged system app — that assumption does not hold for our sideloaded debug builds.

**Fix**

Read the appop instead — see `lawnchair/src/app/lawnchair/shortcuttray/UsageAccess.kt`:

```kotlin
val appOps = context.getSystemService(AppOpsManager::class.java)
val mode = appOps.unsafeCheckOpNoThrow(       // checkOpNoThrow below API 29
    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
)
val granted = mode == AppOpsManager.MODE_ALLOWED
```

The permission still has to stay declared in `lawnchair/AndroidManifest.xml:23` (it already is) — the appop is only consulted for packages that declare it. There is no runtime-permission dialog; you must deep-link to `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

---

## 5. `AbstractSlideInView` is bottom-anchored only — don't use it for a top sheet

**Symptom**

You want a panel that slides down from the top of the screen, so you extend `AbstractSlideInView` (as `ComposeBottomSheet` does) and flip `gravity` to `Gravity.TOP`. The panel renders, but drag-to-dismiss is inverted: dragging *down* (into the screen) dismisses it, and dragging up does nothing.

**Root cause**

`src/com/android/launcher3/views/AbstractSlideInView.java` hard-codes the bottom-sheet convention throughout: the swipe detector is created with `SingleAxisSwipeDetector.VERTICAL` and `DIRECTION_NEGATIVE` (line ~271), and `onDragEnd` treats `velocity > 0` (downward) as "dismiss" (line ~441). `setTranslationShift` also assumes the content hides by moving *down*. Gravity alone does not invert any of that.

**Fix**

For a top-anchored panel, extend `AbstractFloatingView` directly and do your own slide, as `lawnchair/src/app/lawnchair/shortcuttray/ShortcutTrayView.kt` does. You give up the built-in swipe-to-dismiss (dismiss via back / tap-outside / re-trigger instead), but you avoid fighting the framework. Reuse `TYPE_COMPOSE_VIEW` as the floating-view type so the standard `closeAllOpenViews` paths still close it.

---

## 6. Pressing Home while already on the home screen is `Launcher.handleHomeTap()`

**Symptom**

You want Nova-style "press Home on the home screen to reveal a panel" and go looking for a key/intent hook.

**Root cause / what's actually there**

`src/com/android/launcher3/Launcher.java:1631` already calls the overridable `handleHomeTap()` from `onNewIntent`, but only when `alreadyOnHome && isInState(NORMAL) && AbstractFloatingView.getTopOpenView(this) == null` **and** the workspace is already on the default page (otherwise it jumps to the default page first). `LawnchairLauncher` already overrides it to run the home-press gesture handler.

**Consequence you get for free:** because Launcher computes those conditions *before* calling `closeAllOpenViewsExcept`, an already-open floating panel is closed by that path and `handleHomeTap()` is *not* called — so Home naturally toggles the panel shut rather than reopening it. Don't add your own toggle logic on top.

---

## 7. `AllAppsStore.apps` holds several `AppInfo`s for one package — dedup or you show ghosts

**Symptom**

An app appears twice in the Shortcut Tray (e.g. "FitApp" twice; "Play Store" twice in Recent). Tapping the *second* copy opens something that behaves differently — e.g. isn't signed in.

**Root cause**

`launcher.appsView.appsStore.apps` (`Array<AppInfo>`) is not one-entry-per-package. The same package legitimately contributes multiple `AppInfo`s when it (a) exports more than one LAUNCHER activity — a main entry point plus a secondary/login one — or (b) is installed in more than one profile (personal + work), each a distinct `UserHandle`. The full drawer intentionally shows them all. The tray's Frequent/Recent tabs rank by `packageName`, so both copies inherit the same usage value and both survive — and the "wrong" one launches a secondary component/profile.

**Fix**

Collapse to one entry per package in `ShortcutTrayRepository.toTrayApps`, preferring the personal-profile main launcher activity: group by `packageName`, and within a group pick `user == Process.myUserHandle() && targetComponent == pm.getLaunchIntentForPackage(pkg)?.component` (with sensible fallbacks). That guarantees a tap opens the same thing the home screen would. Do the dedup on the *base* list so every tab inherits it.

---

## 8. `PopupContainerWithArrow.showForIcon` needs a real, laid-out `BubbleTextView` anchor

**Symptom**

You want the home-screen long-press menu (app shortcuts, App info, Uninstall, Pause, Widgets…) on a Compose-rendered icon, but `showForIcon` takes a `BubbleTextView` and positions itself from that view's bounds — a Compose `Image` is not one.

**Root cause**

`src/com/android/launcher3/popup/PopupContainerWithArrow.java:527` `getTargetObjectLocation` calls `getDescendantRectRelativeToSelf(mOriginalIcon, …)`, so the anchor must be an actual `View` descendant of the drag layer with valid bounds and a non-null `getIcon()`.

**Fix (used by `ShortcutTrayContent.showAppPopup`)**

Inflate a throwaway `R.layout.all_apps_icon` (`BubbleTextView`), `applyFromApplicationInfo(appInfo)`, set it `INVISIBLE`, and add it to `launcher.dragLayer` positioned over the tapped cell. Convert the cell's Compose `boundsInWindow()` to drag-layer-local coords by subtracting `dragLayer.getLocationInWindow(...)`. Then **`anchor.post { PopupContainerWithArrow.showForIcon(anchor) }`** — posting waits one layout pass so the anchor has real bounds (same pattern as `TaskbarActivityContext`). Remove the anchor on the popup's `onViewDetachedFromWindow`. This reuses Launcher3's public entry point with zero edits to `src/`.
