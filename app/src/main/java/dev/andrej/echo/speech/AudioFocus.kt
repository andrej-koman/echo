package dev.andrej.echo.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

interface Interruptions {
    fun start(onInterrupted: () -> Unit, onOver: () -> Unit)
    fun stop()
}

/**
 * Holds transient audio focus for the length of a take: music pauses instead of bleeding into the
 * recording, and a phone call, which takes focus from us, is reported as an interruption.
 * A permanent loss (another app starts playing) is ignored — the user chose that, not the phone.
 */
class AudioFocus(context: Context) : Interruptions {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun start(onInterrupted: () -> Unit, onOver: () -> Unit) {
        stop()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onInterrupted()
                    AudioManager.AUDIOFOCUS_GAIN -> onOver()
                }
            }
            .build()
        audioManager.requestAudioFocus(request)
        this.request = request
    }

    override fun stop() {
        request?.let(audioManager::abandonAudioFocusRequest)
        request = null
    }
}
