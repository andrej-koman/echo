package dev.andrej.echo.ui.billing

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.billing.PlanPeriod
import dev.andrej.echo.transcribe.TranscriptionStatus
import dev.andrej.echo.ui.components.*
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import java.text.DateFormat
import java.util.Date

/** A capped note being finished after Pro activates. [status] is live; the rest is captured at paywall entry. */
data class UnlockNote(
    val id: String,
    val title: String,
    val capMs: Long,
    val durationMs: Long,
    val status: TranscriptionStatus? = null,
)

/** Share of the remaining audio (past the cap) already transcribed. Whisper's fraction spans the full audio. */
internal fun unlockProgress(note: UnlockNote): Float {
    val fraction = (note.status as? TranscriptionStatus.Transcribing)?.fraction ?: return 0f
    val rest = note.durationMs - note.capMs
    if (rest <= 0) return 0f
    return ((fraction * note.durationMs - note.capMs) / rest).coerceIn(0f, 1f)
}

internal fun periodOf(activePlan: String?): PlanPeriod? = activePlan?.lowercase()?.let {
    when {
        "lifetime" in it -> PlanPeriod.Lifetime
        "year" in it || "annual" in it -> PlanPeriod.Yearly
        "month" in it -> PlanPeriod.Monthly
        else -> null
    }
}

@Composable
internal fun ProWelcome(
    unlockNote: UnlockNote?,
    activePlan: String?,
    renewsAt: Long?,
    onOpenNote: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = EchoTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.surfacePage).glow(colors.surfaceAccentSoft)
            .padding(horizontal = EchoTheme.spacing.gutterScreen).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6, Alignment.CenterVertically),
        ) {
            Mascot(size = 120.dp)
            EchoBadge("PRO", tone = BadgeTone.Info)
            Text("You're on Pro", style = EchoTheme.typography.display, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(
                if (unlockNote != null) "Every note is now transcribed in full. Echo is finishing your last note now."
                else "Every note is now transcribed in full.",
                style = EchoTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center,
            )
        }
        if (unlockNote != null) {
            val progress = unlockProgress(unlockNote)
            Column(
                Modifier.fillMaxWidth().clip(EchoTheme.radii.lg).background(colors.surfaceCard)
                    .hairline(colors.borderSubtle, cornerRadius = 18.dp).padding(EchoTheme.spacing.s6),
                verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceAccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_file_text), null, tint = colors.textAccent, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(unlockNote.title, style = EchoTheme.typography.heading, color = colors.textPrimary, maxLines = 1)
                        Text(
                            if (unlockNote.status is TranscriptionStatus.Transcribing) {
                                "Transcribing ${clock(unlockNote.capMs)} to ${clock(unlockNote.durationMs)} · ${(progress * 100).toInt()}%"
                            } else "Queued",
                            style = EchoTheme.typography.caption, color = colors.textSecondary,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(EchoTheme.radii.pill).background(colors.surfaceSunken)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.actionPrimaryBg))
                }
            }
            Spacer(Modifier.height(EchoTheme.spacing.s6))
        }
        EchoButton(
            if (unlockNote != null) "Back to my note" else "Done",
            { if (unlockNote != null) onOpenNote(unlockNote.id) else onDone() },
            fullWidth = true, size = ButtonSize.Lg,
        )
        val footer = when (periodOf(activePlan)) {
            PlanPeriod.Lifetime -> "Pro lifetime"
            PlanPeriod.Yearly -> "Pro yearly" + renewal(renewsAt)
            PlanPeriod.Monthly -> "Pro monthly" + renewal(renewsAt)
            null -> null
        }
        footer?.let {
            Text(it, style = EchoTheme.typography.caption, color = colors.textTertiary, modifier = Modifier.padding(vertical = EchoTheme.spacing.s6))
        } ?: Spacer(Modifier.height(EchoTheme.spacing.s6))
    }
}

private fun renewal(renewsAt: Long?): String =
    renewsAt?.let { " · renews ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" }.orEmpty()

/** Soft radial accent glow at the top of a screen. */
internal fun Modifier.glow(color: Color): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(color, Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
            radius = size.width * 0.9f,
        ),
    )
}
