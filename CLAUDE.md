# Echo agent guide

Echo is an offline-first Android voice-notes app. Kotlin, Compose, minSdk 29. It records or imports audio, transcribes on-device with Whisper, identifies speakers, and extracts a note and tasks with a local LLM.

## Working rules

- Never commit or push. Andrej reviews all code. Stage files only when asked.
- Build only what was requested. Avoid speculative abstractions and unrelated cleanup.
- Keep comments scarce: explain non-obvious platform behavior or a surprising decision, not the code itself.
- Update the relevant project guidance when work changes a lasting decision or reveals a reusable device or dependency constraint. Record the reason, not a session log.
- Preserve existing user changes. Inspect the worktree and staged diff before editing a file.

## Product and architecture

- The target is `Echo — Feature Spec (v1).pdf`. The rebuild is phased; implement the requested phase or feature only. Notes is the start tab; tasks live inside notes. The app has no account, sync, reminders, or vendor-only AI dependency.
- `AppContainer` provides manual dependency injection. `data/` uses SQLDelight and repository interfaces; ViewModels do not access SQL directly. FTS uses FTS4 for platform SQLite compatibility. Migrations are numbered `.sqm` files beside the schema.
- `RecordingController` writes 16 kHz PCM16 to `recordings/<id>/audio.pcm`. `Transcriptions` handles imports and recordings in unique WorkManager jobs: decode, Whisper, speaker assignment, audio finalization, then analysis. Analysis has its own persistent queue. Preserve restart recovery and partial transcript behavior when editing this pipeline.
- Whisper runs through `whisper.cpp` JNI; speaker detection uses sherpa-onnx's C API. The LLM uses LiteRT-LM with a downloaded Qwen3 `.litertlm` model. `ModelSlot` prevents Whisper and the LLM from residing in memory together.
- UI uses `EchoTheme` semantic tokens and components from `ui/theme/` and `ui/components/`. It follows system light/dark mode; ambient motion respects `LocalReducedMotion`. Screens use hard navigation cuts. Use the design artifact links in the detailed notes when visual work needs them.

## Where to read more

Read the matching section of [implementation notes](docs/implementation-notes.md) before changing a subsystem:

- Recording, import, transcription, model selection, or recovery: **Recording** and **Import and Whisper**.
- Diarization: **Speakers**. Database or search: **Storage**. Build and device checks: **Build**.
- Navigation, notes, tasks, playback, export, or settings: **Layout** and **Notes**.
- Theme or components: **Design system**. Notifications: **Notifications**.
- Analysis, prompts, model downloads, or memory: **On-device AI**. First-run flow: **Onboarding**.
- Product scope and phase history: **Rebuild in progress (v1 spec)** and the root feature spec.

## Checks

Run the smallest relevant check, then broader checks when the change warrants them. `./gradlew test` runs unit tests; `./gradlew assembleDebug` builds the app; `./gradlew installAndRun` installs and launches on a connected phone. Follow repository command guidance in `AGENTS.md` when using a shell.
