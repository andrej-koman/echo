package dev.andrej.echo.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun AccountScreen(
    state: AuthState,
    aiState: AiCardState,
    onSignOut: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(title = "Profile")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EchoTheme.spacing.gutterScreen),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            EchoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(state)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = when (state) {
                                is AuthState.SignedIn ->
                                    state.account.displayName ?: state.account.email ?: "Signed in"

                                else -> "No account"
                            },
                            style = EchoTheme.typography.titleSm,
                            color = EchoTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when (state) {
                                is AuthState.SignedIn -> state.account.email.orEmpty()
                                else -> "Using Echo without signing in"
                            },
                            style = EchoTheme.typography.caption,
                            color = EchoTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AiCard(
                state = aiState,
                onDownload = onDownloadModel,
                onCancel = onCancelDownload,
                onDelete = onDeleteModel,
            )

            Text(
                text = "Recordings and transcripts are stored on this device only. " +
                    "Signing out leaves them where they are.",
                style = EchoTheme.typography.caption,
                color = EchoTheme.colors.textTertiary,
            )

            EchoButton(
                text = if (state is AuthState.SignedIn) "Sign out" else "Sign in",
                onClick = onSignOut,
                variant = ButtonVariant.Secondary,
                fullWidth = true,
            )

            Box(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AiCard(
    state: AiCardState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    if (state is AiCardState.Checking) return

    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            Text(
                text = when (state) {
                    AiCardState.NanoReady -> "Using your phone's built-in AI"
                    is AiCardState.NeedsDownload ->
                        "Pull tasks and reminders out of your recordings. " +
                            "~${state.bytes / 1_000_000}MB, Wi-Fi recommended."

                    is AiCardState.Downloading -> "Downloading…"
                    AiCardState.LiteRtReady -> "Ready · on this device"
                    is AiCardState.Unsupported -> "This phone can't run on-device AI."
                    AiCardState.Checking -> ""
                },
                style = EchoTheme.typography.bodySm,
                color = EchoTheme.colors.textPrimary,
            )

            if (state is AiCardState.Downloading) {
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = EchoTheme.colors.textAccent,
                    trackColor = EchoTheme.colors.surfaceAccentSoft,
                )
            }

            when (state) {
                is AiCardState.NeedsDownload ->
                    EchoButton(
                        text = "Download",
                        onClick = onDownload,
                        variant = ButtonVariant.Secondary,
                        fullWidth = true,
                    )

                is AiCardState.Downloading ->
                    EchoButton(
                        text = "Cancel",
                        onClick = onCancel,
                        variant = ButtonVariant.Secondary,
                        fullWidth = true,
                    )

                AiCardState.LiteRtReady ->
                    EchoButton(
                        text = "Remove",
                        onClick = onDelete,
                        variant = ButtonVariant.Secondary,
                        fullWidth = true,
                    )

                else -> Unit
            }
        }
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
