package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline

/** Shown under a free note whose transcript stops at the cap. */
@Composable
internal fun FreeCapCard(transcribedUntilMs: Long, durationMs: Long, onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    val minutes = ((durationMs - transcribedUntilMs).coerceAtLeast(0) + 59_999) / 60_000
    val fade = colors.surfacePage
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Zero-height strip that paints a fade over the last transcript lines above it.
        Spacer(
            Modifier.fillMaxWidth().height(1.dp).drawBehind {
                val h = 60.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, fade), startY = -h, endY = 0f),
                    topLeft = Offset(0f, -h),
                    size = Size(size.width, h),
                )
            },
        )
        Text(
            "${clock(transcribedUntilMs)} · end of free transcript",
            style = EchoTheme.typography.micro,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = EchoTheme.spacing.s5),
        )
        Column(
            Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen).fillMaxWidth()
                .clip(EchoTheme.radii.lg).background(colors.surfaceAccentSoft)
                .hairline(colors.borderSubtle, cornerRadius = 18.dp)
                .padding(EchoTheme.spacing.s6),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, tint = colors.textAccent, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        if (minutes == 1L) "1 more minute to transcribe" else "$minutes more minutes to transcribe",
                        style = EchoTheme.typography.heading, color = colors.textPrimary,
                    )
                    Text("Your full recording is saved", style = EchoTheme.typography.caption, color = colors.textSecondary)
                }
            }
            Text(
                "Free notes are transcribed up to 2 minutes. With Pro, Echo finishes this one on your phone and redoes the summary and tasks from all of it.",
                style = EchoTheme.typography.bodySm, color = colors.textSecondary,
            )
            EchoButton("Transcribe the rest with Pro", onUpgrade, fullWidth = true, size = ButtonSize.Lg)
        }
        Spacer(Modifier.height(EchoTheme.spacing.s6))
    }
}
