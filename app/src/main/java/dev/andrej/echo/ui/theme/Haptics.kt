package dev.andrej.echo.ui.theme

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The app's four haptics. Everything but [success] goes through the View, which already honours
 * the system haptic setting and the device's own tuning of each constant.
 */
@Immutable
class EchoHaptics(
    private val view: View,
    private val context: Context,
) {
    /** Any button, tab or control tap: the lightest tick Android exposes. */
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    /** Capture opens: a heavy click, like a walkie-talkie key going down. */
    fun engage() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** Capture closes: a crisp release, audio in, processing started. */
    fun release() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)

    /** The take is filed away: a double pulse you can feel without looking. */
    fun success() {
        if (!hapticsEnabled()) return
        vibrator()?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }

    private fun hapticsEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1

    private fun vibrator(): Vibrator? =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
}

@Composable
fun rememberEchoHaptics(): EchoHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { EchoHaptics(view, context.applicationContext) }
}
