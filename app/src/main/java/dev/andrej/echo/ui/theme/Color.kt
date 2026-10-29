package dev.andrej.echo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Base ramps, transcribed from the Echo design system's tokens.css. They keep their light values
// in both themes; only the semantic layer below changes.

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
val Amber700 = Color(0xFF7D5C10)

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
    val surfaceCream: Color = Paper200,
    val surfaceScrim: Color = AlphaInk40,
    val surfaceBar: Color = Paper050,
    val aura: Color = Paper200,

    val textPrimary: Color = Ink900,
    val textSecondary: Color = Ink600,
    val textTertiary: Color = Ink500,
    val textInverse: Color = Paper050,
    val textAccent: Color = Bronze700,
    val textOnAccent: Color = Ink900,
    val textDanger: Color = Clay600,
    val textSuccess: Color = Sage700,

    val borderSubtle: Color = AlphaInk06,
    val borderDefault: Color = AlphaInk10,
    val borderStrong: Color = Ink200,
    val borderAccent: Color = Bronze400,

    val actionPrimaryBg: Color = Bronze400,
    val actionPrimaryFg: Color = Ink900,
    val actionSecondaryBg: Color = Paper000,
    val actionSecondaryFg: Color = Ink800,
    val actionInverseBg: Color = Ink900,
    val actionDisabledBg: Color = Ink100,
    val actionDisabledFg: Color = Ink400,

    val recordIdle: Color = Bronze400,
    val recordLive: Color = Clay600,
    /** Mic and stop glyphs on the record gradients: dark ink in both themes, like labels on bronze. */
    val recordGlyph: Color = Ink900,
    val recordLiveSoft: Color = Clay100,
    val waveformActive: Color = Bronze500,
    val waveformIdle: Color = Ink400,
    val indicatorIdle: Color = Ink400,
    val controlTrack: Color = Ink400,
    val transcriptHighlight: Color = Sage200,
    val skeleton: Color = Ink100,

    val statusSuccessBg: Color = Sage100,
    val statusSuccessFg: Color = Sage700,
    val statusWarningBg: Color = Amber100,
    val statusWarningFg: Color = Amber700,
    val statusDangerBg: Color = Clay100,
    val statusDangerFg: Color = Clay600,
    val statusNeutralBg: Color = Ink50,
    val statusNeutralFg: Color = Ink600,
    val statusInfoBg: Color = Paper200,
    val statusInfoFg: Color = Bronze700,
)

/**
 * Dark stays warm: deep browns, never charcoal, and surfaces get lighter as they come forward.
 * The design's top/tab bar is 80% page over a blur; Android has no backdrop blur, so it is opaque.
 */
val DarkEchoColors = EchoColors(
    surfacePage = Color(0xFF1C1810),
    surfaceCard = Color(0xFF241F16),
    surfaceRaised = Color(0xFF2D271C),
    surfaceSunken = Color(0xFF16120B),
    surfaceSheet = Color(0xFF2D271C),
    surfaceInverse = Color(0xFFF3ECD9),
    surfaceAccentSoft = Color(0xFF3A2C1D),
    surfaceSageSoft = Color(0xFF2F3322),
    surfaceCream = Color(0xFF33291A),
    surfaceScrim = Color(0x99080603),
    surfaceBar = Color(0xFF1C1810),
    aura = Color(0x24D4A373),

    textPrimary = Color(0xFFF3ECD9),
    textSecondary = Color(0xFFC9BEA6),
    textTertiary = Color(0xFFA3967F),
    textInverse = Ink900,
    textAccent = Bronze300,
    textDanger = Clay300,
    textSuccess = Sage400,

    borderSubtle = Color(0x0FFFF5DC),
    borderDefault = Color(0x1AFFF5DC),
    borderStrong = Ink700,

    actionSecondaryBg = Color(0xFF2D271C),
    actionSecondaryFg = Color(0xFFF3ECD9),
    actionInverseBg = Color(0xFFF3ECD9),
    actionDisabledBg = Color(0xFF2D271C),
    actionDisabledFg = Ink500,

    recordLive = Clay400,
    recordLiveSoft = Color(0xFF3D2219),
    waveformActive = Bronze400,
    waveformIdle = Color(0xFF8A7C66),
    indicatorIdle = Ink500,
    controlTrack = Color(0xFF8A7C66),
    transcriptHighlight = Color(0xFF3D4328),
    skeleton = Ink800,

    statusSuccessBg = Color(0xFF2F3322),
    statusSuccessFg = Sage400,
    statusWarningBg = Color(0xFF3A2E12),
    statusWarningFg = Amber400,
    statusDangerBg = Color(0xFF3D2219),
    statusDangerFg = Clay300,
    statusNeutralBg = Color(0xFF2D271C),
    statusNeutralFg = Color(0xFFC9BEA6),
    statusInfoBg = Color(0xFF33291A),
    statusInfoFg = Bronze300,
)
