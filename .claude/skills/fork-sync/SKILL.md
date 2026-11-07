---
name: fork-sync
description: Merge upstream LawnchairLauncher/lawnchair 16-dev (and its systemui submodule) into the chairz fork without losing the Chairz rebrand or local features.
---

# Sync chairz with upstream Lawnchair

Verified 2026-11-07 (merged 241 commits, `7d9e039594`).

## 0. Prepare
- Commit local work first. Leave `.idea/vcs.xml` alone (IDE noise).
- Back up the phone's launcher data (debug builds allow `run-as`):
  `adb exec-out run-as app.chairz.debug tar cf - databases shared_prefs files > ~/Documents/chairz-backups/app.chairz.debug-$(date +%Y%m%d-%H%M).tar`
- Remotes (add if missing):
  - main repo: `upstream` → https://github.com/LawnchairLauncher/lawnchair.git
  - submodule `platform_frameworks_libs_systemui`: `upstream` → https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui.git (origin is the politiapps fork, which carries a Gradle 9 `=` assignment commit).
- Fetch with `git fetch --no-recurse-submodules upstream 16-dev`.

## 1. Submodule first
```sh
cd platform_frameworks_libs_systemui
git fetch upstream
git checkout -B 16-dev <current pointer>
git merge --no-edit <upstream pointer>   # pointer = git ls-tree upstream/16-dev platform_frameworks_libs_systemui (from the main repo)
```

## 2. Main repo, on a branch
```sh
git checkout -b sync/upstream-$(date +%F)
git merge --no-ff --no-commit upstream/16-dev
```
Expected conflicts:
- **Translations** (`lawnchair/res/values*/strings.xml`) – take upstream, then re-apply the rebrand to text only, keeping copyright headers as Lawnchair (Apache 2.0 requires retaining notices):
  ```sh
  git diff --name-only --diff-filter=U | grep 'res/values' | xargs git checkout --theirs --
  for f in lawnchair/res/values*/strings.xml; do perl -i -pe 'if (/Copyright/) { s/Chairz/Lawnchair/g } else { s/Lawnchair/Chairz/g }' "$f"; done
  ```
  Check: `git grep -l 'Copyright.*Chairz'` must be empty.
- **build.gradle** – version comment only; keep the Chairz name.
- **Submodule pointer** – `git add platform_frameworks_libs_systemui` after step 1.

## 3. Build, install, verify
- `./gradlew assembleLawnWithQuickstepGithubDebug -q` (after Gradle/AGP bumps it can take more than 10 min, so run it in the background).
- Install with the `sideload-test` skill (`--user 0` only). The versionCode must not go down, or `adb install -r` refuses the downgrade.
- Check the home layout, then the cold-start tray race in `sideload-test`.

## 4. Commit and push
- Commit the merge on the sync branch. Pushing the submodule merge to the politiapps fork is required before anyone else (or CI) can check out the new pointer.
