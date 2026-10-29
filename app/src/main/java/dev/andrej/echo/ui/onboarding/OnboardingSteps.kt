package dev.andrej.echo.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow

@Composable
internal fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection, Alignment.CenterVertically),
        ) {
            val aura = EchoTheme.colors.aura
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        // The design's glow is a 300dp disc; drawn past this box's bounds so its
                        // empty fade does not push the headline down. CSS radial-gradient sizes to
                        // the farthest corner, hence √2 × the disc's half-width.
                        val radius = 150.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(
                                0f to aura,
                                0.28f to aura,
                                0.7f to aura.copy(alpha = 0f),
                                center = center,
                                radius = radius * 1.414f,
                            ),
                            radius = radius * 1.414f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The design's image has transparent margin around the ghost; this raster is
                // tighter, so a smaller size draws the same visible ghost.
                Mascot(size = 120.dp, mood = MascotMood.Breathing, contentDescription = "Echo, the ghost mascot")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Say it. Echo\nsorts the rest.",
                    style = EchoTheme.typography.display,
                    color = EchoTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Talk naturally and get a transcript, notes and todos, all on your phone.",
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Row(
                modifier = Modifier
                    .clip(EchoTheme.radii.pill)
                    .background(EchoTheme.colors.statusSuccessBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_off),
                    contentDescription = null,
                    tint = EchoTheme.colors.statusSuccessFg,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Works fully offline",
                    style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.statusSuccessFg,
                )
            }
        }

        EchoButton(
            text = "Get started",
            onClick = onGetStarted,
            size = ButtonSize.Lg,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ColumnScope.VoiceToTasksStep(onContinue: () -> Unit) {
    StepIntro(
        title = "Speak once, get everything",
        body = "Echo writes down what you say, then pulls out the todos, reminders and plans inside it.",
    )

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        EchoCard(tone = CardTone.Raised, padding = CardPadding.Md, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_audio_waveform),
                        contentDescription = null,
                        tint = EchoTheme.colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "You said",
                        style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "0:14",
                        style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textSecondary,
                    )
                }
                DemoTranscript()
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkles),
                contentDescription = null,
                tint = EchoTheme.colors.textSuccess,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "3 items added",
                style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.textSuccess,
            )
        }

        EchoCard(tone = CardTone.Paper, padding = CardPadding.Sm, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
                DemoItemRow(R.drawable.ic_bell, "Call John about the client project", "9:00 AM tomorrow")
                DemoItemRow(R.drawable.ic_list_checks, "Send notes before the FDC meeting", null)
                DemoItemRow(R.drawable.ic_calendar, "Meeting with FDC", "Friday, 1:00 PM")
            }
        }
    }

    PrimaryContinue(onContinue)
}

private val DemoSegments = listOf(
    "Remind me to " to false,
    "call John about client project at nine tomorrow" to true,
    ". Oh, and I need to " to false,
    "send my notes" to true,
    " before the " to false,
    "FDC meeting on Friday at one" to true,
    "." to false,
)

@Composable
private fun DemoTranscript() {
    val highlight = SpanStyle(
        color = EchoTheme.colors.textPrimary,
        fontWeight = FontWeight.Medium,
        background = EchoTheme.colors.transcriptHighlight,
    )
    Text(
        text = buildAnnotatedString {
            DemoSegments.forEach { (text, emphasis) ->
                if (emphasis) withStyle(highlight) { append(text) } else append(text)
            }
        },
        style = EchoTheme.typography.body.copy(lineHeight = 24.3.sp),
        color = EchoTheme.colors.textSecondary,
    )
}

/** Illustration only: the design's per-kind icons, which real [dev.andrej.echo.data.TodoItem] rows do not have. */
@Composable
private fun DemoItemRow(@DrawableRes icon: Int, label: String, meta: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(EchoTheme.radii.sm)
                .background(EchoTheme.colors.surfaceSageSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = EchoTheme.colors.textSuccess,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
            Text(
                text = label,
                style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.textPrimary,
            )
            if (meta != null) {
                Text(text = meta, style = EchoTheme.typography.micro, color = EchoTheme.colors.textTertiary)
            }
        }
    }
}

@Composable
internal fun ColumnScope.PrivacyStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s9, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .echoShadow(EchoTheme.elevation.haloSage, cornerRadius = 56.dp)
                    .echoShadow(EchoTheme.elevation.shadow2, cornerRadius = 56.dp)
                    .clip(CircleShape)
                    .background(EchoTheme.colors.surfaceSageSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = EchoTheme.colors.statusSuccessFg,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        CenteredIntro(
            title = "Everything stays\non your phone",
            body = "No account, no upload, no internet needed. What you say is yours.",
        )

        EchoCard(tone = CardTone.Paper, padding = CardPadding.Md, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6)) {
                PrivacyRow(R.drawable.ic_cpu, "Transcribed on this device")
                PrivacyRow(R.drawable.ic_plane, "Works in airplane mode")
                PrivacyRow(R.drawable.ic_lock, "Recordings never leave your phone")
            }
        }
    }

    PrimaryContinue(onContinue)
}

@Composable
private fun PrivacyRow(@DrawableRes icon: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(EchoTheme.radii.md)
                .background(EchoTheme.colors.surfaceAccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = EchoTheme.colors.textAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textPrimary,
        )
    }
}

@Composable
internal fun ColumnScope.MicrophoneStep(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s9, Alignment.CenterVertically),
    ) {
        Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            // The record button's own gradient, pinned in both themes like the button itself.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .echoShadow(EchoTheme.elevation.haloBronze, cornerRadius = 60.dp)
                    .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = 60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Bronze400, Bronze500),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = EchoTheme.colors.recordGlyph,
                    modifier = Modifier.size(46.dp),
                )
            }
            Mascot(
                size = 64.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-18).dp, y = (-26).dp)
                    .rotate(8f),
            )
        }

        CenteredIntro(
            title = "Let Echo hear you",
            body = "Echo needs the microphone to record. Audio is processed on this phone and never uploaded.",
        )

        Row(
            modifier = Modifier
                .clip(EchoTheme.radii.md)
                .background(EchoTheme.colors.statusInfoBg)
                .padding(horizontal = EchoTheme.spacing.s6, vertical = EchoTheme.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = EchoTheme.colors.statusInfoFg,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "You can change this anytime in Settings",
                style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.statusInfoFg,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
        EchoButton(
            text = "Allow microphone",
            onClick = onAllow,
            size = ButtonSize.Lg,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
        EchoButton(
            text = "Not now",
            onClick = onNotNow,
            variant = ButtonVariant.Ghost,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ColumnScope.FirstNoteStep(
    needsDownload: Boolean,
    onDownload: () -> Unit,
    onRecord: () -> Unit,
    onLater: () -> Unit,
) {
    StepIntro(
        title = "Try your first note",
        body = "Tap the button and say what's on your mind. Echo turns it into a note when you stop.",
    )

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        RecordButton(state = RecordButtonState.Idle, onClick = onRecord, size = RecordButtonSize.Lg)
        Text(
            text = "Tap to start",
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textSecondary,
        )
    }

    // Not in the design: only shown off Wi-Fi, where Welcome deliberately did not start the
    // download. Without it the first note would sit "waiting for model" with no way forward here.
    if (needsDownload) {
        EchoButton(
            text = "Download AI · 977 MB",
            onClick = onDownload,
            variant = ButtonVariant.Soft,
            size = ButtonSize.Sm,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Not sure what to say? Try",
            style = EchoTheme.typography.microCaps.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textSecondary,
        )
        EchoCard(tone = CardTone.Cream, padding = CardPadding.Sm, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s1),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = EchoTheme.colors.textAccent,
                    modifier = Modifier.padding(top = EchoTheme.spacing.s1).size(16.dp),
                )
                Text(
                    text = "\"Remind me to water the plants tonight, and add milk and eggs to my shopping list.\"",
                    style = EchoTheme.typography.body.copy(lineHeight = 24.3.sp),
                    color = EchoTheme.colors.textPrimary,
                )
            }
        }
    }

    EchoButton(
        text = "I'll do it later",
        onClick = onLater,
        variant = ButtonVariant.Ghost,
        fullWidth = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepIntro(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = EchoTheme.typography.title, color = EchoTheme.colors.textPrimary)
        Text(text = body, style = EchoTheme.typography.body, color = EchoTheme.colors.textSecondary)
    }
}

@Composable
private fun CenteredIntro(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = EchoTheme.typography.title,
            color = EchoTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = EchoTheme.typography.body,
            color = EchoTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 310.dp),
        )
    }
}

@Composable
private fun PrimaryContinue(onContinue: () -> Unit) {
    EchoButton(
        text = "Continue",
        onClick = onContinue,
        size = ButtonSize.Lg,
        fullWidth = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
