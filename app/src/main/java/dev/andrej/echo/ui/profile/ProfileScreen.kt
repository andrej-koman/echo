package dev.andrej.echo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.SpaceMono

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    authState: AuthState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Profile",
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EchoTheme.spacing.gutterScreen),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            when (authState) {
                is AuthState.SignedIn -> {
                    IdentityCard(authState)
                    StatsGrid(state)
                    WeekCard(state, onClick = onOpenHistory)
                    Box(modifier = Modifier.weight(1f))
                    AccountRail(
                        onSignOut = onSignOut,
                        onDeleteAccount = { confirmingDelete = true },
                    )
                }

                else -> SignedOutBody(onSignIn = onSignIn)
            }
        }
    }

    ConfirmSheet(
        visible = confirmingDelete,
        title = "Delete account?",
        message = "Your notes and tasks stay on this device. This just signs Echo out and forgets " +
            "your account here.",
        confirmLabel = "Delete account",
        onConfirm = {
            confirmingDelete = false
            onDeleteAccount()
        },
        onDismiss = { confirmingDelete = false },
    )
}

@Composable
private fun IdentityCard(state: AuthState.SignedIn) {
    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(state)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.account.displayName ?: state.account.email ?: "Signed in",
                    style = EchoTheme.typography.titleSm,
                    color = EchoTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.account.email.orEmpty(),
                    style = EchoTheme.typography.caption,
                    color = EchoTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(state: ProfileUiState) {
    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("NOTES", state.notesCount.toString(), Modifier.weight(1f))
            Stat("TASKS DONE", state.tasksDoneCount.toString(), Modifier.weight(1f))
            Stat("CAPTURED", state.capturedTotal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = EchoTheme.typography.title.copy(fontFamily = SpaceMono),
            color = EchoTheme.colors.textPrimary,
        )
        Text(
            text = label,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun WeekCard(state: ProfileUiState, onClick: () -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "This week",
                style = EchoTheme.typography.heading,
                color = EchoTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "HISTORY",
                style = EchoTheme.typography.monoCaption,
                color = EchoTheme.colors.textAccent,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = EchoTheme.colors.textTertiary,
                modifier = Modifier.size(15.dp).padding(start = EchoTheme.spacing.s2),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EchoTheme.spacing.s5)
                .height(62.dp),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        ) {
            state.week.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((48 * day.fraction).dp.coerceAtLeast(4.dp))
                        .clip(EchoTheme.radii.xs)
                        .background(
                            if (day.active) EchoTheme.colors.textAccent else EchoTheme.colors.borderDefault,
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        ) {
            state.week.forEach { day ->
                Text(
                    text = day.label,
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccountRail(onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        EchoButton(text = "Sign out", onClick = onSignOut, variant = ButtonVariant.Secondary, fullWidth = true)
        EchoButton(text = "Delete account", onClick = onDeleteAccount, variant = ButtonVariant.Ghost, fullWidth = true)
        Box(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SignedOutBody(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            variant = MascotVariant.Plain,
            title = "Sign in to sync your account",
            body = "Recordings and transcripts already live on this device. Signing in just gives " +
                "them a name and an email to remember.",
        )
        EchoButton(
            text = "Sign in with Google",
            onClick = onSignIn,
            variant = ButtonVariant.Primary,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s6),
        )
    }
}

@Composable
private fun Avatar(state: AuthState) {
    val initial = (state as? AuthState.SignedIn)
        ?.account
        ?.let { it.displayName ?: it.email }
        ?.firstOrNull()
        ?.uppercaseChar()

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(EchoTheme.radii.pill)
            .background(EchoTheme.colors.surfaceAccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (initial == null) {
            Icon(
                painter = painterResource(R.drawable.ic_user),
                contentDescription = null,
                tint = EchoTheme.colors.textAccent,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                text = initial.toString(),
                style = EchoTheme.typography.titleSm,
                color = EchoTheme.colors.textAccent,
            )
        }
    }
}
