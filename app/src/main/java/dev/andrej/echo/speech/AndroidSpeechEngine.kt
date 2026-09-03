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
 * Only the on-device recognizer is used, so audio never leaves the phone. Its calls must all
 * happen on the main thread, hence the flowOn and Handler below.
 */
class AndroidSpeechEngine(private val context: Context) : TranscriptionEngine {

    override fun availability(): Availability = when {
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> Availability.NoRecognizer
        else -> Availability.Available
    }

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
        Handler(Looper.getMainLooper()).post {
            // Not destroyed: destroying the client can cancel the download it just handed off.
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer.triggerModelDownload(baseIntent(language))
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

        var listening = true
        var consecutiveFailures = 0

        fun intent() = baseIntent(language).apply {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        fun restart() {
            if (!listening) return
            trySend(TranscriptionEvent.Level(SILENCE_DB))
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
                restart()
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(TranscriptionEvent.Level(rmsdB))
                Log.d(TAG, "RMS $rmsdB")
            }

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                when (error) {
                    // Silence, not a problem.
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
        const val SILENCE_DB = -2f
    }
}
