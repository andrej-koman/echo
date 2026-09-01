# Echo — Live Audio Transcription

**Date:** 2026-09-01
**Status:** Implemented (v1)

## Problem

A personal Android app for talking into the phone and getting text back, live — words appearing
while speaking, not after — with finished transcripts saved to a browsable history.

## Requirements

Stated priorities, in order of how much they constrained the design:

1. Works offline — no network dependency.
2. Audio never leaves the phone.
3. Best accuracy achievable under (1) and (2).
4. Zero running cost.
5. Live feedback: partial results while speaking.
6. Transcripts saved and browsable. Audio is not kept.
7. English and Slovenian, user-selectable.

Priorities 1, 2 and 4 rule out every cloud API. Between the remaining on-device options,
Android's built-in recognizer was chosen for v1 over Whisper: it needs no NDK work, no model
download, and streams partial results natively. Whisper remains the accuracy upgrade path.

## Design

### Engine behind an interface

`TranscriptionEngine` exposes `availability()` and `transcribe(language): Flow<TranscriptionEvent>`,
with events `Partial`, `Final`, `Level` and `Failed`. Nothing above this interface knows which
recognizer is running, so a Whisper implementation can be added without touching UI or storage.

`AndroidSpeechEngine` implements it over `SpeechRecognizer.createOnDeviceSpeechRecognizer()`.
Using only the on-device recognizer is what makes "audio never leaves the phone" a guarantee
rather than a hope; it requires API 33, which is why `minSdk` was raised from 26 to 33.

**Continuous transcription.** `SpeechRecognizer` is designed for one-shot dictation and ends its
session when the speaker pauses. The engine therefore opens a fresh recognition session on
`onResults`, and on `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` — silence is not a failure. Other
errors are retried up to three times before surfacing as `Failed`, so a broken recognizer cannot
spin forever. All of this is confined to one class.

### State

`RecordViewModel` accumulates `Final` events into `committedText` and keeps the latest `Partial`
in `partialText`; the screen renders both concatenated, so words appear immediately and firm up
as the recognizer settles. On stop, the combined text is trimmed and saved unless blank.

### Storage

`TranscriptRepository` with a JSON-file implementation writing `filesDir/transcripts.json`
via kotlinx.serialization, exposing a `StateFlow` so the history list updates itself.

Room was the obvious choice and was rejected: the latest KSP release is 2.3.11 while the project
compiles with Kotlin 2.4.10, so Room's annotation processor cannot run. The repository interface
keeps that decision reversible. Writes go to a temp file and are renamed, so a crash mid-write
cannot corrupt the store, and an unreadable file degrades to an empty list rather than a crash.

Language preference lives in `SharedPreferences` — one string, no reason to add DataStore.

### Dependency wiring

`AppContainer`, built lazily by `EchoApplication`, constructs the engine, repository and settings
store and provides a `ViewModelProvider.Factory`. Hand-written: at three screens, a graph you can
read top to bottom beats code generation.

## Error handling

| Condition | Behavior |
|---|---|
| Mic permission not granted | Requested on first record tap; denial leaves the button usable for a retry |
| No on-device recognizer | Record button replaced by instructions pointing at system speech settings |
| Language model missing | Message suggesting the other language or a system-settings install |
| `ERROR_NO_MATCH` / timeout mid-session | Not an error — session restarts, transcription continues |
| Repeated unknown recognizer errors | Retried 3×, then surfaced as a dismissible error |
| Corrupt transcript file | Logged, treated as empty history |

## Testing

`FakeTranscriptionEngine` replays scripted events, which makes every layer above the engine
testable with no microphone and no device:

- `JsonTranscriptRepositoryTest` — save, newest-first ordering, delete, persistence across
  instances, corrupt-file recovery.
- `RecordViewModelTest` — partial replacement, final accumulation, pending partial kept on stop,
  blank transcript not saved, state reset, failure handling, post-failure events ignored,
  level exposure, language persistence, double-start guard.
- `RecordScreenTest` (instrumented) — prompt, live text, language chips, missing-recognizer state.

The disk dispatcher is injected into the repository so tests run writes on the test scheduler.
This was found by a failing test, not by inspection: with a hardcoded `Dispatchers.IO`,
`advanceUntilIdle()` returned before the save landed.

`AndroidSpeechEngine` itself is verified by hand on a device — its behavior is the OS recognizer's.

## Known limitations

- Device variance in `SpeechRecognizer` behavior; the restart loop may need tuning per device.
- Slovenian on-device models are not available on every device.
- No audio retained, so transcripts cannot be re-transcribed by a future engine.
- Recording stops when the app is backgrounded — no foreground service in v1.
