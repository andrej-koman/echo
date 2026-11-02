# Echo

Android app: on-device voice notes — record or import audio, Whisper transcribes it, a local LLM
pulls out a note and tasks. Kotlin, Compose, minSdk 29.

## Rules

- **Never commit.** No `git commit`, no `git push`. Andrej reviews all code. Stage nothing unless asked.
- **YAGNI.** Implement only what was asked. No speculative abstractions, no "while I'm here" extras, no config for cases that don't exist yet.
- **Few comments.** Code should read on its own. Comment only what the code cannot say: a non-obvious platform quirk, or why an unusual choice was made. No comments restating the line below them, no KDoc on obvious functions.
- **Keep this file current.** When something below stops being true, or a session turns up a fact worth having next time — a new package, a changed decision, a device quirk, a dependency that cannot be used — update the relevant section here as part of the work. Facts and decisions with their reasons, not a changelog.

## Rebuild in progress (v1 spec)

`Echo — Feature Spec (v1).pdf` (repo root, untracked) is the target: LoroNote parity on Android —
on-device Whisper, speaker diarization, AI summary/tasks/polishing on every phone, import,
TXT/SRT/audio export, playback-synced editor, widget + Quick Settings tile, Wear OS, Play Billing.
The phased roadmap is in `~/.claude-personal/plans/hi-please-use-echo-swift-gray.md`. Each phase
builds one feature and redesigns its UI only when that phase arrives. Decisions made for it:

- **Reminders, the daily brief and Gemini Nano are gone** (Phase 0, 2026-10-30). Not in the spec,
  and `USE_EXACT_ALARM` is a Play policy risk once reminders aren't the app's core function. Tasks
  keep due dates and Home's snooze pill, but nothing rings. Nano: the spec forbids depending on a
  vendor AI platform, and the S24 can't run it anyway.
- **The Home task dashboard stays**, although the spec's app is library-first.
- **Storage → SQLDelight** (Phase 1, done 2026-10-30; see Storage). Room is blocked by KSP;
  SQLDelight needs no compiler plugin. FTS4, not FTS5 — FTS5 isn't guaranteed in platform SQLite
  on older Android.
- **Whisper → whisper.cpp over JNI** (Phase 2): the only mature Android runtime for Large V3
  Turbo quantized; language detect, `initial_prompt` and timestamps built in.
- **Diarization → sherpa-onnx** (Phase 6, done 2026-11-02; see Speakers). Its Silero VAD is not
  used: Whisper chunks still cut at the quietest 100 ms.
- **Transcription jobs → WorkManager** with `setForeground`: the old "no WorkManager" reason was
  Nano's foreground-only limit, which no longer applies.
- **minSdk 29** (Phase 3, with `SpeechRecognizer` gone). API 30+/31+/33+ calls are gated:
  the microphone foreground type, `VibratorManager`, `POST_NOTIFICATIONS`. `mipmap-anydpi-v26`
  keeps its qualifier although lint calls it obsolete — renamed, AAPT stops finding
  `ic_launcher`.
- Old JSON data is not migrated — it has no audio, and the rebuild starts clean.
- **Import was pulled ahead of Phases 1–3** (2026-10-30, Andrej had files on the phone to try).
  Phase 1 then moved storage underneath it to SQLite.

## Recording

- `RecordingController` (app-scoped) records through `AudioRecorder` straight to
  `recordings/<id>/audio.pcm` — raw 16 kHz PCM16, what Whisper reads, with no header to finish if
  the process dies. Stop saves a transcript row with that id (empty text) and hands it to
  `Transcriptions.transcribe`; a take with no audio is discarded. Afterwards the PCM is encoded to
  AAC `source` (`AudioEncoder`, 32 kbps — ~14 MB/hour instead of 115) and deleted. Encoding goes
  to `source.part` and is renamed: a half-written `source` would read as an import.
- **There is no live text and no Processing screen.** Stop leaves Capture at once; Whisper runs
  as a WorkManager job (minutes, not seconds) and the note's badge shows "Transcribing… N%".
- Pause/resume append to the same file (resume waits for the paused read to finish first).
  `AudioFocus` takes transient focus for the take: music pauses, and a phone call
  (`AUDIOFOCUS_LOSS_TRANSIENT`) auto-pauses until focus returns — but the end of a call never
  undoes a pause the user made. A failing `AudioRecord` (mic held by another app) pauses with
  `micFailed` instead of losing the take.
- Every take runs under `RecordingService` (microphone foreground type), started from
  `RecordViewModel.startRecording` too — not only from the notification — so leaving the app
  keeps recording. Capture's chevron leaves the screen and keeps recording; the tab bar's record
  button (`launchSingleTop`) or the notification returns to it.
- **Startup recovery** (`Transcriptions.recoverInterruptedRecordings`): an `audio.pcm` with no
  transcript row is a take whose process died before Stop; it is filed with its length and
  queued. Called once from `AppContainer.init`, before any take can exist.
- **Dictionary** (Settings → Dictionary, `DictionaryRepository`): terms joined into Whisper's
  `initial_prompt` with `carry_initial_prompt` on, so it holds for every 30 s window of a chunk,
  not only the first. Applies to new transcriptions only.
- Language is a Whisper code in Settings (`SettingsStore.transcriptionLanguage`, preference key
  `transcription_language` — the old `language` key held a `SpeechRecognizer` locale), or
  auto-detect when unset. The list comes from `WhisperNative.languages()` (99 codes, no model
  needed); `PickerSheet` scrolls for it.

## Import and Whisper

- **whisper.cpp v1.9.5** is fetched by CMake `FetchContent` (URL + SHA256 pinned) in
  `app/src/main/cpp/CMakeLists.txt` and built static into `libechowhisper.so` with the JNI shim
  `whisper_jni.cpp`. Native build is always `-O3` (`CMAKE_BUILD_TYPE=Release` even for debug —
  unoptimised it is tens of times slower), arm64-v8a only, `armv8.2-a+dotprod+fp16`, OpenMP off
  (the NDK's would need `libomp.so` shipped). Segment text crosses JNI as bytes: Whisper can split
  a multibyte character and `NewStringUTF` aborts on that.
- Recordings share this pipeline from the PCM step on (see Recording).
- **Model per RAM** (`speech/whisper/WhisperModels.kt`): ≥7 GB total memory (8 GB phones report
  ~7.3) → Large V3 Turbo q5_0 (574 MB), else Small q5_1 (190 MB). Ungated
  `ggerganov/whisper.cpp` on HuggingFace, through the same `ModelStore`/`HttpModelDownloader` as
  the LLM. It downloads on the first import, not at onboarding.
- **Pipeline** (`transcribe/Transcriptions.kt`): the picked/shared file is copied to
  `files/recordings/<id>/source` (a content URI's grant dies with the sharing app; export will need
  the original), a placeholder transcript titled after the file name is saved, and a unique
  WorkManager job (`TranscriptionWorker`, tagged `transcript:<id>`) runs. `AudioDecoder`
  (MediaExtractor + MediaCodec → downmix → linear resample) writes 16 kHz PCM16 to `audio.pcm`,
  deleted afterwards. Whisper runs in 5-minute chunks cut at the quietest 100 ms of the last 3 s;
  the transcript text is rewritten after each chunk. Language is auto-detected on the first chunk
  and held. Bracketed captions (`[BLANK_AUDIO]`, `(music)`) are dropped. Then the existing
  analysis queue takes over (`autoAnalyze`).
- Status comes from WorkManager (`Transcriptions.status`) and is shown through the same
  `PendingCopy`/`PendingBadge` path as analysis; it wins over an analysis block. The detail
  screen's retry button retries transcription when that is what failed.
- Speed on the S24 (Large V3 Turbo, CPU): download plus a 3:20 file took ~10 min end to end.
  Music produces invented text ("The End" repeated) — import speech.
- The foreground service type is `mediaProcessing` on API 35+, `dataSync` below; WorkManager's
  `SystemForegroundService` is re-declared in the manifest with both. Started from the background
  (retry, reboot) the foreground call is refused and the job runs without it.
- `adb shell am start -a SEND --eu android.intent.extra.STREAM content://media/...` can't test the
  share target: the shell can't grant the URI. Use the Notes upload button.

## Speakers

- **sherpa-onnx v1.13.8** from JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx`; the repo is
  scoped to that group in `settings.gradle.kts`). Echo calls its **C API** through its own
  `diarize_jni.cpp` (`libechodiarize.so`), not the AAR's Kotlin classes: those take a
  `FloatArray`, and an hour of float samples (230 MB) is past the 256 MB Java heap, so the native
  side reads `audio.pcm` itself. The AAR has no headers, so CMake downloads `c-api.h` pinned to
  v1.13.8 by SHA256 and `dlopen`s `libsherpa-onnx-c-api.so`. Its JNI and C++ wrapper `.so`s are
  excluded from the APK. onnxruntime adds ~22 MB to it.
- Models (`speech/speakers/SpeakerModels.kt`, through `ModelStore`): pyannote segmentation 3.0
  int8 (1.5 MB, HF commit-pinned) and 3D-Speaker ERes2Net base (40 MB, sherpa-onnx's GitHub
  release — the model its own examples use with the default 0.5 clustering threshold). Fetched
  after Whisper on "Get started", else inside the first transcription job.
- Runs in `Transcriptions.run` after the last Whisper chunk, inside `slot.whisper`, before the
  PCM is deleted (status `FindingSpeakers`, "Finding speakers… N%"). Off with Settings →
  Detect speakers. A failure only logs: the transcript is never failed for want of speakers.
  sherpa-onnx can't be aborted part way — its progress callback's return value is ignored.
- `assignSpeakers`: each segment gets the speaker it overlaps most (the nearest turn when none),
  renumbered by first appearance. Tables: `segment.speaker` (an index, nullable) and `speaker`
  (`transcriptId`, `idx`, `name`; null name shows "Speaker N"). Renaming touches only `speaker`.
  `setTranscription` drops a transcript's speakers; `editSegments` keeps them.
- Reassigning, in the editor: tap a speaker label (that line), long-press lines to select several
  then "Speaker…", or in Edit mode select text and "Speaker for selection" — the line is split
  around the selection, times interpolated by character offset (Whisper segments have no word
  times). The sheet also renames and adds a speaker. Colours: `EchoColors.speakers`, by index,
  wrapping.

## Storage

- One SQLite database, `echo.db`, through SQLDelight 2.4.1 (`app/src/main/sqldelight/`).
  Tables: `transcript`, `segment`, `segment_fts`, `speaker`, `todo`, `analysis_queue`,
  `dictionary_term`. The repository interfaces kept
  their shape, so ViewModels never see SQL; each `Sql*Repository` exposes `Query.asFlow()`
  flows, which re-emit on any write to their table.
- Tables are added in the phase that uses them (folders in 8), each with a
  numbered `.sqm` migration beside the `.sq` files. `1.sqm` (Phase 5) added
  `transcript.polishedText` and `todo.sourceOffsetMs`; `2.sqm` (Phase 7) added `segment_fts` and
  `dictionary_term`; `3.sqm` (Phase 6) added `speaker` and `segment.speaker`; schema is version 4.
- **Search** (`TranscriptRepository.search`, Notes' search field): `segment_fts` (FTS4,
  `tokenize=unicode61`, so `č` matches `c` and case folds) one row per segment, plus `LIKE` on
  title and summary. The repository writes `segment_fts` in the same transactions as `segment` —
  no triggers. `ftsMatch` turns each typed word into a lowercased prefix term; lowercase because
  a bare `AND`/`OR`/`NOT` is an FTS operator.
- `segment` holds Whisper's timed segments (bracketed captions already dropped);
  `transcript.text` stays as the joined text, which is all a pre-Phase-3 recording had. `TranscriptRepository.setTranscription` replaces text and segments in one transaction.
- No foreign keys: SQLite needs `PRAGMA foreign_keys` per connection. Deleting a transcript
  deletes its segments in the same transaction; todos and queue entries are deleted by the
  callers, as before.
- `AnalysisBlock` is stored as JSON text — a sealed type nothing ever queries into. One that
  no longer decodes reads as no reason, not a crash.
- The old JSON files are deleted on first launch (`deleteJsonStore` in `AppContainer`), with
  `recordings/`, whose audio belonged to them.
- Tests use an in-memory JDBC driver (`testDatabase()` in the test `data` package).

## Build

```bash
./gradlew installAndRun   # build + install + launch on connected phone
./gradlew test            # unit tests, no device
./gradlew assembleDebug
```

Wireless debugging drops often: `adb connect <ip:port>` from Settings → Developer options → Wireless debugging.
There are no instrumented tests (the old `RecordScreenTest` tested live recognizer text). Grant
the mic without the dialog: `adb shell pm grant dev.andrej.echo android.permission.RECORD_AUDIO`.
Capture opens behind a locked keyguard (`am start -n dev.andrej.echo/.MainActivity --ez
open_capture true`) and records, but Stop can't be tapped — `am force-stop` then leaves the take
for startup recovery.

## Layout

```
speech/       AudioRecorder (mic → 16 kHz PCM16 file), AudioFocus (call interruptions),
              AudioDecoder (any file → 16 kHz PCM), AudioEncoder (PCM → AAC .m4a),
              whisper/ (JNI, WhisperTranscriber, model per RAM),
              speakers/ (SpeakerNative JNI, Diarizer + assignSpeakers, model specs)
transcribe/   Transcriptions (import, recordings, job status, startup recovery) +
              TranscriptionWorker (WorkManager) + Chunking
cpp/          CMake build of whisper.cpp + whisper_jni.cpp, diarize_jni.cpp over sherpa-onnx's C API
ai/           LlmRunner interface + LiteRtLlmRunner (downloaded Qwen3), LlmRunnerProvider,
              ModelStore/HttpModelDownloader for the weights, AnalysisPrompt (summary, merge
              and tasks prompts + parsers), TextChunks, PolishPrompt + TranscriptPolisher,
              TranscriptAnalyzer, AnalysisOutcome/AnalysisBlock, PendingAnalysisWorker
data/         Transcript + Segment, TranscriptRepository, TodoItem + DerivedRepository,
              PendingAnalysis + AnalysisQueueRepository, each Sql*Repository over one
              EchoDatabase (Database.kt), SettingsStore
sqldelight/   the schema, one .sq file per table (generated code in dev.andrej.echo.data.db)
ui/theme/     OutLoud design tokens (Color, Type, Shape, Spacing, Elevation, Motion, Theme)
ui/components/ the ported design system kit
ui/home/      HomeScreen + HomeViewModel — hero task, then/coming-up strip, latest note
ui/tasks/     TasksScreen + TasksViewModel — grouped by source transcript, done checkbox
ui/capture/   CaptureScreen — recording, paused
ui/record/    RecordViewModel + RecordUiState over RecordingController (still named for its old screen)
ui/onboarding/ OnboardingScreen + OnboardingSteps (the 5 first-run screens), OnboardingViewModel
ui/profile/   ProfileScreen (the Profile tab) + ProfileViewModel, SettingsScreen +
              SettingsViewModel, HistoryScreen + HistoryViewModel, DictionaryScreen +
              DictionaryViewModel
ui/notes/     NotesScreen + NotesViewModel (the Notes tab — one shared card per day, hairline
              dividers, same rhythm as Tasks; search; NoteRow, PendingBadge, PendingCopy)
ui/detail/    NoteDetailScreen (NOTE + tasks-from-this-note + segment editor, bar-icon actions),
              NotePlayer (ExoPlayer), TranscriptEdits (find/replace/edit/speaker logic),
              SpeakerSheet
notify/       RecordingNotifier (+ RecordingActionReceiver, BootReceiver), ModelDownloadNotifier
              (+ ModelDownloadActionReceiver)
AppContainer manual DI, no Hilt
```

Four tabs: Home / Tasks — record button — Notes / Profile, Home the start destination. There is no
separate Transcripts tab — Notes is the one browsable list (former Transcripts content merged into it,
`ui/notes/NotesScreen.kt`), and raw transcript text lives only in each note's detail screen
("WHAT WAS SAID" in `NoteDetailScreen`), not as a tab of its own or anywhere in Profile.
No header account icon
button anywhere — Profile is reached only via its tab (Tasks' empty-state "Turn on AI" button also
tab-switches there, via `onOpenProfile`). The record
button sits in the bar's middle slot (`TabBar(centerGap = true)` leaves the gap, `EchoApp` overlays the
button on a bar-coloured cradle so it pokes above the hairline). The tab bar is icon-only — no labels, no
selection crossfade — and the NavHost runs with every transition set to `None`: the design's
screen changes read as jank on device, so switching is a hard cut. Tab switches go through `NavHostController.selectTab`: Home pops back to Home, other tabs
`popUpTo(HOME)` + `launchSingleTop`. Never `saveState`/`restoreState` — the saved tab stack keys to
Home, so tapping Home restored it and landed on the last tab. Cost: tab scroll position is not kept.
`EchoApp` gates on `AppContainer.onboardingDone` outside the NavHost (see Onboarding below).

## Notes

- No Room: KSP has no release matching Kotlin 2.4.10.
- **Fully offline, no account.** Google sign-in, Supabase auth and sync were removed
  (2026-10-29). The only network traffic left is the LLM weights download (`HttpModelDownloader`,
  hence `INTERNET`) and the system's own speech language-pack download. The remote Supabase
  project was left as-is; nothing in the app talks to it.
- `ui.notes.title()` derives a title from the opening of the text (first sentence, or
  six words) — the fallback for a transcript analysis has not named yet, not a permanent scheme.
  `Transcript.asRow()` prefers `title`/`summary` when present. Since Phase 5 `summary` is the
  gist — a sentence or two, a short paragraph or bullets for a long note — and the full
  cleaned-up text is polishing's job (see On-device AI). Row excerpts and `NoteDetailScreen`'s
  NOTE section render it as-is.
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
  `buildTasksPrompt` takes the transcript's own `createdAt` as a `LocalDateTime`, not just a
  `LocalDate` — the prompt states both "Today is …" *and* "the current time is …" so the model
  has something to add a spoken duration to. Without the current time, "in 20 minutes" is
  unanswerable and the on-device model was observed inventing an arbitrary hour (`02:20` for a
  note recorded at 11am) rather than refusing. `parseTasks`/`parseSummary` also repair one specific
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
  day/time via native `DatePickerDialog`/`TimePickerDialog`, delete it
  (`DerivedRepository.update`/`delete`, alongside `replaceFor`/`setDone`/`deleteFor`), and reach the source transcript only
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
- `derivedId` takes no `kind` — one entity, one id — but keeps the null-byte separator between
  transcript id and text: a plain concatenation would let `"t1"` + `"x"` collide with
  `"t"` + `"1x"`.
- **Home ("1b reworked")** puts one task in a hero slot rather than a list. `HomeViewModel.buildState`
  reuses `ui.tasks.groupByDue`/`isOverdue` (both `internal`, not `private`, for this reuse): the hero
  is the first item of the "Today" group (overdue items sort first, so an overdue task always wins
  the slot over a same-day timed one; a timed item wins over an untimed one). Its action pill is
  context-dependent: a snooze pill (bg `surfaceAccentSoft`) when overdue — `HomeViewModel.snooze`
  pushes `dueAt` forward by `SettingsStore.snoozeMinutes` (`snoozeTime`) via
  `DerivedRepository.update`, nothing fancier — or `Add a time` (solid `actionPrimaryBg`) when the
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
- Deletes are hard deletes, no tombstones. `TodoItem.updatedAt` stays: the Completed section
  sorts by it.
- Derived rows are keyed by `UUID.nameUUIDFromBytes(transcriptId + kind + normalized text)`, not
  minted fresh, so re-analysis lands on the same ids and `Task.done` survives it. Before this,
  Analyze silently un-ticked every task. Two rows with the same text under one transcript collapse
  to one (`distinctBy`) — a repeated task from a single note is noise.
- **Editor and playback** (Phase 7, `ui/detail/`): `NotePlayer` wraps a Media3 ExoPlayer over
  `recordings/<id>/source` (`Transcriptions.audioFile`; a recording has one only once transcribed),
  released with the screen and paused on `ON_STOP`. The transcript renders as segment rows in a
  `LazyColumn` with the player bar pinned below: the playing segment is highlighted
  (`transcriptHighlight`), tapping a row plays from it. **Edit** turns rows into text fields, saved
  on Done; **Replace** is a case-insensitive literal find & replace-all. Both go through
  `TranscriptRepository.editSegments`, which rejoins `transcript.text`; a segment emptied is
  dropped. Neither is offered while the note is still transcribing (each chunk rewrites the
  segments) or while polished text is shown (it isn't segmented). Tapping a task with a
  `sourceOffsetMs` plays from there; long-press opens `EditTaskScreen`. Pure logic in
  `TranscriptEdits.kt`. No auto-scroll to the playing segment.
- `NoteDetailScreen` (redesigned from the OutLoud "Transcript Page" doc): the bar carries
  back / delete / re-analyze as icons instead of three stacked bottom buttons, so the body is
  content, not chrome. Re-analyze (`ic_audio_waveform`) re-runs `TranscriptAnalyzer` through
  `NotesViewModel.analyze()`; it goes `IconButtonVariant.Ghost` and no-ops while the entry's
  `PendingCopy.severity` is `Parked` (nothing to retry yet — `WaitingForModel`/`NoBackend` are
  preconditions), otherwise `Soft` (bronze) and calls `analyze()`. One in flight at a time
  (`analyzingId`, sourced from `PendingAnalysisWorker.activeId`). Body: NOTE (the summary) then a
  "TASKS FROM THIS NOTE" section reusing `TaskGroupCard` from `ui/tasks/TasksScreen.kt` — made
  `internal` (was `private`) so the detail screen can render the same rows, checkbox and
  bronze/clay time labels as the Tasks tab, tapping one seeking the player (see Editor above)
  (`sourceTitleFor = { null }` since the source is already the screen you're on). Below that,
  "WHAT WAS SAID": the segments (see Editor above), with Edit / Replace / Copy / Polish
  ("Restore original" once polished; polished text then replaces the segments in a card,
  captioned "· POLISHED"). A `pending` entry (parked or failed) replaces NOTE/TASKS
  with a status card (icon avatar + copy + a retry button when `Failed`) — analysis hasn't
  produced a note yet, so there is nothing to show there. `NotesViewModel.tasks` (all
  `DerivedRepository.items`, unfiltered) backs both this screen's per-transcript filter and the
  Notes list's per-row task counts.
- **Pending analysis queue.** `TranscriptAnalyzer.analyze()` returns `AnalysisOutcome`
  (`Success` or `Blocked(AnalysisBlock)`) instead of a nullable `Analysis`, so a failed analysis
  carries a reason instead of vanishing: `EmptyTranscript`, `WaitingForModel(bytes)`,
  `NoBackend(reason)`, `GenerationFailed(message)`, `EmptyResult`. `WaitingForModel` and
  `NoBackend` are preconditions, not failures — a transcript parked on either never exhausts its
  retries. `PendingAnalysisWorker` owns the drain: it reads `AnalysisQueueRepository`
  (the `analysis_queue` table), runs
  `TranscriptAnalyzer` against each entry, and either attaches the analysis or records the block.
  `GenerationFailed`/`EmptyResult` consume one of 3 attempts; past that an entry sits unretried
  until a manual Analyze. The worker runs on `AppContainer`'s `appScope`, not a ViewModel scope —
  navigating away no longer cancels an in-flight analysis, which used to happen with
  the old `_analyzingId` on `viewModelScope`. Three triggers call
  `worker.requestDrain()`: `Transcriptions.run` once a transcript's text is in,
  `AppContainer`'s collector on
  `modelStore.state` (collapsed to the state's kind, not raw equality, so download progress ticks
  don't retrigger it), and `MainActivity.onStart()` via `container.onAppForegrounded()`. Both of
  the latter two also call `LlmRunnerProvider.invalidate()` first — without it a resolved
  `NoLlmRunner` answer stays cached for the process's life even after a model finishes
  downloading. `NotesViewModel.analyze()` (the manual button) calls
  `worker.analyzeNowAsync()`, which bypasses both the attempt cap and the "already analyzed"
  drop that the automatic drain applies — an explicit tap always gets a fresh run. Analysis does
  not use WorkManager yet; transcription jobs will (see Rebuild above). **No timed backoff**: drains are event-triggered,
  not a polling loop, so there is nothing to back off *from*; the attempt cap alone prevents
  churn. `NotesViewModel.pending`/`HomeViewModel` and `ui.notes.group()` turn
  each queue entry's `lastBlock` into a `PendingCopy` (`ui/notes/NotesViewModel.kt`)
  — a plain `{text, severity}` pair, no Compose types, so it stays testable without a device. An
  entry only gets copy once the drain has actually attempted it (`lastBlock != null`); a
  freshly-enqueued row shows nothing for the moment before that. `PendingBadge` (in
  `ui/notes/NotesScreen.kt`, reused by Home and the detail screen) renders it as an
  `EchoBadge`: `Parked` (waiting on a model or backend) in `BadgeTone.Neutral`, `Failed`
  (`GenerationFailed`/`EmptyResult`, exhausted or not — the badge doesn't distinguish) in
  `BadgeTone.Warning`. `EmptyTranscript` never renders — it's never enqueued. No dedicated
  first-run modal; the badge is the only affordance, on transcript rows (Home and Notes),
  recent-note cards, and the detail screen, which already has the Analyze button to retry with.
- **Profile, Settings, History** implement the OutLoud design's three-screen exploration
  (Claude Design project `582866bc-4d0a-4f8f-a6bc-dbb7efb86455`). `ProfileScreen` replaced the old
  `AccountScreen` stub: a NOTES/TASKS DONE/CAPTURED stats grid and a "This week" bar-chart card
  (tap opens History) with a streak — no identity card or account rail since the offline pivot.
  `ProfileViewModel.buildState` computes the
  week bars and streak by walking backward from today over `Transcript.createdAt` grouped by
  calendar day (zone-aware) — the first empty day stops the streak. `AiViewModel` is gone: its
  Download/Cancel/Remove UI moved into `SettingsScreen`'s ON-DEVICE AI card, and `SettingsViewModel`
  now takes the same `aiCardState`/`onDownloadModel`/`onCancelDownload`/`onDeleteModel` shape
  directly from `AppContainer`. `SettingsStore` beyond `transcriptionLanguage`: `detectSpeakers`, `autoAnalyze` (gates the
  enqueue after transcription — off means every note waits for a manual Analyze),
  `wifiOnlyDownload` (checked in `AppContainer.downloadModel()` via `ConnectivityManager`, download
  is skipped rather than queued if off Wi-Fi), `snoozeMinutes` (Settings' TASKS section, Home's
  snooze pill). The ON-DEVICE AI card shows the one model, no backend picker. Settings' FEEL section (Haptics, Reduce
  motion) stays read-only by design — threading the setting through `rememberEchoHaptics()`/
  `LocalReducedMotion` would need a new CompositionLocal reaching every call site, out of scope for
  this pass; the switches render "on" but ignore taps. `AppContainer.storageUsedBytes()` backs
  Settings' DATA section, alongside **Delete all data** (behind `ConfirmSheet`):
  `AppContainer.deleteAllData()` clears the pending queue, every `TodoItem` and every transcript, via each repository's `clear()`. It keeps the model, settings and
  `onboardingDone`. `HistoryScreen`/`HistoryViewModel` render a month calendar
  (`YearMonth`/`LocalDate` state, not persisted) with a 4-level heat grid (0/1/2/3+ notes that
  day), month totals, and a selected-day detail list; `nextMonth()` refuses to advance past the
  current month. `EchoSwitch` (44×24 pill, 20dp thumb) moved from a private composable in
  `EditTaskScreen.kt` to `ui/components/EchoSwitch.kt` now that Settings needs it too, gaining an
  `enabled` flag for the read-only FEEL rows. `ui/components/BottomSheet.kt` is new and generic:
  `ConfirmSheet` (destructive confirmation) and `PickerSheet` (single-choice list with a check
  mark) are both hand-rolled `Box`/`AnimatedVisibility` scrim+sheet overlays, not Material's
  `ModalBottomSheet` — nothing else in the app uses one, and the design's plain white sheet with no
  system scrim animation is simpler to hand-roll consistently with `echoShadow`/`hairline`.

## Design system

Screens follow the "Echo" design system (formerly "OutLoud"), published as a Design System
artifact: https://claude.ai/artifact/3VcQDGeoAbWCMknDg8WUV2 (Claude Design project
`07874187-7a4b-4852-b91d-5f05af0973e4`). Token values live in its `project/tokens.css` /
`tokens.json`; read them with the Artifact tool (`action: "read"`, `path`) rather than guessing.
The older screen files (`Echo Screens.dc.html`, `Echo Transcripts.dc.html`, project
`2a9bb6ef-…`) are no longer reachable.

Onboarding is ported from the separate "Echo Onboarding" Design artifact
(https://claude.ai/artifact/We1pU5EfPrcFTnNatC6jmV), light and dark artboards.

- Tokens live in `ui/theme/` and are reached through the `EchoTheme` object
  (`EchoTheme.colors.recordLive`), not through `MaterialTheme`. Material's `ColorScheme` carries
  only the subset that maps cleanly, so stock M3 components don't clash.
- **Light and dark, following the system setting; no dynamic color.** `EchoTheme` picks
  `EchoColors()` or `DarkEchoColors` (and `EchoElevation()` or `DarkEchoElevation`) from
  `isSystemInDarkTheme()`; there is no in-app toggle. Dark stays warm browns, never charcoal.
  The ramps (`Bronze400`, `Ink100`, …) keep their light values in both themes, so components use
  semantic tokens only — raw ramp steps are left only where the design itself pins them in both
  themes (the record button and processing spinner gradients, bronze-filled discs with white
  glyphs). `aura` replaces raw papaya whip for the capture wash, `indicatorIdle` and `skeleton`
  replace the raw ink steps that used to be hard-coded. The design's bars are 80% page over a
  backdrop blur; Compose has no backdrop blur, so `surfaceBar` is opaque in both themes.
  `res/values-night/` mirrors `themes.xml`/`colors.xml` so the cold-start window and system bar
  icons match before Compose draws. Labels on bronze (`actionPrimaryFg`, `textOnAccent`) are dark
  ink in both themes, not white.
- Shadows are ink-tinted, never black — black reads grey against cornsilk (dark uses warm
  near-black `#080603`, per the design). Use
  `Modifier.echoShadow(...)`, never `Modifier.shadow()`.
- DM Sans is the only typeface, bundled as static TTFs in `res/font`. Space Mono was dropped
  with the design system's update; timers and counts now use DM Sans (`typography.timer` for the
  big recording clock). The design asks for `tabular-nums`, but the bundled static DM Sans has no
  `tnum` feature (nor does Google Fonts' variable DM Sans 4.004), so digits are proportional and
  a ticking timer jitters slightly — `fontFeatureSettings = "tnum"` would silently do nothing. The mascot is a raster `drawable-xxxhdpi/mascot_echo.png` (the polished
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
  goes through `View.performHapticFeedback`, which already honours the system haptic setting.
  `success()` needs `VibrationEffect.EFFECT_DOUBLE_CLICK` and so needs the `VIBRATE` permission
  and its own check of `Settings.System.HAPTIC_FEEDBACK_ENABLED`.
- `EchoTopBar` has three forms: a `title`/`subtitle` one, a slot one taking arbitrary centre
  content, and a searchable one (`title`/`query`/`onQueryChange`/`searchPlaceholder`) that
  delegates to the other two depending on whether its search icon has been tapped. All three draw
  the same bar chrome, so every screen header keeps one surface, hairline and height.
- Home has its own `HomeViewModel` now (see "Home (1b reworked)" above); with no transcripts at
  all it still falls back to the mascot empty state ("Upload audio" opens the import picker,
  "Type a note" is inert).

## Notifications

Three channels, no per-task reminders (cut in Phase 0): the recording notification
(`RecordingNotifier` — idle "tap to record", or ongoing while `RecordingService` runs), the
model download progress (`ModelDownloadNotifier`), and transcription progress (posted by
`TranscriptionWorker` itself, including the Whisper download inside the job).

- **`MY_PACKAGE_REPLACED` and reboot clear the idle recording notification**, so
  `notify/BootReceiver.kt` listens for both and re-posts it. It is *not* `directBootAware` and does
  not listen for `LOCKED_BOOT_COMPLETED`: `filesDir` is credential-encrypted and unreadable before
  first unlock.
- The record action without `RECORD_AUDIO` granted opens `MainActivity` with
  `EXTRA_OPEN_CAPTURE`, an `Intent` extra rather than a Navigation-Compose deep link: `EchoApp`
  renders `OnboardingScreen` *outside* the `NavHost`, so a cold-start `handleDeepLink` would be
  dropped. `MainActivity` holds the flag in `Activity` state (the extra removed each time so a
  config-change recreation doesn't renavigate) and passes it to `MainApp`'s `NavHost`.
  `FLAG_ACTIVITY_SINGLE_TOP` as an *Intent* flag gets `onNewIntent` delivery against the default
  `standard` launch mode.
- `POST_NOTIFICATIONS` is requested by Capture together with `RECORD_AUDIO` (API 33+): the
  recording controls and transcription progress live in notifications. Only the mic is required
  to record; denied notifications mean `notify()` silently no-ops.
- The recording notification is Idle ("tap to record"), Recording or Paused (`RecordingMode`),
  with Pause/Resume and Stop actions into `RecordingService`; tapping it opens Capture. It is
  re-posted only when the mode changes — the controller's state ticks ten times a second.
- **`R.drawable.ic_notification_echo`** is the one `smallIcon` on every channel — the launcher's
  stroked ghost closes into a grey blob at 12–24dp, so this is the same silhouette flattened to a
  solid shape with the eyes cut out via `fillType="evenOdd"`.
- **Model download** (`notify/ModelDownloadNotifier`) is wired into the `modelStore.state`
  collector in `AppContainer` that also drives `llmRunners.invalidate()`: a silent
  (`IMPORTANCE_LOW`, `setSilent(true)`), ongoing progress notification while
  `ModelState.Downloading`, cancelled on any other state — including `Failed`, so a failed
  download doesn't leave a stuck progress bar. No "finished" notification. **Pause** and
  **Cancel** are the same call (`AppContainer.cancelDownload`, which cancels the coroutine — the
  `.part` file survives so the next `downloadModel()` resumes) except Cancel also calls
  `deleteModel()`.
- Widgets and the Quick Settings tile come in Phase 10 (design: OutLoud "Echo Notifications &
  Widgets", Claude Design project `e17770fa-3b8d-43f1-b85d-c74c647c4e70`).

## On-device AI

Analysis runs on a downloaded model, the only backend. `LlmRunnerProvider` still takes an ordered
list and caches the first usable runner (`NeedsDownload` beats the next candidate — the download
is the user's call), so a smaller per-tier model (Phase 2) slots in as one more class.

- **Gemini Nano was removed** (Phase 0): the spec forbids a vendor AI dependency, and the S24
  can't run it — AICore's `checkStatus()` *throws* `[606] FEATURE_NOT_FOUND` there (Google's
  device list starts Samsung at the S25).
- The model is Qwen3-1.7B int4 `.litertlm` (977MB) via LiteRT-LM. The format must be `.litertlm`,
  never GGUF — the wrong format fails silently on load. Gemma repos on HuggingFace are
  `gated: auto` and 401 an anonymous download; `litert-community/Qwen3-1.7B` is not gated.
- **The model URL is pinned to an HF commit** (`ModelStore.kt`), because the file's metadata can
  outrun the library. On 2026-10-11 the repo rewrote the chat template for LiteRT-LM 0.18
  (content parts); under 0.16.1 it rendered an empty user turn and every analysis came back
  "Please provide the details…" — on GPU and CPU alike, at any prompt length. Fixed by LiteRT-LM
  0.18.1. That answer in the `TranscriptAnalyzer` log means the template, not the prompt. Move
  the pin and the library together, and don't take the repo's `gpu_composites` file: it needs a
  LiteRT-LM newer than 0.18.1.
- `Engine.initialize()` costs ~19s against ~5s of inference, so it is held open between calls
  and warmed up when Capture opens, from an application-scoped coroutine in `AppContainer` —
  leaving Capture must not cancel a half-finished init.
- **One large model in memory at a time** (`ai/ModelSlot`). Whisper Large and the LLM together
  had the low-memory killer take the app even in the foreground, then again on every relaunch
  as WorkManager resumed the job — it looked like a crash loop with an empty crash buffer
  (`adb logcat | grep lmkd` shows it). Every engine use goes through `slot.llm`; Whisper runs
  in `slot.whisper`, which waits for a running LLM call, then closes the engine first. The next
  analysis pays the 19s load again.
- `ModelStore.delete()` sweeps every file prefixed with the model's name: LiteRT-LM writes a
  `..._mldrift_weight_cache.bin` beside the weights that is dead without them.
- MediaPipe LLM Inference is maintenance-only; LiteRT-LM replaces it.
- **Three prompts, not one** (Phase 5). The old combined prompt carried ~850 tokens of
  instructions and the 1.7B model followed little of it. `TranscriptAnalyzer` now runs a
  summary prompt and a tasks prompt per chunk (`chunkLines`, 1200 words — about 1700 of the
  4096-token context), and for a multi-chunk note one merge prompt over the parts' summaries;
  each step retries once at temperature 0 and the first step to fail blocks the whole analysis.
- **A yes/no before the tasks prompt** (`buildIntentPrompt`): "does the speaker talk about their
  own plans?" — only a yes runs the tasks prompt for that chunk. Told in words not to list
  steps someone explains, the 1.7B model still turned every step of an imported how-to video
  into a task; asked the yes/no alone, it answered no. Keep the tasks prompt's examples varied
  in line numbers: an example with only low numbers made every task point at line 0.
  This replaced the 3000-word truncation.
- **Tasks point into the recording.** The tasks prompt numbers its lines with their index in the
  whole transcript (Whisper's segments, or sentences for a transcript without them) and asks for
  each task's `line`; `parseTasks` turns that into `TodoItem.sourceOffsetMs`, the segment's
  start. Phase 7's player will seek to it.
- **Polishing is on demand** (the note's Polish button), not automatic: it rewrites the whole
  transcript, one call per 900-word chunk, where analysis only reads it. The result goes to
  `transcript.polishedText`; segments and `text` keep the original, so Restore is setting it
  back to null, and re-transcribing clears it. `parsePolish` rejects a reply under half or over
  1.3× the chunk's words — a summary, or an answer to the transcript, not a cleanup.
  `TranscriptPolisher` runs on the app scope like analysis; `LiteRtLlmRunner` holds a mutex so
  the two never share the engine at once.

## Onboarding

Five screens (`ui/onboarding/`): Welcome, Voice to tasks (1/4), Private and offline (2/4),
Microphone (3/4), First note (4/4). One composable switches on `OnboardingStep` — no NavHost,
hard cuts like the rest of the app; system back steps back, and leaves the app on Welcome.

- **Gate:** `AppContainer.onboardingDone` = `SettingsStore.onboardingDone`, or any transcript
  already on disk — an install that predates onboarding goes straight to Home.
- **Downloads start on "Get started", never block.** `startOnboardingDownloads()` starts the
  Whisper model and (when `aiCardState` says `NeedsDownload`) the LLM, both only on Wi-Fi. On
  mobile data nothing starts — the first recording's transcription job fetches Whisper itself; step 4 shows a "Download AI · 977 MB" button (not in
  the design) so the first note isn't stuck "waiting for model" with no way forward. A note
  recorded mid-download queues and auto-analyses when the model lands (existing drain trigger).
- **Paths:** Skip (steps 1–2) → Microphone, never past it. Allow → system dialog; granted →
  First note, denied → finish → Home. Not now → finish → Home (Capture asks again later). The
  record button → finish, then `MainApp` opens straight on Capture through the same
  pending-open-capture flag the recording notification uses. "I'll do it later" → finish → Home.
- Voice-to-tasks' three example rows are a static `DemoItemRow` with per-kind icons
  (bell / list-checks / calendar), not `TaskRow` — real `TodoItem`s have no kind.
- Button labels follow the design system's bumped scale: Sm/Md/Lg = 14/15/17sp semibold (was
  13/14/15 medium). The same update made the record glyph (`recordGlyph`, mic and stop on the
  record gradients) dark ink instead of white, darkened `textTertiary`/`waveformIdle`/
  `indicatorIdle`, added `controlTrack` for the off-state switch track, and moved `recordLive` to
  clay-600 and `statusWarningFg` to amber-700 in light.
- `CardTone.Cream` now uses the new `surfaceCream` token (papaya whip / dark `#33291A`), as the
  design system's Card does; it used to fall back to the page colour.
