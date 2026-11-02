package dev.andrej.echo.ui.detail

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlinx.coroutines.delay

class NotePlayer(context: Context, file: File) {

    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                sync()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration != C.TIME_UNSET) durationMs = player.duration
                if (state == Player.STATE_ENDED) {
                    player.pause()
                    seekTo(0)
                }
            }
        })
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }

    fun toggle() = if (player.isPlaying) player.pause() else player.play()

    fun pause() = player.pause()

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    fun skip(deltaMs: Long) = seekTo((player.currentPosition + deltaMs).coerceIn(0, durationMs.coerceAtLeast(0)))

    fun cycleSpeed() {
        speed = nextSpeed(speed)
        player.setPlaybackSpeed(speed)
    }

    fun playFrom(ms: Long) {
        seekTo(ms)
        player.play()
    }

    internal fun sync() {
        positionMs = player.currentPosition
    }

    internal fun release() = player.release()
}

private val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

internal fun nextSpeed(current: Float): Float = speeds[(speeds.indexOf(current) + 1).mod(speeds.size)]

/** Null without a [file]. Released with the screen, and paused when the app leaves the foreground. */
@Composable
fun rememberNotePlayer(file: File?): NotePlayer? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(file) { file?.let { NotePlayer(context, it) } }

    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player?.release()
        }
    }
    LaunchedEffect(player, player?.playing) {
        while (player?.playing == true) {
            player.sync()
            delay(100)
        }
    }
    return player
}
