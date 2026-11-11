package dev.andrej.echo.ui.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.billing.Plan
import dev.andrej.echo.billing.PlanPeriod
import dev.andrej.echo.ui.components.*
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import dev.andrej.echo.ui.theme.rememberEchoHaptics
import java.text.NumberFormat
import java.time.Period
import java.util.Currency
import kotlin.math.roundToInt

@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    onBack: () -> Unit,
    unlockNote: UnlockNote? = null,
    renewsAt: Long? = null,
    onOpenNote: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val activePlan by viewModel.activePlan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.load() }
    if (isPro) {
        ProWelcome(unlockNote, activePlan, renewsAt, onOpenNote, onBack, state.arrival, state.busy, viewModel::consumeArrival)
        return
    }
    val haptics = rememberEchoHaptics()
    val colors = EchoTheme.colors
    val plans = state.plans.sortedBy { when (it.period) { PlanPeriod.Yearly -> 0; PlanPeriod.Monthly -> 1; PlanPeriod.Lifetime -> 2 } }
    val monthly = plans.firstOrNull { it.period == PlanPeriod.Monthly }
    val selected = state.selected
    Column(
        Modifier.fillMaxSize().background(colors.surfacePage).glow(colors.surfaceAccentSoft)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = EchoTheme.spacing.gutterScreen, end = EchoTheme.spacing.s4, top = EchoTheme.spacing.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pro", style = EchoTheme.typography.heading, color = colors.textAccent)
            EchoIconButton(iconRes = R.drawable.ic_x, contentDescription = "Close", onClick = onBack)
        }
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = maxHeight)
                    .padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s8, Alignment.CenterVertically),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                    Text("Transcribe every minute", style = EchoTheme.typography.titleSm, color = colors.textPrimary, textAlign = TextAlign.Center)
                    Text(
                        "Free notes stop at 2 minutes. Pro turns the whole recording into text, a summary and tasks.",
                        style = EchoTheme.typography.bodySm, color = colors.textSecondary, textAlign = TextAlign.Center,
                    )
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6)) {
                    Feature(R.drawable.ic_infinity, "No time limit", "Meetings, lectures, hours-long recordings")
                    Feature(R.drawable.ic_sparkles, "Summary and tasks from all of it", "Not just the first 2 minutes")
                    Feature(R.drawable.ic_upload, "Full-length imports", "Turn audio you already have into complete notes")
                    Feature(R.drawable.ic_wifi_off, "Still fully offline", "Your audio never leaves your phone", sage = true)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = scroll.canScrollForward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.surfacePage))),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_down), "More below",
                        tint = colors.textTertiary, modifier = Modifier.padding(bottom = EchoTheme.spacing.s2).size(20.dp),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            if (state.loading) {
                CircularProgressIndicator(color = colors.textAccent)
            } else {
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
                    plans.forEach { plan ->
                        PlanCard(plan, monthly, selected = plan.id == selected?.id, enabled = !state.busy, onSelect = {
                            if (plan.id != selected?.id) haptics.tick()
                            viewModel.select(plan)
                        })
                    }
                }
            }
            state.message?.let {
                Text(it, style = EchoTheme.typography.bodySm, color = colors.textSecondary, textAlign = TextAlign.Center)
                if (plans.isEmpty()) EchoButton("Try again", viewModel::load, variant = ButtonVariant.Secondary, enabled = !state.busy && !state.loading)
            }
            EchoButton(
                text = selected?.let(::ctaText) ?: "Get Echo Pro",
                onClick = { context.findActivity()?.let(viewModel::purchase) },
                enabled = !state.loading && selected != null && context.findActivity() != null,
                loading = state.busy,
                fullWidth = true,
                size = ButtonSize.Md,
            )
            selected?.let {
                // Two lines reserved so switching plans doesn't shift the layout.
                Text(captionText(it), style = EchoTheme.typography.caption, color = colors.textSecondary, textAlign = TextAlign.Center, minLines = 2)
            }
            // Placeholders until the legal URLs exist.
            Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                val canRestore = !state.loading && !state.busy
                Text(
                    "Restore purchase",
                    style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (canRestore) colors.textSecondary else colors.textTertiary,
                    modifier = Modifier.clickable(enabled = canRestore, role = Role.Button, onClick = viewModel::restore),
                )
                Text("Terms", style = EchoTheme.typography.caption, color = colors.textTertiary)
                Text("Privacy", style = EchoTheme.typography.caption, color = colors.textTertiary)
            }
        }
    }
}

private fun ctaText(plan: Plan) = when (plan.period) {
    PlanPeriod.Yearly -> "Start Pro for ${plan.localizedPrice} a year"
    PlanPeriod.Monthly -> "Start Pro for ${plan.localizedPrice} a month"
    PlanPeriod.Lifetime -> "Get Pro for ${plan.localizedPrice} once"
}

private fun captionText(plan: Plan) = when (plan.period) {
    PlanPeriod.Yearly -> "Renews yearly at ${plan.localizedPrice} until you cancel in Google Play."
    PlanPeriod.Monthly -> "Renews monthly at ${plan.localizedPrice} until you cancel in Google Play."
    PlanPeriod.Lifetime -> "One payment. No subscription."
}

@Composable
private fun Feature(icon: Int, title: String, caption: String, sage: Boolean = false) {
    val colors = EchoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (sage) colors.surfaceSageSoft else colors.surfaceAccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = if (sage) colors.textSuccess else colors.textAccent, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(caption, style = EchoTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

private fun formatMicros(micros: Long, currencyCode: String): String? = runCatching {
    val value = micros / 1_000_000.0
    NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(currencyCode)
        if (value % 1.0 == 0.0) maximumFractionDigits = 0
    }.format(value)
}.getOrNull()

@Composable
private fun PlanCard(plan: Plan, monthly: Plan?, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = EchoTheme.colors
    val yearly = plan.period == PlanPeriod.Yearly
    val saving = if (yearly && monthly != null && plan.priceMicros > 0 && monthly.priceMicros > 0) {
        (100 * (1 - plan.priceMicros.toDouble() / (12 * monthly.priceMicros))).roundToInt().takeIf { it > 0 }
    } else null
    val struck = if (yearly && saving != null && monthly != null) formatMicros(12 * monthly.priceMicros, monthly.currencyCode) else null
    val title = when (plan.period) { PlanPeriod.Yearly -> "Pro yearly"; PlanPeriod.Monthly -> "Pro monthly"; PlanPeriod.Lifetime -> "Pro lifetime" }
    val unit = when (plan.period) { PlanPeriod.Yearly -> "/ year"; PlanPeriod.Monthly -> "/ month"; PlanPeriod.Lifetime -> "once" }
    val sub = plan.trial?.takeIf { !plan.period.isLifetime() }?.let { "${trialLength(it.period, it.cycles)} free trial, then ${plan.localizedPrice}" }
        ?: when (plan.period) {
            PlanPeriod.Yearly -> plan.localizedPricePerMonth?.let { "$it a month, billed yearly" } ?: "Billed yearly"
            PlanPeriod.Monthly -> "Cancel anytime"
            PlanPeriod.Lifetime -> "Pay once, keep Pro forever"
        }
    Row(
        Modifier.fillMaxWidth().clip(EchoTheme.radii.lg)
            .background(if (selected) colors.surfaceRaised else colors.surfaceCard)
            .hairline(if (selected) colors.textAccent else colors.borderSubtle, cornerRadius = 18.dp, width = if (selected) 2.dp else 1.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = EchoTheme.spacing.s5, vertical = EchoTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).hairline(if (selected) colors.textAccent else colors.borderStrong, cornerRadius = 9.dp, width = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.textAccent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                saving?.let { EchoBadge("Save $it%", tone = BadgeTone.Success) }
            }
            Text(sub, style = EchoTheme.typography.caption, color = if (plan.trial != null && !plan.period.isLifetime()) colors.textAccent else colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                Text(plan.localizedPrice, style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                Text(unit, style = EchoTheme.typography.caption, color = colors.textSecondary)
            }
            struck?.let {
                Text(it, style = EchoTheme.typography.caption.copy(textDecoration = TextDecoration.LineThrough), color = colors.textTertiary)
            }
        }
    }
}

private fun PlanPeriod.isLifetime() = this == PlanPeriod.Lifetime

private fun trialLength(iso: String, cycles: Int): String = runCatching {
    val period = Period.parse(iso).multipliedBy(cycles)
    listOfNotNull(
        period.years.takeIf { it > 0 }?.let { "$it-year" },
        period.months.takeIf { it > 0 }?.let { "$it-month" },
        period.days.takeIf { it > 0 }?.let { "$it-day" },
    ).joinToString(" ")
}.getOrDefault(iso)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
