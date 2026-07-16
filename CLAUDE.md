# CLAUDE.md — chairz

## 0. What this project is
`chairz` (github.com/politiapps/chairz) is a fork of [LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair), an open-source Android home-screen launcher built on AOSP Launcher3. Current base branch: `16-dev` (unstable, mid-rebase per upstream README — consider `15-dev` if things break).

Primary goal of this fork: add a **Nova Launcher–style compact app-search overlay** (search bar + Frequent/Recent/New-Updated tabs) that is *not* the standard full-screen app drawer. Feature specs for individual pieces of work are given as separate task briefs, not stored in this file.

Licence: **Apache License 2.0** (confirmed in `LICENSE.txt`; copyright AOSP 2005–2008 and Lawnchair 2024). Re-read the file before any distribution or Play Store submission if it has changed.

---

## 1. Codebase orientation (read before coding)

- `README.md` — upstream project overview, features, branch structure
- `CONTRIBUTING.md` — general reminders only. It does **not** document the package-split convention; it defers to <https://docs.lawnchair.app/developers>. The `lawnchair/` vs `src/` split below is confirmed from the tree, not from CONTRIBUTING.
- `lawnchair/src/` — Lawnchair's own Kotlin/Java code (package root `app/lawnchair/`). **New code goes here.** The `lawnchair/` dir also holds `res/`, `aidl/`, `assets/`, `protos/`.
- `src/` — cloned Launcher3/AOSP code (`com/`, `android/`). **Avoid modifying directly**; upstream merges will conflict with local changes here.
- `quickstep/` — connects Launcher3 to the Android Recents screen and other system-only behaviour.
- `lawnchair/src/app/lawnchair/qsb/providers/` — the search-provider list (`Google.kt`, `Bing.kt`, `AppSearch.kt`, …). Note `QsbSearchProvider` is a **`sealed class`**, so it is *not* an open extension point — new providers must be added inside that same package/module. It is a useful *reference* for how Lawnchair structures pluggable search, but it is built for web search engines, not app-search UI.
- Build variant: there are **three flavour dimensions** (`app`, `recents`, `channel`) — see `build.gradle:255`. `lawnWithQuickstepDebug` is **not** a real variant. The debug variant is `lawnWithQuickstepGithubDebug` (channel ∈ `github` | `nightly` | `play`; `play` is marked `isDefault`). Gradle task: `./gradlew assembleLawnWithQuickstepGithubDebug`.
- Base branch: `16-dev`. The fork's `origin` (github.com/politiapps/chairz) has **only `16-dev`**. `upstream` → `LawnchairLauncher/lawnchair` is now configured and fetched, so `upstream/15-dev` (and `9-dev` … `14-dev`) are available locally if you need to fall back.
- **As of 2026-07-14 this fork is at parity with upstream:** local `16-dev` == `upstream/16-dev` (`4844b02b33`), 0 commits ahead, 0 behind. No local modifications exist yet — so the first drawer-overlay commit is the point at which `fork-sync` starts to matter.
- `git fetch upstream` emits a fatal-looking nested-submodule error (`IconLoader` / `platform_frameworks_libs_systemui`) **that you should ignore** — the fetch itself succeeds. See `documentation/DEV_RULES.md` rule 1 before you try to "fix" it.

**Standing rule:** once you explore a part of the source for the first time, record what you found in `documentation/architecture.md` rather than re-deriving it next session. Only write down what you've actually confirmed in the code — no guesses.

---

## 2. Task workflows — invoke a skill first

Skills live in `.claude/skills/`. Create these as you formalise recurring procedures. Suggested starting set for this project (none of the previous project's skills — db-migration, mmserver-ops, election-results, etc. — apply here):

- **`gradle-build-debug`** — building/running the debug APK, diagnosing Gradle sync failures, submodule issues.
- **`drawer-overlay-feature`** — the workflow for building out custom drawer/overlay UI in this fork. Update this skill as you learn the real integration points; don't store one-off feature specs in it, just the reusable procedure.
- **`fork-sync`** — pulling upstream changes from `LawnchairLauncher/lawnchair` into this fork without clobbering local modifications.
- **`sideload-test`** — building and installing the debug APK to a physical device (e.g. via `adb install`) for manual testing.

Populate each skill file only once you've actually run the procedure once and know it works — don't write speculative steps into a skill.

---

## 3. Domain documentation (create when relevant, don't guess)

- `documentation/architecture.md` — real package/module layout once explored
- `documentation/gestures.md` — how Lawnchair's existing gesture system works and what hooks into it

If a file doesn't exist yet for a domain you're touching, create it, populate it with what you've verified from the actual code (not assumptions), and say so explicitly in your output.

---

## 4. Continuous learning loop

When you hit a real bug, a non-obvious Gradle/Kotlin/Compose quirk, or a Launcher3-specific gotcha:

1. Diagnose root cause.
2. Add a rule to `documentation/DEV_RULES.md` describing it and the fix.
3. If it affects one of the packaged skills above, update that skill too.
4. State explicitly in your output that the rule was added.

---

## 5. Things this file deliberately does NOT claim
- Exact file paths for any in-progress feature work (record these in `documentation/architecture.md` as you confirm them, not here)
- Whether `16-dev` or `15-dev` is the right long-term base (and note `15-dev` is not currently fetchable from this fork — see §1)

This file describes how to work in this repo generally. Specific task briefs (e.g. the current drawer-overlay feature) live outside this file — see the task prompt given directly to Claude Code for that session's scope.
