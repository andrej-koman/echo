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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import dev.andrej.echo.ui.theme.EchoTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private var pendingOpenCapture by mutableStateOf(false)
    private var pendingOpenTask by mutableStateOf<String?>(null)
    private var pendingOpenNote by mutableStateOf<OpenNote?>(null)
    private var pendingOpenCalendar by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.onAppForegrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as EchoApplication).container
        pendingOpenCapture = consumeBooleanExtra(intent, EXTRA_OPEN_CAPTURE)
        pendingOpenTask = consumeTaskExtra(intent)
        pendingOpenNote = consumeNoteExtra(intent)
        pendingOpenCalendar = consumeBooleanExtra(intent, EXTRA_OPEN_CALENDAR)
        // A recreation replays the same Intent; the import already happened the first time.
        if (savedInstanceState == null) importShared(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.derived.items.collect { tasks ->
                    if (Build.VERSION.SDK_INT >= 33 && tasks.any { !it.done && !it.reminderDelivered && it.reminderAt != null } &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        val preferences = getSharedPreferences("task_notifications", MODE_PRIVATE)
                        if (!preferences.getBoolean("permission_requested", false)) {
                            preferences.edit().putBoolean("permission_requested", true).apply()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }

        setContent {
            val themeMode by container.themeMode.collectAsStateWithLifecycle()
            EchoTheme(themeMode = themeMode) {
                EchoApp(
                    viewModelFactory = container.viewModelFactory,
                    billing = container.billing,
                    onboardingDone = container.onboardingDone,
                    themeMode = themeMode,
                    onThemeModeChange = container::setThemeMode,
                    pendingOpenCapture = pendingOpenCapture,
                    onOpenCaptureHandled = { pendingOpenCapture = false },
                    pendingOpenTask = pendingOpenTask,
                    onOpenTaskHandled = { pendingOpenTask = null },
                    pendingOpenNote = pendingOpenNote,
                    onOpenNoteHandled = { pendingOpenNote = null },
                    pendingOpenCalendar = pendingOpenCalendar,
                    onOpenCalendarHandled = { pendingOpenCalendar = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenCapture = consumeBooleanExtra(intent, EXTRA_OPEN_CAPTURE)
        pendingOpenTask = consumeTaskExtra(intent)
        consumeNoteExtra(intent)?.let { pendingOpenNote = it }
        if (consumeBooleanExtra(intent, EXTRA_OPEN_CALENDAR)) pendingOpenCalendar = true
        importShared(intent)
    }

    private fun importShared(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        container.transcriptions.offer(uris)
    }

    /** Removes the extra so a config change, which recreates the Activity from the same Intent, does not navigate twice. */
    private fun consumeBooleanExtra(intent: Intent, key: String): Boolean {
        val value = intent.getBooleanExtra(key, false)
        if (value) intent.removeExtra(key)
        return value
    }

    private fun consumeTaskExtra(intent: Intent): String? =
        intent.getStringExtra(EXTRA_OPEN_TASK)?.also { intent.removeExtra(EXTRA_OPEN_TASK) }

    private fun consumeNoteExtra(intent: Intent): OpenNote? {
        val id = intent.getStringExtra(EXTRA_OPEN_NOTE) ?: return null
        val tasks = intent.getBooleanExtra(EXTRA_OPEN_NOTE_TASKS, false)
        intent.removeExtra(EXTRA_OPEN_NOTE)
        intent.removeExtra(EXTRA_OPEN_NOTE_TASKS)
        return OpenNote(id, tasks)
    }

    override fun onStart() {
        super.onStart()
        container.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        container.onAppBackgrounded()
    }

    companion object {
        const val EXTRA_OPEN_CAPTURE = "open_capture"
        const val EXTRA_OPEN_TASK = "open_task"
        const val EXTRA_OPEN_NOTE = "open_note"
        const val EXTRA_OPEN_NOTE_TASKS = "open_note_tasks"
        const val EXTRA_OPEN_CALENDAR = "open_calendar"
    }
}

data class OpenNote(val id: String, val tasks: Boolean)
