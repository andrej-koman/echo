package dev.andrej.echo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EchoSpacing(
    val s1: Dp = 2.dp,
    val s2: Dp = 4.dp,
    val s3: Dp = 6.dp,
    val s4: Dp = 8.dp,
    val s5: Dp = 12.dp,
    val s6: Dp = 16.dp,
    val s7: Dp = 20.dp,
    val s8: Dp = 24.dp,
    val s9: Dp = 32.dp,
    val s10: Dp = 40.dp,
    val s11: Dp = 56.dp,
    val s12: Dp = 72.dp,

    val gutterScreen: Dp = 20.dp,
    val gapList: Dp = 10.dp,
    val gapSection: Dp = 28.dp,
    val tapMin: Dp = 44.dp,
)
