package dev.andrej.echo.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Transcribes speech with Android's on-device recognizer.
 *
 * Two things about [SpeechRecognizer] shape this class:
 *
 * 1. It is built for one-shot dictation — it ends the session as soon as the speaker pauses.
 *    Echo wants continuous transcription, so this class silently starts a new recognition
 *    session whenever one finishes, and keeps feeding results into the same flow. A pause is
 *    not the end of anything, and `ERROR_NO_MATCH` during silence is not a failure.
 * 2. Its methods must be called from the main thread, which is why the flow is pinned to
 *    [Dispatchers.Main].
 *
 * Only the on-device recognizer is used, so audio never leaves the phone. That API requires
 * Android 13 (API 33), which is this app's minimum.
 */
class AndroidSpeechEngine(private val context: Context) : TranscriptionEngine {

    override fun availability(): Availability = when {
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> Availability.NoRecognizer
        else -> Availability.Available
    }

    /**
     * Asks the recognizer which languages it has and which it could download.
     *
     * This is what stops the app from requesting a locale the device does not hold: a phone
     * can support en-US while only en-GB is actually installed, and asking for the missing one
     * fails with ERROR_LANGUAGE_UNAVAILABLE.
     */
    override suspend fun languageSupport(): LanguageSupport =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

                fun finish(support: LanguageSupport) {
                    recognizer.destroy()
                    if (continuation.isActive) continuation.resume(support)
                }

                recognizer.checkRecognitionSupport(
                    baseIntent(Locale.getDefault().toLanguageTag()),
                    Executors.newSingleThreadExecutor(),
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            finish(
                                LanguageSupport(
                                    installed = support.installedOnDeviceLanguages,
                                    supported = support.supportedOnDeviceLanguages,
                                ),
                            )
                        }

                        override fun onError(error: Int) {
                            Log.w(TAG, "checkRecognitionSupport failed with error $error")
                            finish(LanguageSupport(installed = emptyList(), supported = emptyList()))
                        }
                    },
                )

                continuation.invokeOnCancellation { recognizer.destroy() }
            }
        }

    override fun requestModelDownload(language: String) {
        // Must run on the main thread like every other recognizer call.
        Handler(Looper.getMainLooper()).post {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.triggerModelDownload(baseIntent(language))
            // Not destroyed immediately: the download is handed to the system, and destroying
            // the client too early can cancel it. It is released when the process ends.
        }
    }

    private fun baseIntent(language: String) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

    override fun transcribe(language: String): Flow<TranscriptionEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

        // Guards the restart loop: a session that ends because the user stopped must not be
        // restarted, and a recognizer that fails immediately must not be retried forever.
        var listening = true
        var consecutiveFailures = 0

        fun intent() = baseIntent(language).apply {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        fun restart() {
            if (!listening) return
            recognizer.cancel()
            recognizer.startListening(intent())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {

            override fun onPartialResults(results: Bundle) {
                results.firstTranscript()?.let { trySend(TranscriptionEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle) {
                consecutiveFailures = 0
                results.firstTranscript()?.let { trySend(TranscriptionEvent.Final(it)) }
                // A result means this session is over. Immediately open the next one.
                restart()
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(TranscriptionEvent.Level(rmsdB))
            }

            override fun onEndOfSpeech() {
                // The speaker paused. onResults or onError follows, which handles the restart.
            }

            override fun onError(error: Int) {
                when (error) {
                    // Silence, not a problem: keep the session alive.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> restart()

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        fail(FailureReason.MissingPermission)

                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    -> fail(FailureReason.LanguageUnavailable)

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        fail(FailureReason.RecognizerBusy)

                    // Transient client/server hiccups are common across OEMs. Retry a few
                    // times, then give up rather than spinning forever.
                    else -> {
                        consecutiveFailures++
                        Log.w(TAG, "Recognition error $error (failure $consecutiveFailures)")
                        if (consecutiveFailures <= MAX_CONSECUTIVE_FAILURES) restart() else fail(FailureReason.Unknown)
                    }
                }
            }

            private fun fail(reason: FailureReason) {
                listening = false
                trySend(TranscriptionEvent.Failed(reason))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(intent())

        awaitClose {
            listening = false
            recognizer.stopListening()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    private fun Bundle.firstTranscript(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "AndroidSpeechEngine"
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
