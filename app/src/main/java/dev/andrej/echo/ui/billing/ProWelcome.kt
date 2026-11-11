package dev.andrej.echo.ui.billing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.andrej.echo.ui.theme.CelebrateLandMs
import dev.andrej.echo.ui.theme.LocalReducedMotion
import dev.andrej.echo.ui.theme.hairline
import dev.andrej.echo.ui.theme.rememberEchoHaptics
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    arrival: ProArrival? = null,
    pending: Boolean = false,
    onArrivalSeen: () -> Unit = {},
) {
    val colors = EchoTheme.colors
    val celebration = rememberCelebration(arrival, pending, onArrivalSeen)
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
            CelebrationMascot(celebration)
            EchoBadge("PRO", tone = BadgeTone.Info, modifier = Modifier.graphicsLayer { alpha = celebration.content.value })
            Text(
                "You're on Pro", style = EchoTheme.typography.display, color = colors.textPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = celebration.content.value },
            )
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

private class Celebration(hidden: Boolean) {
    val mascot = Animatable(if (hidden) 0f else 1f)
    val content = Animatable(if (hidden) 0f else 1f)
    val rings = List(2) { Animatable(1f) }
}

/**
 * While a purchase is [pending], Pro can activate a frame before [arrival] does, so the mascot
 * starts hidden then and waits for it instead of flashing in at full size.
 */
@Composable
private fun rememberCelebration(arrival: ProArrival?, pending: Boolean, onArrivalSeen: () -> Unit): Celebration {
    val haptics = rememberEchoHaptics()
    val reducedMotion = LocalReducedMotion.current
    val celebration = remember { Celebration(hidden = pending || arrival == ProArrival.Purchased) }
    LaunchedEffect(arrival) {
        when (arrival) {
            ProArrival.Purchased -> {
                val secondBeatMs = haptics.celebrate()
                if (reducedMotion) {
                    celebration.mascot.snapTo(1f)
                    celebration.content.snapTo(1f)
                } else coroutineScope {
                    launch {
                        celebration.mascot.animateTo(1f, keyframes {
                            durationMillis = CelebrateLandMs + 180
                            0f at 0
                            1.2f at CelebrateLandMs using FastOutSlowInEasing
                        })
                    }
                    celebration.rings.forEachIndexed { i, ring ->
                        launch {
                            delay(if (i == 0) CelebrateLandMs.toLong() else secondBeatMs.toLong())
                            ring.snapTo(0f)
                            ring.animateTo(1f, tween(720, easing = LinearOutSlowInEasing))
                        }
                    }
                    launch {
                        delay(CelebrateLandMs.toLong())
                        celebration.content.animateTo(1f, tween(260))
                    }
                }
                onArrivalSeen()
            }
            ProArrival.Restored -> {
                haptics.success()
                celebration.mascot.snapTo(1f)
                celebration.content.snapTo(1f)
                onArrivalSeen()
            }
            null -> Unit
        }
    }
    LaunchedEffect(pending, arrival) {
        // A purchase that ended without an arrival, e.g. Pro activating some other way.
        if (!pending && arrival == null && celebration.mascot.value == 0f) {
            celebration.mascot.snapTo(1f)
            celebration.content.snapTo(1f)
        }
    }
    return celebration
}

@Composable
private fun CelebrationMascot(celebration: Celebration) {
    val ring = EchoTheme.colors.textAccent
    Box(
        Modifier.size(120.dp).drawBehind {
            val base = size.minDimension / 2f
            celebration.rings.forEach { anim ->
                val p = anim.value
                if (p < 1f) {
                    drawCircle(ring.copy(alpha = 0.35f * (1f - p)), radius = base * (0.9f + 0.9f * p), style = Stroke(2.dp.toPx()))
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Mascot(
            size = 120.dp,
            modifier = Modifier.graphicsLayer {
                val m = celebration.mascot.value
                val scale = 0.6f + 0.4f * m
                scaleX = scale
                scaleY = scale
                alpha = m.coerceIn(0f, 1f)
            },
        )
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
