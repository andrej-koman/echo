package dev.andrej.echo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import dev.andrej.echo.R

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

/** Durations, timers and counts only. */
val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** The design system's own scale. Material's roles are mapped separately, below. */
@Immutable
data class EchoTypography(
    val displayLg: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 47.5.sp,
        letterSpacing = (-0.022).em,
    ),
    val display: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 42.sp,
        letterSpacing = (-0.022).em,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
        letterSpacing = (-0.014).em,
    ),
    val titleSm: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        letterSpacing = (-0.014).em,
    ),
    val heading: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
        letterSpacing = (-0.006).em,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.75.sp,
        letterSpacing = (-0.006).em,
    ),
    val bodySm: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.3.sp,
        letterSpacing = (-0.006).em,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.1.sp,
        letterSpacing = (-0.006).em,
    ),
    val micro: TextStyle = TextStyle(
        fontFamily = DmSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 13.6.sp,
    ),
    val microCaps: TextStyle = TextStyle(
        fontFamily = SpaceMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 13.6.sp,
        letterSpacing = 0.10.em,
    ),
    val monoMicro: TextStyle = TextStyle(
        fontFamily = SpaceMono,
        fontSize = 11.sp,
        lineHeight = 13.6.sp,
    ),
    val monoCaption: TextStyle = TextStyle(
        fontFamily = SpaceMono,
        fontSize = 13.sp,
        lineHeight = 16.1.sp,
    ),
    val monoTimer: TextStyle = TextStyle(
        fontFamily = SpaceMono,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).em,
    ),
)

/** Only the four Material roles the app actually uses are worth overriding. */
internal fun materialTypography(echo: EchoTypography) = Typography(
    bodyLarge = echo.body,
    bodyMedium = echo.bodySm,
    labelLarge = echo.bodySm.copy(fontWeight = FontWeight.Medium),
    labelMedium = echo.caption.copy(fontWeight = FontWeight.Medium),
)
