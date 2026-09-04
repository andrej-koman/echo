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
ai/           LlmRunner interface + MlKitLlmRunner (Gemini Nano) and LiteRtLlmRunner (bundled
              Qwen3), ModelStore/HttpModelDownloader for the weights, AnalysisPrompt,
              TranscriptAnalyzer, AnalysisOutcome/AnalysisBlock, PendingAnalysisWorker
data/         Transcript, TranscriptRepository (JSON file), TodoItem + DerivedRepository
              (derived.json), PendingAnalysis + AnalysisQueueRepository (pending_analysis.json),
              SettingsStore, AuthStore
auth/         AuthRepository interface + GoogleAuthRepository (Credential Manager)
ui/theme/     OutLoud design tokens (Color, Type, Shape, Spacing, Elevation, Motion, Theme)
ui/components/ the ported design system kit
ui/home/      HomeScreen + HomeViewModel — up next, recent notes
ui/tasks/     TasksScreen + TasksViewModel — grouped by source transcript, done checkbox
ui/capture/   CaptureScreen — Listening and Processing
ui/record/    RecordViewModel (still named for its old screen)
ui/auth/      SignInScreen, AccountScreen, AuthViewModel, AiViewModel
ui/transcripts/, ui/detail/    also NotesScreen — reuses the transcript row rendering
AppContainer manual DI, no Hilt
```

Engine sits behind an interface so a different backend (e.g. Whisper) can replace it without touching UI.
`AuthRepository` is behind an interface for the same reason — Google is the only provider today.

Four tabs: Home / Tasks — record button — Notes / Transcripts, Home the start destination. The record
button sits in the bar's middle slot (`TabBar(centerGap = true)` leaves the gap, `EchoApp` overlays the
button on a bar-coloured cradle so it pokes above the hairline). The tab bar is icon-only — no labels, no
selection crossfade — and the NavHost runs with every transition set to `None`: the design's
screen changes read as jank on device, so switching is a hard cut. Tab switches go through `NavHostController.selectTab`: Home pops back to Home, other tabs
`popUpTo(HOME)` + `launchSingleTop`. Never `saveState`/`restoreState` — the saved tab stack keys to
Home, so tapping Home restored it and landed on the last tab. Cost: tab scroll position is not kept.
`EchoApp` gates on `AuthState` outside the
NavHost, so signing out drops the whole graph rather than unwinding a back stack.

## Notes

- Languages come from the device via `checkRecognitionSupport()` — never hardcode locales. This phone has only `en-GB` installed; `sl-SI` is unsupported on-device.
- `SpeechRecognizer` ends on silence; `AndroidSpeechEngine` restarts sessions to stay continuous. `ERROR_NO_MATCH` is silence, not failure.
- No Room: KSP has no release matching Kotlin 2.4.10.
- Sign-in needs `google_web_client_id` in `res/values/auth.xml` — the OAuth **web** client ID,
  plus an Android client ID registered with this package and signing SHA-1. Left blank in the
  repo: `GoogleAuthRepository` then reports itself unconfigured instead of failing opaquely.
- No server verifies the Google ID token; the account is a local label. `GoogleIdTokenCredential`
  has no email field of its own — `id` is the email address for Google accounts.
- `TranscriptsViewModel.title()` derives a title from the opening of the text (first sentence, or
  six words) — the fallback for a transcript analysis has not named yet, not a permanent scheme.
  `Transcript.asRow()` prefers `title`/`summary` when present.
- Task and Reminder unified into one `TodoItem` (`text`, `dueAt` — never null, `hasTime`,
  `done`). A speaker who names no date at all still gets an item due **today**; `hasTime`
  records only whether a clock time was stated. `resolveDue` (in `ai/AnalysisPrompt.kt`)
  resolves this: `LocalDateTime.parse` succeeding means a timed item, `LocalDate.parse`
  succeeding means a date-only item, and anything else — an unresolved relative phrase, a
  malformed stamp, absent entirely — falls back to today's start of day, untimed. That fallback
  uses wall-clock "today" at parse time (`TranscriptAnalyzer` passes `LocalDate.now(zone)`
  down), deliberately not the transcript's own day: re-analysing a month-old note should still
  resolve *its* relative phrases against that note's day (the prompt's "Today is …" line, driven
  by `transcript.createdAt`), but an item with no named date is due now, not a month ago.
  `TasksScreen` still groups items by `sourceTranscriptId` (regrouping by due date is a later
  step); `TasksUiState.aiOff` is keyed off `items.isEmpty()`.
- Old-shape `derived.json` (the pre-unification `tasks`/`reminders` lists) is not migrated —
  it decodes to zero rows via `Json { ignoreUnknownKeys = true }` plus a defaulted `items`
  field, and a re-Analyze regenerates. `derivedId` no longer takes a `kind` — one entity, one
  id — but keeps the null-byte separator between transcript id and text: a plain
  concatenation would let `"t1"` + `"x"` collide with `"t"` + `"1x"`.
- Home's "Up next" is undone `TodoItem`s sorted by `(dueAt, hasTime descending)`, capped at
  three (`HomeViewModel.buildUpNext`). The section disappears rather than rendering an empty
  card when there is nothing due.
- Rows carry `updatedAt` and transcripts carry a `deletedAt` tombstone — `delete()` marks, the
  `transcripts` flow filters. Both default off `createdAt`/null, so files written before them still
  parse. Sync is not built and is not planned yet; these exist so a delete and a last-write-wins
  merge stay possible later, when a hard delete would already have resurrected rows.
- Derived rows are keyed by `UUID.nameUUIDFromBytes(transcriptId + kind + normalized text)`, not
  minted fresh, so re-analysis lands on the same ids and `Task.done` survives it. Before this,
  Analyze silently un-ticked every task. Two rows with the same text under one transcript collapse
  to one (`distinctBy`) — a repeated task from a single note is noise.
- `TranscriptDetailScreen`'s Analyze button re-runs `TranscriptAnalyzer` through
  `TranscriptsViewModel.analyze()`, for transcripts recorded before analysis existed or a failed
  run. One in flight at a time (`analyzingId`, sourced from `PendingAnalysisWorker.activeId`),
  disabling the button under it.
- **Pending analysis queue.** `TranscriptAnalyzer.analyze()` returns `AnalysisOutcome`
  (`Success` or `Blocked(AnalysisBlock)`) instead of a nullable `Analysis`, so a failed analysis
  carries a reason instead of vanishing: `EmptyTranscript`, `WaitingForModel(bytes)`,
  `NoBackend(reason)`, `GenerationFailed(message)`, `EmptyResult`. `WaitingForModel` and
  `NoBackend` are preconditions, not failures — a transcript parked on either never exhausts its
  retries. `PendingAnalysisWorker` owns the drain: it reads `AnalysisQueueRepository`
  (`pending_analysis.json`, mirrors `JsonTranscriptRepository`'s disk safety), runs
  `TranscriptAnalyzer` against each entry, and either attaches the analysis or records the block.
  `GenerationFailed`/`EmptyResult` consume one of 3 attempts; past that an entry sits unretried
  until a manual Analyze. The worker runs on `AppContainer`'s `appScope`, not a ViewModel scope —
  navigating away no longer cancels an in-flight analysis, which used to happen with
  `TranscriptsViewModel`'s old `_analyzingId` on `viewModelScope`. Three triggers call
  `worker.requestDrain()`: `RecordViewModel.stopRecording()` after saving (awaited inline, so the
  Processing screen's perceived flow is unchanged), `AppContainer`'s collector on
  `modelStore.state` (collapsed to the state's kind, not raw equality, so download progress ticks
  don't retrigger it), and `MainActivity.onStart()` via `container.onAppForegrounded()`. Both of
  the latter two also call `LlmRunnerProvider.invalidate()` first — without it a resolved
  `NoLlmRunner` answer stays cached for the process's life even after a model finishes
  downloading. `TranscriptsViewModel.analyze()` (the manual button) calls
  `worker.analyzeNowAsync()`, which bypasses both the attempt cap and the "already analyzed"
  drop that the automatic drain applies — an explicit tap always gets a fresh run. **No
  WorkManager**: it would need a custom `WorkerFactory` (no Hilt here), and Nano's
  foreground-only restriction already rules out a background retry loop — the triggers above are
  the only times a retry can usefully happen. **No timed backoff**: drains are event-triggered,
  not a polling loop, so there is nothing to back off *from*; the attempt cap alone prevents
  churn. `TranscriptsViewModel.pending`/`HomeViewModel` and `TranscriptsViewModel.group()` turn
  each queue entry's `lastBlock` into a `PendingCopy` (`ui/transcripts/TranscriptsViewModel.kt`)
  — a plain `{text, severity}` pair, no Compose types, so it stays testable without a device. An
  entry only gets copy once the drain has actually attempted it (`lastBlock != null`); a
  freshly-enqueued row shows nothing for the moment before that. `PendingBadge` (in
  `ui/transcripts/TranscriptsScreen.kt`, reused by Home and the detail screen) renders it as an
  `EchoBadge`: `Parked` (waiting on a model or backend) in `BadgeTone.Neutral`, `Failed`
  (`GenerationFailed`/`EmptyResult`, exhausted or not — the badge doesn't distinguish) in
  `BadgeTone.Warning`. `EmptyTranscript` never renders — it's never enqueued. No dedicated
  first-run modal; the badge is the only affordance, on transcript rows (Home and Transcripts),
  recent-note cards, and the detail screen, which already has the Analyze button to retry with.

## Design system

Screens follow the "OutLoud" design system, authored in Claude Design (project
`2a9bb6ef-f934-49c1-847f-36e5231c11db`). Two design files: `Echo Screens.dc.html` (global
modules, core screens, capture flow, detail, empty states) and `Echo Transcripts.dc.html`
(the archive and the transcript detail). Read them with the claude_design MCP rather than
guessing at values.

Neither file has a sign-in artboard — `SignInScreen` and `AccountScreen` are composed from the
tokens and kit, not ported from a design.

- Tokens live in `ui/theme/` and are reached through the `EchoTheme` object
  (`EchoTheme.colors.recordLive`), not through `MaterialTheme`. Material's `ColorScheme` carries
  only the subset that maps cleanly, so stock M3 components don't clash.
- **Light only, no dynamic color.** The system defines one warm-paper palette and has no dark
  ramp, so a dark theme would be invented rather than implemented.
- Shadows are ink-tinted, never black — black reads grey against cornsilk. Use
  `Modifier.echoShadow(...)`, never `Modifier.shadow()`.
- DM Sans (UI and display) and Space Mono (durations, timers, counts only) are bundled as static
  TTFs in `res/font`. The mascot is a raster `drawable-xxxhdpi/mascot_echo.png` (the polished
  artwork) on a square 1024px canvas with the ghost centred in it — `Mascot` renders it with
  `ContentScale.Fit` into a square, so a non-square canvas both shrinks and offsets him. Three
  prop variants sit beside it (`mascot_tasks`, `mascot_notes`, `mascot_transcripts`) and are
  selected with `Mascot(variant = ...)` / `EmptyState(variant = ...)`: each per-tab screen uses its
  own variant in both the top bar's leading slot and its empty state. `MascotVariant.Plain` is the
  default everywhere else. `mascot_echo` is
  also the adaptive launcher foreground inside a 52dp layer-list box, which puts ~42dp of
  ghost inside the 72dp launcher mask; at the old 66dp his tail clipped. The monochrome launcher
  layer stays a vector, and its `<group>` scale/translate is tuned so its bounds match the
  raster foreground's to within a fraction of a dp — change one and re-measure the other. The
  launcher background is `Paper050` (`#FFFDF5`), not the app's `Paper100` page cornsilk: at
  launcher size the darker cornsilk reads as a dull beige tile. Icons
  are hand-authored VectorDrawables tracing lucide 0.454 — Material's filled glyphs do not
  match, and `material-icons-extended` is not worth its size.
- Ambient animation (breathing, ripples, dots, shimmer) must check `LocalReducedMotion`, which
  reads `ANIMATOR_DURATION_SCALE`. It is decoration, not feedback. `Waveform` is the exception:
  its bars are a scrolling history of the real mic level, so they keep moving regardless.
- Haptics live in `ui/theme/Haptics.kt`, reached with `rememberEchoHaptics()`. Four calls:
  `tick()` (every button, icon button and tab tap), `engage()` / `release()` (record button down
  and stop), `success()` (the take is filed away, on leaving Capture). Everything but `success()`
  goes through `View.performHapticFeedback`, which already honours the system haptic setting;
  `pulse()` beats once per `SPINNER_PERIOD_MS` under the Processing screen, so the haptic and the
  outer spinner arc share one period; it is ambient, so it checks `LocalReducedMotion`.
  `success()` needs `VibrationEffect.EFFECT_DOUBLE_CLICK` and so needs the `VIBRATE` permission
  and its own check of `Settings.System.HAPTIC_FEEDBACK_ENABLED`.
- `EchoTopBar` has two forms: a `title`/`subtitle` one and a slot one taking arbitrary centre content
  (Home puts the search field there instead of a title). Both draw the same bar chrome, so every screen
  header keeps one surface, hairline and height.
- Home reads its recent notes from `TranscriptsViewModel`; it has no view model of its own. With no
  transcripts it shows only the mascot empty state (its "Upload audio" / "Type a note" buttons are
  inert); with data it is two sections, "Up next" then "Recent notes" — no greeting, no action cards.
  "Up next" is `StubUpNext`, three hardcoded items, until transcripts are routed into todos and
  reminders. Recent note cards carry no tag pill: `Transcript` has no tag field.
- The two content tabs (Tasks / Notes) are still stubs, but the data behind them
  is real: `RecordViewModel.stopRecording` now saves the transcript and then runs
  `TranscriptAnalyzer`, writing `TodoItem` rows and a title and summary back onto the
  transcript. The Processing screen holds for that work — `STUB_PROCESSING_DELAY_MS` is gone.
  The transcript is saved *before* analysis, so no model failure can cost a recording.

## On-device AI

Analysis runs on whichever backend the device has, picked once per process by `LlmRunnerProvider`
(first usable wins; `NeedsDownload` still beats the next candidate — the download is the user's
call). One prompt and one parser serve both, so a new backend costs one class.

- **Gemini Nano is not available on the S24.** AICore is installed
  (`0.release.samsungslsi.prod_aicore_20260723`) but `checkStatus()` *throws*
  `[606] FEATURE_NOT_FOUND: Feature 636 is not available`. Google's device list starts Samsung at
  the S25; blog posts claiming S24 support are from the retired AI Edge dev preview. Unsupported
  devices throw rather than return `UNAVAILABLE`, so the `GenAiException` catch in
  `MlKitLlmRunner.availability()` is load-bearing — without it every non-Nano phone crashes.
- **Nano is foreground-only** (`BACKGROUND_USE_BLOCKED`; foreground services do not qualify).
  That is why analysis lives on the Processing screen and there is no service or WorkManager.
- Fallback is Qwen3-1.7B int4 `.litertlm` (977MB) via LiteRT-LM. The format must be `.litertlm`,
  never GGUF — the wrong format fails silently on load. Gemma repos on HuggingFace are
  `gated: auto` and 401 an anonymous download; `litert-community/Qwen3-1.7B` is not gated.
- `Engine.initialize()` costs ~19s against ~5s of inference, so it is held open for the life of
  the process and warmed up when Capture opens, from an application-scoped coroutine in
  `AppContainer` — leaving Capture must not cancel a half-finished init.
- `ModelStore.delete()` sweeps every file prefixed with the model's name: LiteRT-LM writes a
  `..._mldrift_weight_cache.bin` beside the weights that is dead without them.
- MediaPipe LLM Inference is maintenance-only; LiteRT-LM replaces it.
