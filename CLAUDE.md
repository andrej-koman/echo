# Echo

Android app: live on-device speech-to-text. Kotlin, Compose, minSdk 33.

## Rules

- **Never commit.** No `git commit`, no `git push`. Andrej reviews all code. Stage nothing unless asked.
- **YAGNI.** Implement only what was asked. No speculative abstractions, no "while I'm here" extras, no config for cases that don't exist yet.
- **Few comments.** Code should read on its own. Comment only what the code cannot say: a non-obvious platform quirk, or why an unusual choice was made. No comments restating the line below them, no KDoc on obvious functions.
- **Keep this file current.** When something below stops being true, or a session turns up a fact worth having next time — a new package, a changed decision, a device quirk, a dependency that cannot be used — update the relevant section here as part of the work. Facts and decisions with their reasons, not a changelog.

## Build

```bash
./gradlew installAndRun   # build + install + launch on connected phone
./gradlew test            # unit tests, no device
./gradlew assembleDebug
```

Wireless debugging drops often: `adb connect <ip:port>` from Settings → Developer options → Wireless debugging.
`connectedAndroidTest` uninstalls the app afterwards; reinstall and re-grant the mic permission with
`adb shell pm grant dev.andrej.echo android.permission.RECORD_AUDIO`.

## Layout

```
speech/       TranscriptionEngine interface + AndroidSpeechEngine (SpeechRecognizer)
data/         Transcript, TranscriptRepository (JSON file), SettingsStore
ui/theme/     OutLoud design tokens (Color, Type, Shape, Spacing, Elevation, Motion, Theme)
ui/components/ the ported design system kit
ui/capture/   CaptureScreen — Listening and Processing
ui/record/    RecordViewModel (still named for its old screen)
ui/history/, ui/detail/
AppContainer manual DI, no Hilt
```

Engine sits behind an interface so a different backend (e.g. Whisper) can replace it without touching UI.

## Notes

- Languages come from the device via `checkRecognitionSupport()` — never hardcode locales. This phone has only `en-GB` installed; `sl-SI` is unsupported on-device.
- `SpeechRecognizer` ends on silence; `AndroidSpeechEngine` restarts sessions to stay continuous. `ERROR_NO_MATCH` is silence, not failure.
- No Room: KSP has no release matching Kotlin 2.4.10.

## Design system

Screens follow the "OutLoud" design system, authored in Claude Design (project
`2a9bb6ef-f934-49c1-847f-36e5231c11db`, file `Echo Screens.dc.html`). Read it with the
claude_design MCP rather than guessing at values.

- Tokens live in `ui/theme/` and are reached through the `EchoTheme` object
  (`EchoTheme.colors.recordLive`), not through `MaterialTheme`. Material's `ColorScheme` carries
  only the subset that maps cleanly, so stock M3 components don't clash.
- **Light only, no dynamic color.** The system defines one warm-paper palette and has no dark
  ramp, so a dark theme would be invented rather than implemented.
- Shadows are ink-tinted, never black — black reads grey against cornsilk. Use
  `Modifier.echoShadow(...)`, never `Modifier.shadow()`.
- DM Sans (UI and display) and Space Mono (durations, timers, counts only) are bundled as static
  TTFs in `res/font`. Icons are hand-authored VectorDrawables tracing lucide 0.454 — Material's
  filled glyphs do not match, and `material-icons-extended` is not worth its size.
- Ambient animation (breathing, ripples, dots, shimmer) must check `LocalReducedMotion`, which
  reads `ANIMATOR_DURATION_SCALE`. It is decoration, not feedback.
- The three content tabs (Tasks / Notes / Reminders) are stubs: the design's routing of a
  transcript into notes, todos and reminders does not exist yet. `STUB_PROCESSING_DELAY_MS` in
  `RecordViewModel` is a placeholder hold so the Processing screen is visible; delete it once
  there is real work to wait on.
