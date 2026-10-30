package dev.andrej.echo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.andrej.echo.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private var pendingOpenCapture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as EchoApplication).container
        pendingOpenCapture = consumeBooleanExtra(intent, EXTRA_OPEN_CAPTURE)
        // A recreation replays the same Intent; the import already happened the first time.
        if (savedInstanceState == null) importShared(intent)

        setContent {
            EchoTheme {
                EchoApp(
                    viewModelFactory = container.viewModelFactory,
                    onboardingDone = container.onboardingDone,
                    pendingOpenCapture = pendingOpenCapture,
                    onOpenCaptureHandled = { pendingOpenCapture = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenCapture = consumeBooleanExtra(intent, EXTRA_OPEN_CAPTURE)
        importShared(intent)
    }

    private fun importShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        container.transcriptions.import(uris)
    }

    /** Removes the extra so a config change, which recreates the Activity from the same Intent, does not navigate twice. */
    private fun consumeBooleanExtra(intent: Intent, key: String): Boolean {
        val value = intent.getBooleanExtra(key, false)
        if (value) intent.removeExtra(key)
        return value
    }

    override fun onStart() {
        super.onStart()
        container.onAppForegrounded()
    }

    companion object {
        const val EXTRA_OPEN_CAPTURE = "open_capture"
    }
}
