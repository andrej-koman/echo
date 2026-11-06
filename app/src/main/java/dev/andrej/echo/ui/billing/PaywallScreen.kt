package dev.andrej.echo.ui.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.billing.Plan
import dev.andrej.echo.billing.PlanPeriod
import dev.andrej.echo.ui.components.*
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import java.time.Period

@Composable
fun PaywallScreen(viewModel: PaywallViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(isPro) { if (isPro) onBack() }
    Column(Modifier.fillMaxSize().background(EchoTheme.colors.surfacePage)) {
        EchoTopBar(title = "Echo Pro", leading = {
            EchoIconButton(iconRes = R.drawable.ic_chevron_left, contentDescription = "Back", onClick = onBack)
        })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            Mascot(size = 52.dp)
            Text("Keep the whole conversation.", style = EchoTheme.typography.display, color = EchoTheme.colors.textPrimary)
            Text("Transcribe full recordings and imports. Unlock the rest of your existing notes.", style = EchoTheme.typography.body, color = EchoTheme.colors.textSecondary)
            EchoCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
                    Text("Full-length transcription", style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
                    Text("Export Markdown, subtitles and original audio", style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
                    Text("Your audio and notes stay on your device", style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
                }
            }
            if (isPro) {
                Text("Echo Pro unlocked", style = EchoTheme.typography.heading, color = EchoTheme.colors.textAccent)
            } else if (state.loading) {
                CircularProgressIndicator(color = EchoTheme.colors.textAccent)
            } else {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
                    state.plans.sortedBy { when (it.period) { PlanPeriod.Yearly -> 0; PlanPeriod.Monthly -> 1; PlanPeriod.Lifetime -> 2 } }.forEach { plan ->
                        PlanCard(plan, selected = plan.id == state.selected?.id, enabled = !state.busy, onSelect = { viewModel.select(plan) })
                    }
                }
            }
            state.message?.let {
                Text(it, style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
                if (state.plans.isEmpty()) EchoButton("Try again", viewModel::load, variant = ButtonVariant.Secondary, enabled = !state.busy && !state.loading)
            }
            EchoButton(
                text = state.selected?.let { "Get Pro · ${it.localizedPrice}" } ?: "Get Echo Pro",
                onClick = { context.findActivity()?.let(viewModel::purchase) },
                enabled = !state.loading && state.selected != null && context.findActivity() != null,
                loading = state.busy,
                fullWidth = true,
                size = ButtonSize.Lg,
            )
            state.selected?.let { plan ->
                Text(
                    text = when (plan.period) {
                        PlanPeriod.Monthly -> "${plan.localizedPrice} per month. Renews automatically; cancel in Google Play."
                        PlanPeriod.Yearly -> "${plan.localizedPrice} per year. Renews automatically; cancel in Google Play."
                        PlanPeriod.Lifetime -> "${plan.localizedPrice} once. No subscription."
                    },
                    style = EchoTheme.typography.caption,
                    color = EchoTheme.colors.textSecondary,
                )
            }
            EchoButton("Restore purchases", viewModel::restore, variant = ButtonVariant.Ghost, enabled = !state.loading && !state.busy, fullWidth = true)
            Text("Purchases use Google Play. No Echo account needed.", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            Text("Terms of service · Link coming soon", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            Text("Privacy policy · Link coming soon", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val colors = EchoTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(EchoTheme.radii.md)
            .background(if (selected) colors.surfaceAccentSoft else colors.surfaceCard)
            .hairline(if (selected) colors.textAccent else colors.borderSubtle, cornerRadius = 12.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(EchoTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plan.period.name, style = EchoTheme.typography.heading, color = colors.textPrimary)
            if (plan.period == PlanPeriod.Yearly) EchoBadge("Recommended", tone = BadgeTone.Success)
        }
        Text(plan.localizedPrice, style = EchoTheme.typography.titleSm, color = colors.textPrimary)
        plan.localizedPricePerMonth?.let { Text("$it per month, billed yearly", style = EchoTheme.typography.caption, color = colors.textSecondary) }
        plan.trial?.let { trial ->
            val length = runCatching {
                val period = Period.parse(trial.period).multipliedBy(trial.cycles)
                listOfNotNull(
                    period.years.takeIf { it > 0 }?.let { "$it year${if (it == 1) "" else "s"}" },
                    period.months.takeIf { it > 0 }?.let { "$it month${if (it == 1) "" else "s"}" },
                    period.days.takeIf { it > 0 }?.let { "$it day${if (it == 1) "" else "s"}" },
                ).joinToString(" ")
            }.getOrDefault(trial.period)
            Text("$length free trial, then ${plan.localizedPrice}", style = EchoTheme.typography.caption, color = colors.textAccent)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
