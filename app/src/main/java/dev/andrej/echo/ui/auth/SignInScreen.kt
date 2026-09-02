package dev.andrej.echo.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun SignInScreen(
    busy: Boolean,
    error: String?,
    onSignInWithGoogle: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EchoTheme.spacing.s9),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Mascot(size = 104.dp, mood = MascotMood.Breathing)

        Spacer(Modifier.size(EchoTheme.spacing.s8))

        Text(
            text = "Echo",
            style = EchoTheme.typography.display,
            color = EchoTheme.colors.textPrimary,
        )

        Spacer(Modifier.size(EchoTheme.spacing.s3))

        Text(
            text = "Speak once, filed correctly.",
            style = EchoTheme.typography.body,
            color = EchoTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        if (error != null) {
            Text(
                text = error,
                style = EchoTheme.typography.caption,
                color = EchoTheme.colors.statusDangerFg,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = EchoTheme.spacing.s5),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EchoButton(
                text = "Continue with Google",
                onClick = onSignInWithGoogle,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Lg,
                fullWidth = true,
                loading = busy,
                enabled = !busy,
                leading = {
                    Image(
                        painter = painterResource(R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )

            EchoButton(
                text = "Continue without an account",
                onClick = onContinueWithoutAccount,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Md,
                fullWidth = true,
                enabled = !busy,
            )
        }

        Spacer(Modifier.size(EchoTheme.spacing.s6))

        Text(
            text = "Transcription runs on the device. An account only labels this install — " +
                "nothing is uploaded.",
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(EchoTheme.spacing.s9))
    }
}
