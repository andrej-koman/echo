package dev.andrej.echo.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

val LocalEchoColors = staticCompositionLocalOf { EchoColors() }
val LocalEchoTypography = staticCompositionLocalOf { EchoTypography() }
val LocalEchoSpacing = staticCompositionLocalOf { EchoSpacing() }
val LocalEchoRadii = staticCompositionLocalOf { EchoRadii() }
val LocalEchoElevation = staticCompositionLocalOf { EchoElevation() }
val LocalEchoMotion = staticCompositionLocalOf { EchoMotion() }

/**
 * True when the user has turned animations off system-wide. The ambient loops — breathing,
 * rings, dots, shimmer — must go static; they are decoration, not feedback.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

object EchoTheme {
    val colors: EchoColors
        @Composable @ReadOnlyComposable get() = LocalEchoColors.current
    val typography: EchoTypography
        @Composable @ReadOnlyComposable get() = LocalEchoTypography.current
    val spacing: EchoSpacing
        @Composable @ReadOnlyComposable get() = LocalEchoSpacing.current
    val radii: EchoRadii
        @Composable @ReadOnlyComposable get() = LocalEchoRadii.current
    val elevation: EchoElevation
        @Composable @ReadOnlyComposable get() = LocalEchoElevation.current
    val motion: EchoMotion
        @Composable @ReadOnlyComposable get() = LocalEchoMotion.current
}

private val colors = EchoColors()

/**
 * Light only, no dynamic color: the design system defines a single warm-paper palette and has
 * no dark ramp, so a dark theme would be invented rather than implemented.
 */
private val EchoColorScheme = lightColorScheme(
    primary = colors.actionPrimaryBg,
    onPrimary = colors.actionPrimaryFg,
    primaryContainer = colors.surfaceAccentSoft,
    onPrimaryContainer = colors.textAccent,
    secondary = Sage500,
    onSecondary = Color.White,
    secondaryContainer = colors.surfaceSageSoft,
    onSecondaryContainer = colors.textSuccess,
    background = colors.surfacePage,
    onBackground = colors.textPrimary,
    surface = colors.surfacePage,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceCard,
    onSurfaceVariant = colors.textSecondary,
    outline = colors.borderStrong,
    outlineVariant = colors.borderDefault,
    error = colors.recordLive,
    onError = Color.White,
    errorContainer = colors.statusDangerBg,
    onErrorContainer = colors.statusDangerFg,
    scrim = colors.surfaceScrim,
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember { EchoTypography() }

    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val activity = context as? Activity
    if (activity != null) {
        SideEffect {
            // The screens paint their own warm background edge to edge, so the system bars
            // need dark icons over it.
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(
        LocalEchoColors provides colors,
        LocalEchoTypography provides typography,
        LocalEchoSpacing provides EchoSpacing(),
        LocalEchoRadii provides EchoRadii(),
        LocalEchoElevation provides EchoElevation(),
        LocalEchoMotion provides EchoMotion(),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = EchoColorScheme,
            typography = materialTypography(typography),
            content = content,
        )
    }
}
