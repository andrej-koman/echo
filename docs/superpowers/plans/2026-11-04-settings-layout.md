# Settings Layout Implementation Plan

**Goal:** Unify model presentation and regroup Settings, with a working theme choice.

**Architecture:** Persist a three-value theme preference in SettingsStore, defaulting to Dark. AppContainer exposes observable Compose state so MainActivity can pass the choice to EchoTheme and SettingsScreen. SettingsScreen presents both model entries as matching rows and moves Qwen management into a sheet.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, JUnit, Gradle.

## Global Constraints

- Preserve existing model download and selection behavior.
- Default to dark theme.
- Use EchoTheme semantic tokens and existing sheets.
- Do not commit or push, per CLAUDE.md.

## Task 1: Theme preference

**Files:** `data/SettingsStore.kt`, `AppContainer.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`, theme unit test.

- [x] Add a failing test for System, Light, and Dark resolution.
- [x] Run the targeted test and confirm it fails because the preference is absent.
- [x] Add the persisted enum, app-level observable value, and theme resolution.
- [x] Run the targeted test and confirm it passes.

## Task 2: Settings layout

**Files:** `ui/profile/SettingsScreen.kt`, `EchoApp.kt`.

- [x] Replace the two model presentations with matching rows and a Qwen management sheet.
- [x] Regroup the existing controls and add the theme picker.
- [x] Remove disabled Haptics and Reduce motion rows.
- [x] Verify a debug build and review the resulting diff.

## Task 3: Guidance

**Files:** `docs/implementation-notes.md`.

- [x] Record the new theme preference and Settings grouping as lasting product decisions.
- [x] Run the smallest relevant tests and build, then inspect worktree status.
