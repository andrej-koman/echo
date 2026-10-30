# Add project-specific ProGuard rules here.

# Called by name from whisper_jni.cpp.
-keep class dev.andrej.echo.speech.whisper.WhisperNative { *; }
-keep interface dev.andrej.echo.speech.whisper.WhisperProgress { *; }
-keep class * implements dev.andrej.echo.speech.whisper.WhisperProgress { *; }
