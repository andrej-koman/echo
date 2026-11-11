# Echo implementation notes

Detailed design decisions, device findings, and implementation history. Read the relevant section when changing that subsystem; verify current behavior in code.

## Rebuild in progress (v1 spec)

`Echo — Feature Spec (v1).pdf` (repo root, untracked) is the target: LoroNote parity on Android —
on-device Whisper, speaker diarization, AI summary/tasks on every phone, import,
TXT/SRT/audio export, playback-synced editor, widget + Quick Settings tile, Wear OS, Play Billing.
The phased roadmap is in `~/.claude-personal/plans/hi-please-use-echo-swift-gray.md`. Each phase
builds one feature and redesigns its UI only when that phase arrives. Decisions made for it:

- **Reminders, the daily brief and Gemini Nano are gone** (Phase 0, 2026-10-30). Not in the spec,
  and `USE_EXACT_ALARM` is a Play policy risk once reminders aren't the app's core function. Tasks
  keep due dates, but nothing rings. Nano: the spec forbids depending on a
  vendor AI platform, and the S24 can't run it anyway.
- **Home and the Tasks tab are gone** (Phase 8, 2026-11-02, Andrej's call): the app is
  library-first. Notes is the start tab; tasks live only inside their note's screen
  (`EditTaskScreen` still edits one). Snooze went with Home.
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
  `Transcriptions.transcribe`; a take with no audio, or under 300 ms above loudness 0.35
  (~-41 dBFS), is discarded with its files: nothing was said, so there is nothing to transcribe. Afterwards the PCM is encoded to
  AAC `source` (`AudioEncoder`, 32 kbps — ~14 MB/hour instead of 115) and deleted. Encoding goes
  to `source.part` and is renamed: a half-written `source` would read as an import.
- **No live text while recording, and no Processing screen.** Stop closes the compact recording row; Whisper runs
  as a WorkManager job (minutes, not seconds) and the note's badge shows "Transcribing… N%".
  The note's own screen streams the text in as Whisper hears it (see Import and Whisper).
- Pause/resume append to the same file (resume waits for the paused read to finish first).
  `AudioFocus` takes transient focus for the take: music pauses, and a phone call
  (`AUDIOFOCUS_LOSS_TRANSIENT`) auto-pauses until focus returns — but the end of a call never
  undoes a pause the user made. A failing `AudioRecord` (mic held by another app) pauses with
  `micFailed` instead of losing the take.
- Every take runs under `RecordingService` (microphone foreground type), started from
  `RecordViewModel.startRecording` too — not only from the notification — so leaving the app
  keeps recording. Recording stays on the current page with a persistent row above the tabs:
  waveform, captured time, pause/resume and microphone interruption status. The center record
  button becomes Stop across navigation. Notification entry reuses the active take.
  Model warmup starts with recording, rather than when its app-scoped ViewModel is created.
- **Startup recovery** (`Transcriptions.recoverInterruptedRecordings`): an `audio.pcm` with no
  transcript row is a take whose process died before Stop; it is filed with its length and
  queued. Known blank notes with raw PCM are also queued, covering process death between Stop's
  row save and enqueue. Called once from `AppContainer.init`, before any take can exist.
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
  ~7.3) → Large V3 Turbo q5_0 (574 MB), else Small q5_1 (190 MB). Settings → Speech model
  overrides it (`SettingsStore.speechModel`, a `WhisperModel.id`; `SpeechModels` resolves it):
  Large Turbo, Medium q5_0, Small, Base q5_1 — Large and Medium offered only where the default
  would be Large. The pick applies from the next job, which downloads it; once it loads, the
  other Whisper files are deleted so a switch doesn't keep two on disk. Ungated
  `ggerganov/whisper.cpp` on HuggingFace, through the same `ModelStore`/`HttpModelDownloader` as
  the LLM. It downloads on the first import, or from onboarding's explicit download button.
- **Import sheet** (`ui/notes/ImportSheet.kt`): every picked or shared file waits in
  `Transcriptions.offered` for the sheet: the file's name and details on top, then a title
  (placeholder is the file name, which the note keeps until analysis names it), then which day it
  belongs to — when recorded (the file's `METADATA_KEY_DATE`, else its last-modified time; an
  MP4's 1904 epoch counts as none) or today. A typed title sets `transcript.titleByUser`
  (`5.sqm`), and `attachAnalysis` then keeps it. "Always use this choice" stores
  `SettingsStore.importDay` and hides only the day question — the title is always asked;
  Settings → Imported files' day sets it back to Ask each time. Dismissing the sheet drops the
  file. "Start transcribing" opens the note (`Transcriptions.started` → `EchoApp` navigates), so
  the user watches it fill in; with several files only the last opens.
- **Pipeline** (`transcribe/Transcriptions.kt`): the picked/shared file is copied to
  `files/recordings/<id>/source` (a content URI's grant dies with the sharing app; export will need
  the original), a placeholder transcript titled after the file name is saved, and a unique
  WorkManager job (`TranscriptionWorker`, tagged `transcript:<id>`) runs. `AudioDecoder`
  (MediaExtractor + MediaCodec → downmix → linear resample) writes 16 kHz PCM16 to `audio.pcm`,
  deleted afterwards. Whisper runs in 5-minute chunks cut at the quietest 100 ms of the last 3 s;
  the transcript text is rewritten after each chunk. Language is auto-detected on the first chunk
  and held. Bracketed captions (`[BLANK_AUDIO]`, `(music)`) are dropped. Then the existing
  analysis queue takes over (`autoAnalyze`).
- **Live text**: `whisper_jni.cpp` hooks `new_segment_callback` (`WhisperListener.onSegment`), and
  `Transcriptions.live` holds the running note's segments so far — in memory only, since the job
  runs in-process; the database still gets them per chunk. `NotesViewModel.segments` prefers it
  for that note. The note screen types it in (`typedSegments`: speed follows the backlog so each 30 s
  window's lump drains in ~2.5 s; "Listening…" before the first line), with a blinking caret,
  following the end until the reader drags away, and shows `TranscribingCard` (status, model, %)
  while any transcription status but Failed is up — in place of the player, which is hidden
  until the job ends. Its stop button (Whisper stage only) calls `Transcriptions.stop`: the
  Whisper run is cancelled (native abort), the live segments heard so far become the note, and
  speakers and analysis then run as usual on that text.
- **Restart recovery**: imports persist `transcription-pending` before their row is saved, copy to
  `source.part`, sync, then rename to `source`. Startup reconciles pending notes with unique KEEP
  work; an interrupted copy is shown as failed rather than decoded as complete audio. Each finished
  Whisper chunk atomically saves `speech-chunk` with its next sample, language, and segments before
  updating the database, so a restart restores the snapshot and skips finished chunks. Explicit
  uncapped transcription resets this snapshot; capped retries and startup recovery preserve it. After Whisper and speaker assignment finish, `Transcriptions` creates
  `recordings/<id>/speech-complete` before finalizing audio or awaiting analysis. If Android kills
  the process during that later work, WorkManager's restarted job skips Whisper and keeps the
  saved transcript and speaker labels. An explicit uncapped transcription request removes the marker; capped retries preserve it until a Pro continuation starts.
- **Speed** (S24, Large V3 Turbo q5_0, measured 2026-11-03 with `whisper_print_timings` →
  logcat tag `whisper`, wall times under tag `Transcriptions`): the encoder is ~95% of the time,
  one pass per 30 s window, 33 s per pass at full clock. Decoding text is cheap. Samsung caps every
  core at ~1.5 GHz when the phone is warm (wireless charging, or ~40 °C after a few minutes of
  this), and a pass then takes 50 s+. Benchmarks over `adb shell` run pinned to 3 cores
  (cpuset 4–6), so use the app's own logs, not `whisper-cli`.
- **Whisper Small** on the same phone: encode ~11 s per 30 s window (vs 33–50 s for Large), a
  15:27 file in 12.5 min of Whisper. Decoding a 15-min import to PCM took 56 s before Whisper
  started.
- **ggml's runtime CPU variants (`GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`) made it far
  slower**: the SVE2 variant took 208 s per pass, and even the i8mm-without-SVE one was several
  times slower than this static `armv8.2-a+dotprod+fp16` build. Don't retry without measuring.
- **VAD** (`WHISPER_VAD`, Silero 6.2.0 ggml, 0.9 MB, fetched inside the job, skipped if it
  can't be): whisper.cpp drops non-speech before the encoder and maps segment times back to the
  original audio, so seek and task offsets still line up. It only saves time where a recording
  has pauses — continuous speech is cut 0%, a 15-min conversation 3–6% per chunk.
- Parallel chunks were not tried: the encoder already keeps every core busy, so two Whisper
  states would split the same work and cost ~200 MB more.
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
- Runs in `Transcriptions.run` **beside Whisper**, inside `slot.whisper`, before the PCM is
  deleted, pinned to the slowest cores (`Diarizer.littleCores`, by `cpuinfo_max_freq`; the JNI
  `sched_setaffinity`s the calling thread before `create()`, so onnxruntime's pool inherits it,
  then restores it). Whisper's threads keep the fast cores. Turns are assigned after the last
  chunk; "Finding speakers… N%" shows only if Whisper finishes first. Measured 2026-11-03 (S24,
  15:27 of audio, Whisper Small): speakers were ready 47 s after Whisper, where running after it
  had cost ~78 s for a 2:17 file alone. Off with Settings →
  Detect speakers. A failure only logs: the transcript is never failed for want of speakers.
  sherpa-onnx can't be aborted part way — its progress callback's return value is ignored.
- `assignSpeakers`: each segment gets the speaker it overlaps most (the nearest turn when none),
  renumbered by first appearance. Tables: `segment.speaker` (an index, nullable) and `speaker`
  (`transcriptId`, `idx`, `name`; null name shows "Speaker N"). Renaming touches only `speaker`.
  `setTranscription` drops a transcript's speakers; `editSegments` keeps them.
- "Who is this?" (tap a speaker header): names that speaker — chips suggest names given in
  other notes (`speakerNames`, most used first) — and moves the run to another speaker or a new
  one. Long-press lines then "Speaker…" moves just those; in Edit mode select text and "Speaker
  for selection" — the line is split around the selection, times interpolated by character
  offset (Whisper segments have no word times). Colours: `EchoColors.speakers`, by index,
  wrapping.

## Storage

- One SQLite database, `echo.db`, through SQLDelight 2.4.1 (`app/src/main/sqldelight/`).
  Tables: `transcript`, `segment`, `segment_fts`, `speaker`, `folder`, `todo`, `analysis_queue`,
  `dictionary_term`. The repository interfaces kept
  their shape, so ViewModels never see SQL; each `Sql*Repository` exposes `Query.asFlow()`
  flows, which re-emit on any write to their table.
- Tables are added in the phase that uses them, each with a
  numbered `.sqm` migration beside the `.sq` files. `1.sqm` (Phase 5) added
  `transcript.polishedText` and `todo.sourceOffsetMs`; `2.sqm` (Phase 7) added `segment_fts` and
  `dictionary_term`; `3.sqm` (Phase 6) added `speaker` and `segment.speaker`; `4.sqm` (Phase 8) added `folder` and
  `transcript.folderId`; `5.sqm` added `transcript.titleByUser`; `6.sqm` adds task reminder time, local date, and delivery state;
  `7.sqm` adds `transcript.transcribedUntilMs`; `8.sqm` adds `todo.sourceQuote`; schema is version 9.
  Deleting a folder un-files its notes in the same
  transaction. A folder's `color` indexes `EchoColors.palette` (shared with speakers), its `icon`
  is a `FolderIcon` name.
- **Search** (`TranscriptRepository.search`, Notes' search field): `segment_fts` (FTS4,
  `tokenize=unicode61`, so `č` matches `c` and case folds) one row per segment, plus `LIKE` on
  title and summary. The repository writes `segment_fts` in the same transactions as `segment` —
  no triggers. `ftsMatch` turns each typed word into a lowercased prefix term; lowercase because
  a bare `AND`/`OR`/`NOT` is an FTS operator.
- `segment` holds Whisper's timed segments (bracketed captions already dropped);
  `transcript.text` stays as the joined text, which is all a pre-Phase-3 recording had. `TranscriptRepository.setTranscription` replaces text and segments in one transaction.
- Analysis results check transcript existence, replace tasks, attach metadata, and remove the
  queue entry in one transaction. Note deletion cancels and awaits analysis before removing the
  transcript and tasks, so native generation returning late cannot recreate reminders. Drain
  scheduling uses a synchronized single-job guard shared by callers on different threads.
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

Google Play uploads use `./gradlew bundleRelease` and an `.aab` signed with a private upload
key. Keep the keystore and its password outside the repository. The debug APK is for local installs.

Release builds run `lintVitalRelease`. Keep the Fragment dependency constraint: Google Play
services brings in Fragment 1.1.0 transitively through Billing, which fails the Activity Result
API compatibility check. Upgrade the transitive library rather than suppressing that check.

Wireless debugging drops often: `adb connect <ip:port>` from Settings → Developer options → Wireless debugging.
Instrumented tests use the separate `uitest` application (`dev.andrej.echo.uitest`) so they
never install over the user's notes. `testUitestUnitTest` runs JVM tests; `assembleUitestAndroidTest`
builds device tests, including task reminder delivery/navigation and Compose gesture checks.
The `uitest` variant also needs `ui-test-manifest` on its own implementation classpath: a
`debugImplementation` dependency alone does not supply Compose's host activity to this build type.
Grant the mic without the dialog: `adb shell pm grant dev.andrej.echo android.permission.RECORD_AUDIO`.
Recording starts behind a locked keyguard (`am start -n dev.andrej.echo/.MainActivity --ez
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
              and tasks prompts + parsers), TextChunks,
              TranscriptAnalyzer, AnalysisOutcome/AnalysisBlock, PendingAnalysisWorker
data/         Transcript + Segment, TranscriptRepository, TodoItem + DerivedRepository, FolderRepository,
              PendingAnalysis + AnalysisQueueRepository, each Sql*Repository over one
              EchoDatabase (Database.kt), SettingsStore
sqldelight/   the schema, one .sq file per table (generated code in dev.andrej.echo.data.db)
ui/theme/     OutLoud design tokens (Color, Type, Shape, Spacing, Elevation, Motion, Theme)
ui/components/ the ported design system kit
ui/folders/   FoldersScreen (the Folders tab) + FoldersViewModel (FolderIcon), FolderScreen (one
              folder's notes), FolderSheet (create/edit/delete)
ui/tasks/     TaskRows (TaskGroupCard/TaskRow/TaskCheckbox for the note screen), EditTaskScreen +
              TasksViewModel
ui/capture/   RecordingRow — timer/pause controls above a full-width waveform; static record button below
ui/record/    RecordViewModel + RecordUiState over RecordingController (still named for its old screen)
ui/onboarding/ OnboardingScreen + OnboardingSteps (the 6 first-run screens), OnboardingViewModel,
              OnboardingSetup (recommendation, setup summary, download status)
ui/profile/   SettingsScreen + SettingsViewModel (the Settings tab), StorageScreen, HistoryScreen +
              HistoryViewModel (the Calendar tab), DictionaryScreen + DictionaryViewModel
ui/notes/     NotesScreen + NotesViewModel (the Notes tab, search; NoteRow, NoteStage,
              PendingBadge, PendingCopy), NoteCard (+ its ··· sheets: move, delete)
ui/detail/    NoteDetailScreen (header, By speaker / Full text / Tasks tabs, sheets), TranscriptViews
              (rows, find/edit bars), TranscribingCard (live progress), PlayerBar (ProgressRow, PlayButton), NotePlayer (ExoPlayer), TranscriptEdits
              (find/replace/edit/speaker logic), SpeakerSheet ("Who is this?"), ShareSheet
export/       ExportFormats (txt/md/srt, pure) + Exporter (cache file → FileProvider → share sheet)
notify/       RecordingNotifier (+ RecordingActionReceiver, BootReceiver), ModelDownloadNotifier
              (+ ModelDownloadActionReceiver)
AppContainer manual DI, no Hilt
```

Four tabs: Notes / Folders — record button — Calendar / Settings, Notes the start destination.
**Notes list** (2026-11-02 design): a flat list of `NoteCard`s, newest first, no day headings —
title, summary (3 lines), then `date · duration · folder` (folder name and icon in the folder's
own colour; a `PendingBadge` takes that slot while anything is pending). The left stripe is
progress (`NoteStage`, from `stage()`): empty grey while waiting (queued, getting a model, parked,
or never analyzed), fills top to bottom with Whisper's %, pulses while finding speakers or
analyzing, solid bronze once `analyzedAt` is set, clay on failure. ··· opens Move to folder /
Delete. Long-pressing a card starts **multi-select** (Notes only): taps then toggle, the top bar
becomes "N selected" with Move to folder and Delete (`BulkActionSheets`), back or ✕ leaves.
`FolderScreen` uses the same cards without the folder label; Calendar keeps its rows.
Header is the mascot + "Echo", with a "Pro" badge when the billing entitlement is active.
Notes is the one list of everything; Folders lists folders, each opening its notes (no folder
filter on Notes); Calendar is `HistoryScreen` (month heat grid, the picked day's notes open on
tap). Tasks sit on their due day, not their note's day, as sage dots beside the bronze note dots;
the picked day lists them above its notes. Undated tasks have no day and stay off the calendar. Settings opens directly from the fourth tab. A note is moved from its own screen (folder icon in
the bar, `PickerSheet`). Raw transcript text lives only in each note's screen. The record
button sits in the bar's middle slot (`TabBar(centerGap = true)` leaves the gap, `EchoApp` overlays the
button on a bar-coloured cradle so it pokes above the hairline). The tab bar is icon-only — no labels, no
selection crossfade — and the NavHost runs with every transition set to `None`: the design's
screen changes read as jank on device, so switching is a hard cut. Tab switches go through `NavHostController.selectTab`: Notes pops back to Notes, other tabs
`popUpTo(NOTES)` + `launchSingleTop`. Never `saveState`/`restoreState` — the saved tab stack keys
to the start tab, so tapping it restored the stack and landed on the last tab. Cost: tab scroll position is not kept.
`EchoApp` gates on `AppContainer.onboardingDone` outside the NavHost (see Onboarding below).

Settings follows the "Echo Settings" design canvas (https://claude.ai/artifact/Bj7HiPxSm7RvZcwLizABhB):
an Echo Pro card (upsell when free, plan and renewal when Pro), then Transcription, Notes AI, Recordings,
Task reminders, Appearance, Storage and data, and About. Speech and notes AI models use matching rows;
their sheets expose selection and download actions. The Notifications switch mirrors Android's state and
only opens the permission prompt or channel settings. The feedback address and privacy URL in
`SettingsScreen.kt` are placeholders until published.
Appearance also holds Haptics and Reduce motion switches (`SettingsStore.hapticsEnabled`/`reduceMotion`, flows on `AppContainer`, passed into `EchoTheme`). Both add to Android's settings rather than replace them: the system haptic setting and animator scale still apply.

## Notes

- No Room: KSP has no release matching Kotlin 2.4.10.
- **Backup policy:** Android cloud backup and device transfer exclude all app storage domains,
  including device-protected storage. `allowBackup=false` is paired with legacy full-backup and
  Android 12+ extraction exclusions because some manufacturers transfer data despite that flag.
  Notes and audio leave through explicit Share/export only; there is no automatic restore on a new phone.
- **Manual backup** (`data/Backup.kt`, Settings → Back up notes): one zip with a manifest, `echo.db`
  (+ `-wal`) and `recordings/`. Models and preferences are left out. The database files are copied
  inside an open write transaction, so no commit or checkpoint can tear the copy. Restore only stages the
  zip under `files/restore/` (it refuses a newer schema and entries that escape the folder), stops
  transcription and analysis work, and restarts the process. `Backup.applyStaged()` swaps the files in
  before the driver opens, because the live connection can't be replaced under the repositories.
  Rules follow https://developer.android.com/identity/data/autobackup.
- **Offline-first, no account.** Recording, playback, transcription, speaker detection and analysis
  run locally once their models are downloaded. Network traffic includes model downloads and
  RevenueCat/Google Play purchase and entitlement requests. RevenueCat uses an anonymous app user
  ID; audio, transcript text and tasks are not sent for billing. Google sign-in, Supabase auth and
  sync were removed; nothing in the app talks to the old Supabase project.
- `ui.notes.title()` derives a title from the opening of the text (first sentence, or
  six words) — the fallback for a transcript analysis has not named yet, not a permanent scheme.
  Note cards prefer `title`/`summary` when present. Since Phase 5 `summary` is the
  gist — a sentence or two, a short paragraph or bullets for a long note. Summaries appear only
  as note-card descriptions in the UI; note details have no Summary tab, and Tasks contains only tasks.
- Note details' **More → Rename note** edits the displayed title, including its text-derived
  fallback before analysis. Save trims whitespace and requires a nonblank title; Cancel discards
  the draft. Renaming sets `transcript.titleByUser`, so re-analysis can refresh the summary and
  tasks without replacing the user's title, including when analysis was already running.
- Task and Reminder unified into one `TodoItem` (`text`, `dueAt` — never null, `hasTime`,
  `hasDate`, `done`). Without a stated date, `dueAt` is a placeholder and `hasDate=false`;
  the UI leaves the deadline unset. `hasTime` records whether a clock time was stated.
  `resolveDue` (in `ai/AnalysisPrompt.kt`)
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
  `TasksViewModel.groupByDue` (from the removed Tasks tab; `EditTaskScreen` reads tasks through
  it) groups undone items into three buckets only: **Today**
  (folds in every overdue item too, sorted ahead of the rest of today's), **Tomorrow**, and one
  flat **Upcoming** group for everything later (`DueGroup.showDate = true` there, so `TaskRow`
  formats its "when" label with `formatDue` instead of `formatTime`). Done items never appear in
  `groupByDue`'s output at all — they collapse into a separate "Completed" section instead
  (`completedByRecency`, most recently checked off first), shown behind a `CompletedHeader`
  toggle at the bottom of the list so a long history of ticked items doesn't push undone work off
  screen. `isOverdue` treats timed and date-only items differently: a timed item is overdue the
  moment its clock time passes, even earlier today, but a date-only item due today isn't overdue
  until tomorrow. Within a group, timed items come first (soonest first), then date-only items
  (oldest-created first). `EditTaskScreen` (long-press a task in a note) lets you retitle a task, change its
  day/time via native `DatePickerDialog`/`TimePickerDialog`, delete it
  (`DerivedRepository.update`/`delete`, alongside `replaceFor`/`setDone`/`deleteFor`), and reach the source transcript only
  from its own "Heard in" row. `TasksViewModel` takes a `TranscriptRepository` for that lookup, on
  top of `DerivedRepository`. Due-time formatting (`formatDue`, `formatTime`, `Long.midnight()`)
  lives in `ui/DueLabels.kt`.
  Notes' search is a `query` `MutableStateFlow` folded into its `combine` before grouping. It uses
  the `EchoTopBar` overload
  (`title`/`query`/`onQueryChange`/`searchPlaceholder`) that toggles a trailing search icon into an
  inline `EchoSearchField` in place of the title, rather than each screen keeping its own
  always-visible field.
- `derivedId` takes no `kind` — one entity, one id — but keeps the null-byte separator between
  transcript id and text: a plain concatenation would let `"t1"` + `"x"` collide with
  `"t"` + `"1x"`.
- Deletes are hard deletes, no tombstones. `TodoItem.updatedAt` stays: the Completed section
  sorts by it.
- Derived rows are keyed by `UUID.nameUUIDFromBytes(transcriptId + kind + normalized text)`, not
  minted fresh, so re-analysis lands on the same ids and `Task.done` survives it. Before this,
  Analyze silently un-ticked every task. Two rows with the same text under one transcript collapse
  to one (`distinctBy`) — a repeated task from a single note is noise.
- **Note screen** (2026-11-02 design, `ui/detail/`): back button, then a pill of Find / Edit /
  Share / ···, a large title, `date · duration · [folder ▾]` (the chip opens the folder picker),
  and a status card while transcription or analysis is pending. Sticky tabs, each shown only once its stage is done — none while Whisper runs, **Full text**
when it finishes (and selected then), **By speaker** once speakers are found, **Tasks** once
analysed: **By speaker** (runs
  of one speaker under `● Speaker 1  0:00 – 0:14`; the playing run gets a card), **Full text**
  (the same segments as paragraphs) and **Tasks** (the summary, then the note's tasks — tap
  plays from `sourceOffsetMs`, long-press opens `EditTaskScreen`). ··· holds Re-analyze, Copy
  text, Delete. The tab bar stays on a note, lit on the tab the note came from, with the play
  button (`PlayButton`, the record button's bronze disc) in the record button's slot — a screen
  fills that slot with `BarCenter { … }` (`LocalBarCenter`). Directly above the bar, no hairline
  between, `ProgressRow`: elapsed / slider (dot thumb, thin track) / remaining. No skip buttons;
  speed was dropped until it has a better home. The screen sits above the bar via
  `LocalBottomBarHeight` rather than behind it.
  `NotePlayer` wraps a Media3 ExoPlayer over `recordings/<id>/source` (`Transcriptions.audioFile`;
  a recording has one only once transcribed), released with the screen and paused on `ON_STOP`.
  **Edit** turns lines into text fields, saved on Done; **Find** highlights and replaces all
  (case-insensitive literal). Both go through `TranscriptRepository.editSegments`, which rejoins
  `transcript.text`; a segment emptied is dropped. Not offered while still transcribing (each
  chunk rewrites the segments). Pure logic in `TranscriptEdits.kt`.
- **Analysis reading sweep** (`ui/detail/AnalysisReading.kt`): active analysis waits for the
  remaining transcription typing, selects Full text once, and returns to the beginning. A soft
  rounded semantic accent marker advances through actual rendered word bounds at roughly 5 words
  per second, gliding between words on the same line and crossfading at line or paragraph breaks.
  A stronger translucent fill and fine outline keep it visible in both themes; eased scrolling
  keeps the word visible. At the end it returns
  to the beginning and loops while analysis is active. This is ambient activity, not
  sentence-level model progress; the processing row retains real completed-step progress. Dragging,
  changing tabs, tapping a paragraph, finding, editing, or selecting stops the presentation until
  a fresh analysis attempt or screen entry, so it cannot pull the reader away from their work.
  Reduced motion uses a static highlight on the first visible passage without resetting or
  scrolling. Typing-follow and analysis-follow scrolling never run concurrently.
  Full text supplies the joined transcript as one measured reading layout, matching its continuous
  text rendering; tapping text or a task-source link stops the sweep before playback or navigation.
- **Share = export** (Phase 9, pulled in with the note screen): Text `.txt`, Markdown `.md`
  (title, date, summary, tasks as a checklist, transcript), Subtitles `.srt` (only with
  segments), Audio (the `source` file, its MIME read with `MediaMetadataRetriever` since `source`
  has no extension). Speaker names and Timestamps toggles; timestamps split one speaker's run into
  a line per segment, srt is always timed. Files go to `cache/export/` (cleared each time) and out
  through `FileProvider` (`${applicationId}.files`).
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
  churn. `NotesViewModel.pending` and `ui.notes.group()` turn
  each queue entry's `lastBlock` into a `PendingCopy` (`ui/notes/NotesViewModel.kt`)
  — a plain `{text, severity}` pair, no Compose types, so it stays testable without a device. An
  entry only gets copy once the drain has actually attempted it (`lastBlock != null`); a
  freshly-enqueued row shows nothing for the moment before that. `PendingBadge` (in
  `ui/notes/NotesScreen.kt`, reused by the detail screen) renders it as an
  `EchoBadge`: `Parked` (waiting on a model or backend) in `BadgeTone.Neutral`, `Failed`
  (`GenerationFailed`/`EmptyResult`, exhausted or not — the badge doesn't distinguish) in
  `BadgeTone.Warning`. `EmptyTranscript` never renders — it's never enqueued. No dedicated
  first-run modal; the badge is the only affordance, on note cards and the detail screen, which already has the Analyze button to retry with.
- **Settings and History** are reached directly from their tabs. `ProfileScreen` and its stats
  were removed; there is no account or profile page in the offline app. `AiViewModel` is gone: its
  Download/Cancel/Remove UI moved into `SettingsScreen`'s ON-DEVICE AI card, and `SettingsViewModel`
  now takes the same `aiCardState`/`onDownloadModel`/`onCancelDownload`/`onDeleteModel` shape
  directly from `AppContainer`. `SettingsStore` beyond `transcriptionLanguage`: `detectSpeakers`, `autoAnalyze` (gates the
  enqueue after transcription — off means every note waits for a manual Analyze),
  `wifiOnlyDownload` (checked in `AppContainer.downloadModel()` via `ConnectivityManager`, download
  is skipped rather than queued if off Wi-Fi). The ON-DEVICE AI card shows the one model, no backend picker. Settings' DATA section opens
  `StorageScreen`; `scanStorage` totals models, recordings, the Echo database (including its WAL),
  preferences, cache, and other app files. The category totals sum to the displayed total and
  display rounded MB/GB sizes. Scanning runs off the main thread and refreshes on opening the screen.
  **Delete all data** is behind `ConfirmSheet`:
  `AppContainer.deleteAllData()` clears the pending queue, every `TodoItem` and every transcript, via each repository's `clear()`. It keeps the model, settings and
  `onboardingDone`. `HistoryScreen`/`HistoryViewModel` render a month calendar
  (`YearMonth`/`LocalDate` state, not persisted) with a 4-level heat grid (0/1/2/3+ notes that
  day), month totals, and a selected-day detail list; `nextMonth()` refuses to advance past the
  current month. `EchoSwitch` (44×24 pill, 20dp thumb) moved from a private composable in
  `EditTaskScreen.kt` to `ui/components/EchoSwitch.kt` now that Settings needs it too, gaining an
  `enabled` flag for the read-only FEEL rows. `ui/components/BottomSheet.kt` is new and generic:
  `ConfirmSheet` (destructive confirmation) and `PickerSheet` (single-choice list with a check
  mark, optional leading icon) are both hand-rolled `Box`/`AnimatedVisibility` scrim+sheet overlays, not Material's
  `ModalBottomSheet` — nothing else in the app uses one, and the design's plain white sheet with no
  system scrim animation is simpler to hand-roll consistently with `echoShadow`/`hairline`. A sheet draws inside its
  screen, under `EchoApp`'s tab bar, so every open `Sheet` counts itself in `LocalOpenSheets`
  and the bar fades out while any is open — otherwise the bar covers the sheet's buttons.
  Menus (a note's ···) use `ActionSheet` + `ActionRow`: a title/meta header, icon rows with an
  optional one-line consequence and trailing current value, and a divider before the destructive
  row. Prefer rows over stacked buttons for any list of actions; `ConfirmSheet` stays for the
  irreversible step after a menu row.

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
- **Light and dark, with a persisted System / Light / Dark choice; no dynamic color.**
  `EchoTheme` resolves `SettingsStore.themeMode` against `isSystemInDarkTheme()`; Dark is the
  default. `AppContainer.themeMode` updates the entire composition immediately when Settings
  changes it. Dark stays warm browns, never charcoal.
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
  `ContentScale.Fit` into a square, so a non-square canvas both shrinks and offsets him. Two
  prop variants sit beside it (`mascot_tasks`, `mascot_notes`) and are
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
- Ambient animation (breathing and dots) must check `LocalReducedMotion`, which
  reads `ANIMATOR_DURATION_SCALE`. It is decoration, not feedback. `Waveform` is the exception:
  its bars are a scrolling history of the real mic level, so they keep moving regardless.
- Haptics live in `ui/theme/Haptics.kt`, reached with `rememberEchoHaptics()`, and are named by
  meaning: `tick()` (button, icon button, tab, pill and plan-card taps), `engage()`/`release()`
  (record button), `toggle(on)` (`EchoSwitch`, switch rows), `threshold()`/`thresholdBack()` (sheet
  drag crossing its 64dp dismiss point), `confirm()` (task completed; uncompleting ticks),
  `scrubTick()`/`scrubBound()` (player slider per 10 s mark and at the ends), `success()` (a take is
  saved, from `RecordingController.filed`, or a restore activates Pro) and `celebrate()` (purchase).
  View-based calls use `View.performHapticFeedback`, which honours the system setting; the API 34
  constants fall back to older ones. `success()`/`celebrate()` use the Vibrator, so they need the
  `VIBRATE` permission and their own `HAPTIC_FEEDBACK_ENABLED` check. `LocalHapticsEnabled` gates all
  of them, and `EchoTheme` swaps `LocalHapticFeedback` for a silent one when it is off, because
  Compose 1.11 `combinedClickable` already plays its own long-press haptic.
- `celebrate()` composes QUICK_RISE → THUD → TICK when every primitive is supported, else an
  amplitude waveform, else `EFFECT_HEAVY_CLICK`. Composition delays count from the end of the
  previous primitive, and the Galaxy S24's THUD runs 300 ms, so the timing is built from
  `getPrimitiveDurations` and `celebrate()` returns when its tick plays; `ProWelcome` starts the
  second ring then. `PaywallState.arrival` tells a purchase (celebrated) from a restore
  (`success()` only). While the purchase is `busy` the mascot starts hidden, because RevenueCat
  flips `isPro` before `purchase()` returns. Debug builds preview it from Settings › DEBUG.
- `EchoTopBar` has three forms: a `title`/`subtitle` one, a slot one taking arbitrary centre
  content, and a searchable one (`title`/`query`/`onQueryChange`/`searchPlaceholder`) that
  delegates to the other two depending on whether its search icon has been tapped. All three draw
  the same bar chrome, so every screen header keeps one surface, hairline and height.

## Billing

- RevenueCat uses an anonymous app user ID and the `pro` entitlement. Google Play products are
  subscription `echo_pro` (base plans `monthly`, `yearly`) and in-app product `echo_lifetime`.
  RevenueCat offering `default` supplies `$rc_monthly`, `$rc_annual`, `$rc_lifetime`; all displayed
  prices and available trial details come from the offering, with Yearly selected by default.
- Configure Play products and the RevenueCat project/service account outside the app. Put the
  public Android SDK key in `local.properties` as `revenuecat.apiKey`. Missing-key and `uitest`
  builds use fake billing and cannot make purchases. Never commit the key.
- Free users transcribe the first two minutes of each recording or import; the entire original
  audio stays available for playback. Speaker detection reads only the same two minutes, so a long free
  import does not spend minutes finding speakers in audio that has no text; the unlock run
  diarizes the whole file and maps its speakers onto the prefix's by overlap. A recording
  crossing two minutes opens the paywall once and continues while it is open or dismissed.
  Imports do not prompt. Capped notes end with a cap card (minutes left, "Transcribe the rest with Pro"). After purchase or restore the paywall route becomes a Pro welcome screen showing the capped note's unlock progress; the note is captured at paywall entry because Pro clears the cap immediately. The renewal date comes from the RevenueCat entitlement `expirationDate` (null for lifetime or a cancelled plan) and is not cached offline. TXT export is free; Markdown, SRT
  and original audio exports require Pro.
- `transcript.transcribedUntilMs` marks a capped result. Pro activation queues capped notes,
  resumes Whisper at that boundary with the existing segments, clears the cap on completion and
  re-queues automatic analysis with a fresh attempt count. Continuation keeps saved prefix edits,
  speaker names and assignments. Recovery joins the saved prefix and checkpoint suffix at the cap's
  timestamp: edits can split or delete segments, and stopped runs can save partial text beyond the
  checkpoint that must be transcribed again. Capped manual retries retain the boundary and latest chunk; free
  retries can finish interrupted audio finalization. Chunk snapshots and the speech-complete marker preserve process
  restart recovery during both the capped and unlocked runs.
- Last known Pro status is cached in Settings before network requests, so offline cold starts
  keep the entitlement. Successful RevenueCat CustomerInfo updates replace it; network failures
  leave it intact. Purchases and restoration require internet. Settings exposes restore to
  everyone and links Pro users to Google Play subscription management.
- **Privacy/Data safety:** RevenueCat uses an anonymous app user ID and collects purchase history.
  Its [Google Play disclosure guide](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety)
  requires Purchase history for app functionality and analytics; this integration sets no customer
  attributes or advertising identifiers. Review disclosures again if integrations are added.
  Audio and note content stay local except an explicit user export. Andrej
  updates the feature spec PDF's privacy row and export-limit question; the PDF is not edited here.
  Terms and privacy URLs remain labeled placeholders until published.
- Keep the README billing checklist open until verified through a Play internal-testing track
  and license tester: recording/import caps, export locks, purchase/unlock, restore after clearing
  app data, offline Pro cold start, and process death while unlocking.

## Notifications

Channels: task reminders, recording (`RecordingNotifier`, ongoing while `RecordingService` runs),
record shortcut (the idle "Tap to record", `IMPORTANCE_MIN`, its own channel because importance can't
be lowered on an existing one), notes ready (`NoteStatusNotifier`), model download progress
(`ModelDownloadNotifier`), and transcription progress (posted by `TranscriptionWorker` itself).

Design: claude.ai/code/artifact/fafde722-3aa6-4a0a-a851-c9fa3332416c ("Echo widgets and
notifications"). Bronze `setColor` on every Echo notification; clay only while a recording is live.

- **Task reminders** use `TaskReminders`, `AndroidTaskReminderDelivery`, and
  `TaskReminderReceiver`. Explicit times use the resolved instant; date-only tasks notify at
  09:00 local time; undated tasks notify one hour after their first database save, independent
  of the recording's age. Past reminder times notify immediately. Defaults are stored separately
  from task deadlines so undated tasks remain undated in the UI.
- Reminder time, original local date (date-only), and delivery state persist in `todo`.
  Migration leaves existing tasks without reminders to avoid an upgrade notification flood.
  Re-analysis preserves an unchanged task's countdown, creation time, and delivered state.
  Deadline edits and reopening re-arm reminders; reopening an overdue reminder delivers now.
  Completing/deleting tasks cancels pending alarms and visible notifications.
- AlarmManager uses exact idle alarms when permitted and inexact idle alarms otherwise.
  Settings → Task reminders exposes notification and exact-alarm system settings; inexact
  delivery can be delayed by Android. Android 13+ notification permission is offered once when
  pending task reminders exist; refusal never blocks task creation. Pending reminders blocked
  by permission denial are retried when the app is foregrounded.
- Boot, app update, time/timezone changes, exact-alarm permission grant, and app foreground
  reconcile pending reminders. Persisted local dates prevent a timezone change from moving a
  date-only reminder to the wrong calendar day. The receiver claims current task state
  transactionally before posting, ignoring stale or duplicate broadcasts. Tapping opens task
  editing through an Activity extra, deferred through onboarding like the capture action.

- **`MY_PACKAGE_REPLACED` and reboot clear the idle recording notification**, so
  `notify/BootReceiver.kt` listens for both and re-posts it. It is *not* `directBootAware` and does
  not listen for `LOCKED_BOOT_COMPLETED`: `filesDir` is credential-encrypted and unreadable before
  first unlock.
- The record action without `RECORD_AUDIO` granted opens `MainActivity` with
  `EXTRA_OPEN_CAPTURE`, an `Intent` extra rather than a Navigation-Compose deep link: `EchoApp`
  renders `OnboardingScreen` *outside* the `NavHost`, so a cold-start `handleDeepLink` would be
  dropped. `MainActivity` holds the flag in `Activity` state (the extra removed each time so a
  config-change recreation doesn't renavigate) and passes it to `MainApp` to start recording.
  `FLAG_ACTIVITY_SINGLE_TOP` as an *Intent* flag gets `onNewIntent` delivery against the default
  `standard` launch mode.
- `POST_NOTIFICATIONS` is requested when starting a recording together with `RECORD_AUDIO` (API 33+): the
  recording controls and transcription progress live in notifications. Only the mic is required
  to record; denied notifications mean `notify()` silently no-ops.
- The recording notification is Idle ("tap to record"), Recording or Paused (`RecordingMode`),
  with Pause/Resume and Stop actions into `RecordingService`; tapping it opens the app with the recording row. It is
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
- **Idle notification is opt-in** (`SettingsStore.idleNotification`, default off; Settings →
  Recordings). Widgets and the tile cover quick capture. Recording shows a system chronometer
  (`setWhen(now - elapsedMs)`) so the notification is re-posted only on mode change; "Stop and save"
  reads that way so nobody fears losing the take. `RecordingNotifier.foreground` is what the service
  posts: the service starts before the controller, so an idle state there still reads as recording.
- **Reminder actions**: Done and Snooze 1h go to `TaskActionReceiver` →
  `DerivedRepository.setDone`/`snooze`, then `TaskReminders.dismiss`; `reconcile` re-arms the alarm
  from the row. Snooze moves a timed task's time an hour past its time (or past now when overdue) so
  lists show the new time; date-only and undated tasks keep their deadline and only remind again.
- **Note status** (`NoteStatusNotifier`) posts only while no Echo activity is started
  (`AppContainer.appVisible`, set from `MainActivity.onStart/onStop`), one card per note (tag
  `note:<id>`, a group summary from two up), private on the lock screen. Note ready comes from
  `PendingAnalysisWorker.outcomes` (`noteStatus()` maps outcome → kind); Transcript ready from
  `Transcriptions.transcribedOnly` (auto-analyze off) or a parked analysis (`WaitingForModel`,
  `NoBackend`); Couldn't finish from `GenerationFailed`/`EmptyResult` or the transcription worker's
  final `Result.failure`. Retry uses `AppContainer.retryNote`, the same split as the note's retry
  button. Opening the note cancels its card. A parked note is retried on every drain, so the notifier
  remembers what it showed per note.
- Note and calendar deep links use Activity extras like `EXTRA_OPEN_CAPTURE`: `EXTRA_OPEN_NOTE`
  (+ `EXTRA_OPEN_NOTE_TASKS` → `detail/{id}?tab=tasks`) and `EXTRA_OPEN_CALENDAR`. There is no
  standalone tasks screen, so "today's tasks" links open the Calendar tab.

## Widgets

- Glance (`androidx.glance:glance-appwidget`), one `GlanceAppWidget` + receiver per widget in
  `widget/`: Record 1×1 (`SizeMode.Exact`, the disc fills the cell, no elapsed time), Combined 4×1,
  Tasks (responsive narrow/wide), Recent notes 4×2.
- `WidgetUpdater` (in `AppContainer`) builds one `WidgetSnapshot` from recording mode, tasks, notes
  and pipeline `Work`, and pushes at most once a second (transcription progress moves per
  percent). It also re-ticks every 15 min and on app foreground, since overdue rows, the day and mic
  permission change outside any flow. Widgets `collectAsState` the snapshot while their session lives.
- Pushes render each placed widget with Glance `compose()` and call `AppWidgetManager.updateAppWidget`
  directly. Glance's `updateAll` goes through a WorkManager `SessionWorker`; One UI cancels that job
  when Echo is swiped from recents (stop reason 11) and holds it until the app opens, so a widget
  stayed on the record disc through a whole take. Only `actionRunCallback` and intent actions are
  used, since lambda actions need a live session.
- `SizeMode.Responsive` picks the largest bucket that fits, else the smallest. Tasks buckets are
  110 dp tall: at 200 dp a One UI 4×2 (194 dp) fit neither and got the narrow layout without times.
- Tasks shows today (overdue included); when that's empty, the first later day with dated tasks,
  labelled Tomorrow, a weekday within the week, or a date. Tasks and Recent scroll (`LazyColumn`).
- `RecordDisc` always reserves the 5 dp live halo, so starting a take doesn't shift neighbours; the
  4×1's middle slot is one fixed width across idle/recording/paused for the same reason.
- Picker previews are Glance `providePreview` with `previewSnapshot`, pushed by `setWidgetPreviews`
  (Android 15+) once per install/update; the system rate-limits it, so a failed push retries on the
  next start.
- Ticked rows stay ~2 s struck through (`justDone`); snoozed rows show "time · +1h" for ~4 s.
- Elapsed time uses a framework `Chronometer` via `AndroidRemoteViews` (`widget_chronometer_*.xml`),
  so a live recording needs no per-second widget updates.
- Colours are day/night resource pairs in `values*/widget_colors.xml`, following the system theme,
  not Echo's in-app theme. Rounded shapes are drawables (`widget_*.xml`) because Glance's
  `cornerRadius` is ignored below Android 12.
- Disc taps: idle → `RecordingActionReceiver` (starts directly, opens the app when the mic isn't
  granted), recording → `ACTION_STOP`, paused → `ACTION_RESUME`. `RecordTileService` does the same
  from Quick Settings and ticks its subtitle only between `onStartListening`/`onStopListening`.

## On-device AI

- Analysis is an explicit transcription-job phase: the job waits for the analysis queue, so
  leaving its last speech status in place would show “Finding speakers… 100%” until analysis ends.
  The same detail progress row shows “Analyzing…”, the running runner's model name, and completion
  of summary, optional merge, intent and task steps. Retries do not advance the percentage;
  skipped task extraction completes its step. Manual analysis uses the same progress stream.
  Progress is keyed by note and clears on completion, failure or cancellation. Each transcription
  job waits only for its own analysis attempt, so other queued notes cannot hold it at 100%.
  WorkManager retains phase progress for automatic analysis. This is step completion, not an
  elapsed-time estimate.

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
  and warmed up when recording starts, from an application-scoped coroutine in `AppContainer` —
  navigating away must not cancel a half-finished init.
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
  The intent prompt explicitly counts personal obligations ("I have to", "I need to") and
  appointments as plans: without positive examples, the small model answered false for a
  barber/store itinerary and silently skipped extraction. Shared dates carry across errands;
  vague filler such as "some stuff to do" is not a task.
  This replaced the 3000-word truncation.
- **Task text must name an action.** For "tomorrow I have a doctor's appointment at 10 a.m. and
  … a reminder for tomorrow at 6 p.m. that I have to go to the mechanic", the 1.7B model wrote
  "Tomorrow at 10 a.m." and "Tomorrow at 6 p.m." as the texts (dues correct). The prompt now has
  an appointment-plus-reminder example, and `isScheduleOnly` rejects time-only text: such an
  answer uses the step's retry, and whatever is still time-only on the last attempt is dropped.
- Task text uses sentence case and final punctuation in the extraction prompt. `formatTaskText`
  also formats task rows and the initial edit text so older lowercase model output reads correctly
  without re-analysis; it preserves existing punctuation and capitalization within the sentence.
- **Task schedules are grounded in source text.** `TaskSchedule.kt` checks extracted tasks
  against the transcript before saving. Explicit English relative dates, weekdays, ISO dates,
  am/pm and 24-hour clocks, noon/midnight, all-day wording and numeric durations override model
  arithmetic. Shared dates carry forward; durations use recording time. Unsupported expressions
  retain the model result. The prompt must not tell the model to use today's date for every
  clock: this caused all four tasks in a tomorrow itinerary to land today or lose their time.
- **Tasks link text and audio.** The prompt requests a verbatim `source` quotation. `TaskSource.kt`
  validates it against the transcript and conservatively matches action words when an extraction
  omitted or paraphrased its quote. Migration `8.sqm` adds nullable `todo.sourceQuote`; legacy
  rows remain usable through action matching. Sentences can cross Whisper segments, and `a.m.` /
  `p.m.` do not split a sentence. The saved offset and task playback use the segment containing
  the action words rather than trusting the model's line number. No word timing is available.
  Verified source spans have muted colors assigned in transcript order in Full text and By speaker;
  tapping opens Tasks and briefly emphasizes the entire matching row with the same color.
  Read-only text flows continuously across Whisper segment boundaries (within each speaker run
  in By speaker); offsets still map taps and long presses to the original segments. Editing and
  selection retain segment fields and suppress links. Stored quotations removed by transcript
  edits produce no link or playback jump. Compact task rows share a grouped outline while remaining
  individual lazy items, so destinations outside the viewport remain reachable; sticky tab height
  is reserved on scroll.
- **Polishing was removed** (2026-11-02, Andrej's call): a note is By speaker and Full text,
  nothing more. `transcript.polishedText` stays in the schema unread — platform SQLite before
  3.35 can't `DROP COLUMN` and a table rebuild wasn't worth it; `selectAll` names its columns so
  the model class doesn't carry it.

## Onboarding

Six screens (`ui/onboarding/`): Welcome, How it works, What will you record? (+ language), What
matters most?, Your setup (downloads), First note. Progress shows five segments after Welcome. One
composable switches on `OnboardingStep` — no NavHost, hard cuts like the rest of the app; the back
arrow and system back step back (choices live in `OnboardingViewModel`, so they survive), and
system back leaves the app on Welcome. Body content scrolls but is at least the viewport's
height, so First note centres the record button in spare space and fits on one page on normal
phones; actions stay pinned below it.

- **Gate:** `AppContainer.onboardingDone` = `SettingsStore.onboardingDone`, or any transcript
  already on disk — an install that predates onboarding goes straight to Notes.
- **Nothing downloads until a button says so.** Welcome starts nothing. The Setup screen's
  "Download on Wi-Fi" and "Download now with mobile data" call `AppContainer.startSetupDownloads`,
  which enqueues the unique `ModelDownloadWorker` (`onboarding-downloads`): `UNMETERED` for Wi-Fi
  (KEEP), `CONNECTED` for mobile data (REPLACE, so it supersedes a job waiting for Wi-Fi). A job
  survives the app being killed; partial files resume. The worker downloads Whisper, the VAD file,
  the speaker models when speaker detection is on, then Notes AI through `downloadModelNow`
  (the Wi-Fi-only *setting* is not consulted; the button or job constraint is the user's choice,
  and `wifiOnlyDownload` is never changed). "Set up later" downloads nothing; the first recording's
  transcription job still fetches Whisper itself. A note recorded mid-download queues and
  auto-analyses when the model lands (existing drain trigger).
- **Recommendation** (`OnboardingSetup.kt`, pure): Faster → Whisper Small; More accurate → Large
  V3 Turbo, and that option is hidden when `SpeechModels.choices` lacks it (phones under ~7 GB);
  Help me choose → `SpeechModels.default`. Speaker detection is on only when Meetings is selected.
  Continuing from the priority screen applies language, speech model and speaker detection through
  `SettingsStore`; Skip on the record/priority screens jumps to Setup and changes nothing. Compare
  models on Setup picks from `SpeechModels.choices` and writes `speechModel` immediately.
- **Sizes are real** (`ModelSpec.bytes`); "No download" / "Ready" appear only when the model is
  already on disk. There is no bundled model and no Echo Pro copy.
- **Download status** on First note is `AppContainer.setupStatus`: the needed stores' states plus
  the worker's WorkManager state — Waiting for Wi-Fi (job queued, nothing downloading),
  progress, Failed (+ Retry, mobile data), Done. Failure never blocks entering the app.
- **Mic permission is asked at the record button**, not on its own screen. Granted → finish, then
  `MainApp` starts recording through the pending-open-capture flag the recording notification
  uses. Denied → stays on First note with a "Microphone blocked" note; Import audio and Explore
  first remain. Import audio → finish, then `MainApp` opens the import picker through
  `pendingOpenImport` in `EchoApp`.
- How it works' demo rows are real `TaskRow`s over in-memory `TodoItem`s (never stored), with
  dates relative to now. Tapping a highlighted phrase (or a row) focuses that task with
  `taskHighlight`, like note detail; rows sit in a clipped box so the press ripple and highlight
  follow the rounded corners. Copy says tasks appear "after a short wait", since analysis is not instant.
- Priority cards share one height (`EqualHeightColumn`), and `ChoiceCard` always reserves the
  check slot, so selecting never re-wraps text.
- Button labels follow the design system's bumped scale: Sm/Md/Lg = 14/15/17sp semibold (was
  13/14/15 medium). The same update made the record glyph (`recordGlyph`, mic and stop on the
  record gradients) dark ink instead of white, darkened `textTertiary`/`waveformIdle`/
  `indicatorIdle`, added `controlTrack` for the off-state switch track, and moved `recordLive` to
  clay-600 and `statusWarningFg` to amber-700 in light.
- `CardTone.Cream` now uses the new `surfaceCream` token (papaya whip / dark `#33291A`), as the
  design system's Card does; it used to fall back to the page colour.
