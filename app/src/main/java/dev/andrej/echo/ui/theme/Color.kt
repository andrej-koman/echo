package dev.andrej.echo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Base ramps, transcribed from the OutLoud design system's tokens/colors.css.

val Bronze50 = Color(0xFFFBF3EA)
val Bronze100 = Color(0xFFF6E7D7)
val Bronze200 = Color(0xFFECD3BA)
val Bronze300 = Color(0xFFE0BB96)
val Bronze400 = Color(0xFFD4A373)
val Bronze500 = Color(0xFFBD8A58)
val Bronze600 = Color(0xFF9C6F43)
val Bronze700 = Color(0xFF7A5533)
val Bronze800 = Color(0xFF573C24)

val Sage50 = Color(0xFFF5F8EA)
val Sage100 = Color(0xFFEEF2E0)
val Sage200 = Color(0xFFE9EDC9)
val Sage300 = Color(0xFFCCD5AE)
val Sage400 = Color(0xFFB3C08E)
val Sage500 = Color(0xFF94A56D)
val Sage600 = Color(0xFF718050)
val Sage700 = Color(0xFF55613B)

val Ink900 = Color(0xFF2A2318)
val Ink800 = Color(0xFF3A3125)
val Ink700 = Color(0xFF4E4335)
val Ink600 = Color(0xFF635747)
val Ink500 = Color(0xFF7A6D5A)
val Ink400 = Color(0xFF9A8F7D)
val Ink300 = Color(0xFFBDB4A2)
val Ink200 = Color(0xFFD9D2C2)
val Ink100 = Color(0xFFEBE6D8)
val Ink50 = Color(0xFFF5F2E8)

val Paper000 = Color(0xFFFFFFFF)
val Paper050 = Color(0xFFFFFDF5)
val Paper100 = Color(0xFFFEFAE0)
val Paper200 = Color(0xFFFAEDCD)

val Clay100 = Color(0xFFF8E2D9)
val Clay300 = Color(0xFFE5A48A)
val Clay400 = Color(0xFFD4795A)
val Clay500 = Color(0xFFC0603F)
val Clay600 = Color(0xFF9E4B2E)

val Amber100 = Color(0xFFFBEECB)
val Amber400 = Color(0xFFDFAE4A)
val Amber600 = Color(0xFFA87C19)

// Shadows and scrims are tinted with ink, never black — black reads grey against cornsilk.
val AlphaInk04 = Color(0x0A3A3125)
val AlphaInk06 = Color(0x0F3A3125)
val AlphaInk10 = Color(0x1A3A3125)
val AlphaInk16 = Color(0x293A3125)
val AlphaInk40 = Color(0x662A2318)
val AlphaPaper80 = Color(0xCCFFFDF5)

/** The semantic layer. Screens and components read these, not the ramps above. */
@Immutable
data class EchoColors(
    val surfacePage: Color = Paper100,
    val surfaceCard: Color = Paper050,
    val surfaceRaised: Color = Paper000,
    val surfaceSunken: Color = Ink50,
    val surfaceSheet: Color = Paper000,
    val surfaceInverse: Color = Ink900,
    val surfaceAccentSoft: Color = Bronze100,
    val surfaceSageSoft: Color = Sage100,
    val surfaceScrim: Color = AlphaInk40,
    val surfaceBar: Color = AlphaPaper80,

    val textPrimary: Color = Ink900,
    val textSecondary: Color = Ink600,
    val textTertiary: Color = Ink400,
    val textInverse: Color = Paper050,
    val textAccent: Color = Bronze700,
    val textOnAccent: Color = Color.White,
    val textDanger: Color = Clay600,
    val textSuccess: Color = Sage700,

    val borderSubtle: Color = AlphaInk06,
    val borderDefault: Color = AlphaInk10,
    val borderStrong: Color = Ink200,
    val borderAccent: Color = Bronze400,

    val actionPrimaryBg: Color = Bronze400,
    val actionPrimaryFg: Color = Color.White,
    val actionSecondaryBg: Color = Paper000,
    val actionSecondaryFg: Color = Ink800,
    val actionInverseBg: Color = Ink900,
    val actionDisabledBg: Color = Ink100,
    val actionDisabledFg: Color = Ink400,

    val recordIdle: Color = Bronze400,
    val recordLive: Color = Clay500,
    val recordLiveSoft: Color = Clay100,
    val waveformActive: Color = Bronze500,
    val waveformIdle: Color = Ink200,
    val transcriptHighlight: Color = Sage200,

    val statusSuccessBg: Color = Sage100,
    val statusSuccessFg: Color = Sage700,
    val statusWarningBg: Color = Amber100,
    val statusWarningFg: Color = Amber600,
    val statusDangerBg: Color = Clay100,
    val statusDangerFg: Color = Clay600,
    val statusNeutralBg: Color = Ink50,
    val statusNeutralFg: Color = Ink600,
    val statusInfoBg: Color = Paper200,
    val statusInfoFg: Color = Bronze700,
)
