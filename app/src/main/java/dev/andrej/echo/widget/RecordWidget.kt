package dev.andrej.echo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import dev.andrej.echo.notify.RecordingMode

/** 1×1: one disc filling the cell that starts, stops and saves, or resumes. */
class RecordWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { RecordContent(previewSnapshot) }
    }

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
    val size = LocalSize.current
    val side = minOf(size.width, size.height) - 4.dp
    // The live halo adds 10 dp around the disc, so the disc shrinks to keep the same footprint.
    val diameter = if (snapshot.recording.mode == RecordingMode.Recording) side - 10.dp else side
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().clickable(recordAction(context, snapshot)),
        contentAlignment = Alignment.Center,
    ) {
        RecordDisc(snapshot, diameter, reserveHalo = false)
    }
}
