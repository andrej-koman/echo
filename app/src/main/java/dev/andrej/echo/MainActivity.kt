package dev.andrej.echo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.andrej.echo.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private var pendingOpenTranscriptId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as EchoApplication).container
        pendingOpenTranscriptId = consumeTranscriptExtra(intent)

        setContent {
            EchoTheme {
                EchoApp(
                    viewModelFactory = container.viewModelFactory,
                    pendingOpenTranscriptId = pendingOpenTranscriptId,
                    onOpenTranscriptHandled = { pendingOpenTranscriptId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenTranscriptId = consumeTranscriptExtra(intent)
    }

    /** Removes the extra so a config change, which recreates the Activity from the same Intent, does not navigate twice. */
    private fun consumeTranscriptExtra(intent: Intent): String? {
        val id = intent.getStringExtra(EXTRA_TRANSCRIPT_ID)
        intent.removeExtra(EXTRA_TRANSCRIPT_ID)
        return id
    }

    override fun onStart() {
        super.onStart()
        container.onAppForegrounded()
    }

    companion object {
        const val EXTRA_TRANSCRIPT_ID = "transcript_id"
    }
}
