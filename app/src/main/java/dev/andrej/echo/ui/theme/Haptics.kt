package dev.andrej.echo.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** The in-app Haptics switch. The system haptic setting applies on top of it. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** When the celebration's mascot lands; the haptic's thud and the first ring share this moment. */
const val CelebrateLandMs = 180

/** Gap between the first and second ring, and between the thud and the closing tick. */
const val CelebrateRingGapMs = 140

/** How [EchoHaptics.celebrate] plays on this phone, from best to plainest. */
internal enum class CelebrationPlan { Primitives, Waveform, Predefined }

internal fun celebrationPlan(sdk: Int, primitivesSupported: Boolean, amplitudeControl: Boolean): CelebrationPlan = when {
    sdk >= Build.VERSION_CODES.R && primitivesSupported -> CelebrationPlan.Primitives
    amplitudeControl -> CelebrationPlan.Waveform
    else -> CelebrationPlan.Predefined
}

/**
 * The app's haptic vocabulary. View-based calls go through [View.performHapticFeedback], which
 * honours the system haptic setting and the device's own tuning of each constant. Vibrator-based
 * calls ([success], [celebrate]) check that setting themselves.
 */
@Immutable
class EchoHaptics(
    private val view: View,
    private val context: Context,
    private val enabled: Boolean,
) {
    /** Any button, tab or control tap: the lightest tick Android exposes. */
    fun tick() = perform(HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    /** Capture opens, or a long-press takes hold: a heavy click, like a walkie-talkie key going down. */
    fun engage() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Capture closes: a crisp release, audio in, processing started. */
    fun release() = perform(HapticFeedbackConstants.KEYBOARD_RELEASE)

    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            on -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.KEYBOARD_RELEASE
        },
    )

    /** A drag crosses the point where letting go acts, e.g. dismissing a sheet. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CONTEXT_CLICK,
    )

    /** The drag comes back before the threshold. */
    fun thresholdBack() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
        else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Something got done, such as completing a task. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    fun scrubTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun scrubBound() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** The take is filed away: a double pulse you can feel without looking. */
    fun success() {
        if (!vibratorAllowed()) return
        vibrator()?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }

    /**
     * Pro just activated: a rise, a thud as the mascot lands, a tick with the second ring.
     * Returns when that tick plays, in ms from now, so the second ring can follow it.
     */
    fun celebrate(): Int {
        val planned = CelebrateLandMs + CelebrateRingGapMs
        if (!vibratorAllowed()) return planned
        val vibrator = vibrator() ?: return planned
        val primitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        )
        when (celebrationPlan(Build.VERSION.SDK_INT, primitives, vibrator.hasAmplitudeControl())) {
            CelebrationPlan.Primitives -> {
                // A composition delay counts from the end of the previous primitive, and a thud
                // can run 300 ms, so both delays are worked out from the phone's own durations.
                val (riseMs, thudMs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibrator.getPrimitiveDurations(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_THUD,
                    ).let { it[0] to it[1] }
                } else 150 to 300
                val thudAt = maxOf(CelebrateLandMs, riseMs)
                val tickAt = maxOf(thudAt + CelebrateRingGapMs, thudAt + thudMs)
                vibrator.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, thudAt - riseMs)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, tickAt - thudAt - thudMs)
                        .compose(),
                )
                return tickAt
            }
            // Many Samsung phones lack the rise and thud primitives but do have amplitude control.
            CelebrationPlan.Waveform -> vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(30, 30, 30, 30, 30, 30, 40, CelebrateRingGapMs - 40L, 25),
                    intArrayOf(30, 55, 85, 115, 150, 185, 255, 0, 90),
                    -1,
                ),
            )
            CelebrationPlan.Predefined -> vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        }
        return planned
    }

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    private fun vibratorAllowed(): Boolean = enabled &&
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1

    private fun vibrator(): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }
}

@Composable
fun rememberEchoHaptics(): EchoHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, context, enabled) { EchoHaptics(view, context.applicationContext, enabled) }
}

internal enum class ScrubFeedback { None, Tick, Bound }

/** A bound on arriving at the start or end, else a tick per ten-second mark crossed. */
internal fun scrubFeedback(fromMs: Long, toMs: Long, durationMs: Long): ScrubFeedback {
    fun atBound(ms: Long) = ms <= 0 || ms >= durationMs
    return when {
        atBound(toMs) -> if (atBound(fromMs) && (fromMs <= 0) == (toMs <= 0)) ScrubFeedback.None else ScrubFeedback.Bound
        fromMs / 10_000 != toMs / 10_000 -> ScrubFeedback.Tick
        else -> ScrubFeedback.None
    }
}
