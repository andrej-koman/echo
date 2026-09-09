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
ui/home/      HomeScreen + HomeViewModel — hero task, then/coming-up strip, latest note
ui/tasks/     TasksScreen + TasksViewModel — grouped by source transcript, done checkbox
ui/capture/   CaptureScreen — Listening and Processing
ui/record/    RecordViewModel (still named for its old screen)
ui/auth/      SignInScreen, AccountScreen (the Profile tab), AuthViewModel, AiViewModel
ui/transcripts/, ui/detail/    NotesScreen (the merged Notes tab — one shared card per day,
              hairline dividers, same rhythm as Tasks; search; TranscriptRow, PendingBadge),
              TranscriptDetailScreen (NOTE + tasks-from-this-note + raw TRANSCRIPT, bar-icon
              actions)
AppContainer manual DI, no Hilt
```

Engine sits behind an interface so a different backend (e.g. Whisper) can replace it without touching UI.
`AuthRepository` is behind an interface for the same reason — Google is the only provider today.

Four tabs: Home / Tasks — record button — Notes / Profile, Home the start destination. There is no
separate Transcripts tab — Notes is the one browsable list (former Transcripts content merged into it,
`ui/transcripts/NotesScreen.kt`), and raw transcript text lives only in Profile's read-only
"RAW TRANSCRIPTS" section (`AccountScreen.kt`), not as a tab of its own. No header account icon
button anywhere — Profile is reached only via its tab (Tasks' empty-state "Turn on AI" button also
tab-switches there, via `onOpenProfile`). The record
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
  `Transcript.asRow()` prefers `title`/`summary` when present. `summary` is the AI's full
  reformatted note (a readable rewrite of the ramble, not a short blurb) — the prompt in
  `ai/AnalysisPrompt.kt` asks for this explicitly. Row excerpts and `TranscriptDetailScreen`'s
  NOTE section both just render it as-is; nothing truncates or re-summarizes it further.
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
  `buildAnalysisPrompt` takes the transcript's own `createdAt` as a `LocalDateTime`, not just a
  `LocalDate` — the prompt states both "Today is …" *and* "the current time is …" so the model
  has something to add a spoken duration to. Without the current time, "in 20 minutes" is
  unanswerable and the on-device model was observed inventing an arbitrary hour (`02:20` for a
  note recorded at 11am) rather than refusing. `parseAnalysis` also repairs one specific
  small-model JSON slip seen on-device: writing `]` where it means to close an item object
  (`"due":"..."]]}` instead of `"..."}]}`), applied only as a fallback when the brace count is
  short by exactly one — a genuinely different malformed response still returns null rather
  than being guessed at.
  `TasksScreen`/`TasksViewModel.groupByDue` groups undone items into three buckets only: **Today**
  (folds in every overdue item too, sorted ahead of the rest of today's), **Tomorrow**, and one
  flat **Upcoming** group for everything later (`DueGroup.showDate = true` there, so `TaskRow`
  formats its "when" label with `formatDue` instead of `formatTime`). Done items never appear in
  `groupByDue`'s output at all — they collapse into a separate "Completed" section instead
  (`completedByRecency`, most recently checked off first), shown behind a `CompletedHeader`
  toggle at the bottom of the list so a long history of ticked items doesn't push undone work off
  screen. `isOverdue` treats timed and date-only items differently: a timed item is overdue the
  moment its clock time passes, even earlier today, but a date-only item due today isn't overdue
  until tomorrow. Within a group, timed items come first (soonest first), then date-only items
  (oldest-created first). Tapping a row opens `EditTaskScreen` (`onEditTask`) instead of jumping
  straight to the source transcript — that full-screen editor lets you retitle a task, change its
  day/time via native `DatePickerDialog`/`TimePickerDialog`, toggle notification on/off, delete it
  (`DerivedRepository.update`/`delete`, added alongside `replaceFor`/`setDone`/`deleteFor`, using
  the same `update { current -> ... }`/`sync` machinery), and reach the source transcript only
  from its own "Heard in" row. `TasksViewModel` takes a `TranscriptRepository` for that lookup, on
  top of `DerivedRepository`. Due-time formatting (`formatDue`, `formatTime`, `Long.midnight()`)
  lives in `ui/DueLabels.kt`, shared by Home and Tasks rather than copied a third time.
  `TasksUiState.aiOff` is keyed off `items.isEmpty()` (before the search filter, so searching
  never flips it).
  Search is a `query` `MutableStateFlow` folded into each of Home/Tasks/Notes' `combine` before
  grouping (`filterByQuery` for Tasks). All three tabs share one `EchoTopBar` overload
  (`title`/`query`/`onQueryChange`/`searchPlaceholder`) that toggles a trailing search icon into an
  inline `EchoSearchField` in place of the title, rather than each screen keeping its own
  always-visible field.
- Old-shape `derived.json` (the pre-unification `tasks`/`reminders` lists) is not migrated —
  it decodes to zero rows via `Json { ignoreUnknownKeys = true }` plus a defaulted `items`
  field, and a re-Analyze regenerates. `derivedId` no longer takes a `kind` — one entity, one
  id — but keeps the null-byte separator between transcript id and text: a plain
  concatenation would let `"t1"` + `"x"` collide with `"t"` + `"1x"`.
- **Home ("1b reworked")** puts one task in a hero slot rather than a list. `HomeViewModel.buildState`
  reuses `ui.tasks.groupByDue`/`isOverdue` (both `internal`, not `private`, for this reuse): the hero
  is the first item of the "Today" group (overdue items sort first, so an overdue task always wins
  the slot over a same-day timed one; a timed item wins over an untimed one). Its action pill is
  context-dependent: `Snooze to tonight` (bg `surfaceAccentSoft`) when overdue — `HomeViewModel.snooze`
  moves `dueAt` to 20:00 local time today via `DerivedRepository.update`, nothing fancier — or `Add a
  time` (solid `actionPrimaryBg`, the bell icon, "the one thing that makes the task ring") when the
  hero has no time set, navigating to `EditTaskScreen` like every other "no time set" affordance.
  When the Today group is empty, the hero slot becomes a mascot "Today is clear" card instead
  (`HeroState.Clear`) and the strip below relabels itself "Coming up" (next `Tomorrow`+`Upcoming`
  items, dated rather than timed) instead of "Then". The "Then"/"Coming up" strip below the hero
  reuses `ui.tasks.TaskRow`/`TaskCheckbox` directly (both loosened to `internal`, `TaskCheckbox` given
  a `size` param for the hero's larger 28dp circle) — same checkbox, same icon/colour rules, so ticking
  a task behaves identically whether it's tapped from Home or Tasks. The strip caps at two rows,
  timed items prioritized; any additional untimed items collapse into a single "+N more today with no
  time set" row rather than being shown individually — `HomeUiState.thenMoreUntimed`/`thenTotalCount`
  reconcile against the "See all N" link. A "N notes still need analyzing" pill (only counting
  `PendingSeverity.Parked`, not `Failed`, entries — those already say "failed" in the badge) sits above
  the hero when the queue has any. The "Latest note" card at the bottom reuses
  `Transcript.asRow` from `ui.notes.NotesViewModel` (loosened to `internal`) but replaces its
  absolute `HH:mm` with a relative stamp ("12 MIN AGO", "YESTERDAY, 18:04") computed in
  `HomeViewModel` — that reformatting only makes sense for the single most-recent note, not the
  Notes list, so it stays local rather than changing `NoteRow` itself. Home dropped its own search
  field with this rework (nothing left on the screen is a list to filter); the header's search icon
  and the "Coming up"/"See all"/"All notes" links all just switch tabs.
- Rows carry `updatedAt` and transcripts carry a `deletedAt` tombstone — `delete()` marks, the
  `transcripts` flow filters. Both default off `createdAt`/null, so files written before them still
  parse. Sync is not built and is not planned yet; these exist so a delete and a last-write-wins
  merge stay possible later, when a hard delete would already have resurrected rows.
- Derived rows are keyed by `UUID.nameUUIDFromBytes(transcriptId + kind + normalized text)`, not
  minted fresh, so re-analysis lands on the same ids and `Task.done` survives it. Before this,
  Analyze silently un-ticked every task. Two rows with the same text under one transcript collapse
  to one (`distinctBy`) — a repeated task from a single note is noise.
- `TranscriptDetailScreen` (redesigned from the OutLoud "Transcript Page" doc): the bar carries
  back / delete / re-analyze as icons instead of three stacked bottom buttons, so the body is
  content, not chrome. Re-analyze (`ic_audio_waveform`) re-runs `TranscriptAnalyzer` through
  `TranscriptsViewModel.analyze()`; it goes `IconButtonVariant.Ghost` and no-ops while the entry's
  `PendingCopy.severity` is `Parked` (nothing to retry yet — `WaitingForModel`/`NoBackend` are
  preconditions), otherwise `Soft` (bronze) and calls `analyze()`. One in flight at a time
  (`analyzingId`, sourced from `PendingAnalysisWorker.activeId`). Body: NOTE (the summary) then a
  "TASKS FROM THIS NOTE" section reusing `TaskGroupCard` from `ui/tasks/TasksScreen.kt` — made
  `internal` (was `private`) so the detail screen can render the same rows, checkbox and
  bronze/clay time labels as the Tasks tab, tapping one going to `EditTaskScreen`
  (`sourceTitleFor = { null }` since the source is already the screen you're on). Below that,
  "WHAT WAS SAID": the raw transcript clamped to 4 lines with a "Show all"/"Show less" toggle
  (`remember(text)`, plain `maxLines` swap — no CSS line-clamp equivalent needed) and a "Copy"
  chip writing to `ClipboardManager`. A `pending` entry (parked or failed) replaces NOTE/TASKS
  with a status card (icon avatar + copy + a retry button when `Failed`) — analysis hasn't
  produced a note yet, so there is nothing to show there. `TranscriptsViewModel.tasks` (all
  `DerivedRepository.items`, unfiltered) backs both this screen's per-transcript filter and the
  Notes list's per-row task counts.
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
  `ui/transcripts/NotesScreen.kt`, reused by Home and the detail screen) renders it as an
  `EchoBadge`: `Parked` (waiting on a model or backend) in `BadgeTone.Neutral`, `Failed`
  (`GenerationFailed`/`EmptyResult`, exhausted or not — the badge doesn't distinguish) in
  `BadgeTone.Warning`. `EmptyTranscript` never renders — it's never enqueued. No dedicated
  first-run modal; the badge is the only affordance, on transcript rows (Home and Notes),
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
- `EchoTopBar` has three forms: a `title`/`subtitle` one, a slot one taking arbitrary centre
  content, and a searchable one (`title`/`query`/`onQueryChange`/`searchPlaceholder`) that
  delegates to the other two depending on whether its search icon has been tapped. All three draw
  the same bar chrome, so every screen header keeps one surface, hairline and height.
- Home has its own `HomeViewModel` now (see "Home (1b reworked)" above); with no transcripts at
  all it still falls back to the mascot empty state (its "Upload audio" / "Type a note" buttons
  are inert).
- `RecordViewModel.stopRecording` saves the transcript and then runs
  `TranscriptAnalyzer`, writing `TodoItem` rows and a title and summary back onto the
  transcript. The Processing screen holds for that work — `STUB_PROCESSING_DELAY_MS` is gone.
  The transcript is saved *before* analysis, so no model failure can cost a recording.

## Notifications

Every undone `TodoItem` gets an `AlarmManager` notification: 15 minutes before `dueAt` when
`hasTime`, else a fixed 9:00 AM local time on `dueAt`'s day. `NotificationScheduler`
(`data/NotificationScheduler.kt`, plus `fireTimeFor`) is a pure-JVM interface so the scheduling
*logic* — `JsonDerivedRepository`'s `sync`/`resync`, called from every write path
(`replaceFor`, `setDone`, `deleteFor`) — is unit-testable without Android; the Android
implementation, `notify/AlarmManagerNotificationScheduler.kt`, is unreachable from unit tests and
only verified on device. `JsonDerivedRepository` takes the scheduler as a **required**
constructor param, not a defaulted no-op, so a build can't silently ship without notifications.

- **`USE_EXACT_ALARM`**, not `SCHEDULE_EXACT_ALARM`: auto-granted and non-revocable for apps
  whose core function is alarms/reminders, but that exemption is a Play Store policy review, not
  a manifest guarantee — a future policy rejection would force a fallback to inexact alarms or
  `canScheduleExactAlarms()` gating, neither of which exists today.
- **`PendingIntent` identity is the Intent's data URI (`echo://item/<id>`), not `requestCode`.**
  `PendingIntent` equality goes through `Intent.filterEquals`, which checks action/data/type/
  package/component/categories and ignores extras — so `requestCode = itemId.hashCode()` alone
  would be a 32-bit collision lottery where one item's alarm silently overwrites another's. With
  an explicit component and a per-item data URI, `requestCode = 0` is safe. The content
  `PendingIntent` (notification tap → `MainActivity`) needs its own per-item data URI for the
  same reason, plus `FLAG_ACTIVITY_SINGLE_TOP` as an *Intent* flag (not a manifest `launchMode`)
  to get `onNewIntent` delivery against `MainActivity`'s default `standard` launch mode.
- **`MY_PACKAGE_REPLACED` clears exact alarms exactly like a reboot does** — during
  `./gradlew installAndRun` development this means every alarm silently disappears without
  `notify/BootReceiver.kt` listening for both actions. It is *not* `directBootAware` and does not
  listen for `LOCKED_BOOT_COMPLETED`: `filesDir` is credential-encrypted and unreadable before
  first unlock, same constraint as everywhere else in this app.
- Tap-through to the source transcript is an `Intent` extra read in `MainActivity`, not a
  Navigation-Compose deep link: `EchoApp` renders `SignInScreen` (or nothing, while `AuthState`
  is `Unknown`) *outside* the `NavHost`, so a cold start calling `handleDeepLink` on a
  controller that doesn't exist yet would drop the intent. `MainActivity` holds the pending
  transcript id in `Activity` state instead (seeded in `onCreate`, updated in `onNewIntent`, the
  extra removed each time so a config-change recreation doesn't renavigate), and passes it down
  to `SignedInApp`'s `NavHost` once it exists.
- `POST_NOTIFICATIONS` is requested from `TasksScreen` the first time its item list goes
  non-empty, guarded by a file-scoped `askedForNotifications` so it fires once per process. If
  denied, scheduling still runs — alarms fire — but `NotificationManager.notify()` silently
  no-ops; no crash, no retry, no re-prompt.

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
