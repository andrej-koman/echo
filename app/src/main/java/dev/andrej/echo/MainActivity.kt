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
    private var pendingOpenTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as EchoApplication).container
        pendingOpenTranscriptId = consumeExtra(intent, EXTRA_TRANSCRIPT_ID)
        pendingOpenTaskId = consumeExtra(intent, EXTRA_TASK_ID)

        setContent {
            EchoTheme {
                EchoApp(
                    viewModelFactory = container.viewModelFactory,
                    pendingOpenTranscriptId = pendingOpenTranscriptId,
                    onOpenTranscriptHandled = { pendingOpenTranscriptId = null },
                    pendingOpenTaskId = pendingOpenTaskId,
                    onOpenTaskHandled = { pendingOpenTaskId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenTranscriptId = consumeExtra(intent, EXTRA_TRANSCRIPT_ID)
        pendingOpenTaskId = consumeExtra(intent, EXTRA_TASK_ID)
    }

    /** Removes the extra so a config change, which recreates the Activity from the same Intent, does not navigate twice. */
    private fun consumeExtra(intent: Intent, key: String): String? {
        val value = intent.getStringExtra(key)
        intent.removeExtra(key)
        return value
    }

    override fun onStart() {
        super.onStart()
        container.onAppForegrounded()
    }

    companion object {
        const val EXTRA_TRANSCRIPT_ID = "transcript_id"
        const val EXTRA_TASK_ID = "task_id"
    }
}
