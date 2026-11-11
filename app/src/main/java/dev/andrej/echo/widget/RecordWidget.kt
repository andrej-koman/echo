package dev.andrej.echo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import dev.andrej.echo.notify.RecordingMode

/** 1×1: one disc that starts, stops and saves, or resumes. */
class RecordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = context.widgets()
        provideContent {
            val snapshot by widgets.snapshot.collectAsState()
            RecordContent(snapshot)
        }
    }
}

class RecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecordWidget()
}

@Composable
private fun RecordContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val mode = snapshot.recording.mode
    Column(
        modifier = GlanceModifier.fillMaxSize().widgetCard().clickable(recordAction(context, snapshot)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mode == RecordingMode.Idle && snapshot.micAllowed) {
            RecordDisc(snapshot, 56.dp)
        } else {
            RecordDisc(snapshot, 44.dp)
            Spacer(GlanceModifier.height(5.dp))
            if (mode == RecordingMode.Idle) {
                Text("Allow mic", style = text(12.sp, WidgetColors.warn, FontWeight.Medium))
            } else {
                Elapsed(snapshot.recording, large = false)
            }
        }
    }
}
