---
name: sideload-test
description: Build the chairz debug APK and install it on the connected phone as the single launcher install. Use whenever installing a build to the device for manual testing.
---

# Sideload chairz to the test phone

Verified 2026-11-07 on the Samsung test device (serial R5CXA1HVTDX).

## Rules

- Exactly one chairz install: `app.chairz.debug` (github debug variant) in the personal profile (user 0). It holds the HOME role.
- Never install the `play` variant (`app.chairz.play.debug`) – it is a separate app, so fixes in it never reach the launcher in use.
- Always pass `--user 0`. A plain `adb install` also installs into the work profile (user 10).

## Steps

1. Build:
   `./gradlew assembleLawnWithQuickstepGithubDebug -q`
2. Install:
   `adb install -r --user 0 "build/outputs/apk/lawnWithQuickstepGithub/debug/Chairz.16.Dev.(<sha>).github.debug.apk"`
3. Verify a single install and the home role:
   ```sh
   for u in 0 10; do adb shell pm list packages --user $u | grep -iE "chairz|lawnchair"; done
   adb shell cmd role get-role-holders android.app.role.HOME   # expect app.chairz.debug
   adb shell dumpsys package app.chairz.debug | grep -m1 lastUpdateTime
   ```
   Expected: only `package:app.chairz.debug` under user 0, nothing under user 10.
4. Clean up strays if they appear:
   `adb uninstall app.chairz.play.debug` and `adb shell pm uninstall --user 10 app.chairz.debug`.

## Reproducing the cold-start tray race

Kill the launcher in the background, then press Home twice 0.9 s apart:
```sh
adb shell am start -a android.settings.SETTINGS; adb shell am kill app.chairz.debug
adb shell 'input keyevent KEYCODE_HOME; sleep 0.9; input keyevent KEYCODE_HOME'
adb shell 'sleep 3'; adb shell input text sla
```
The tray should show results and the keyboard. `adb logcat | grep -E "Displayed app.chairz|setApps"` shows the load gap (see DEV_RULES rule 13).
