package dev.andrej.echo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import dev.andrej.echo.R
import dev.andrej.echo.notify.RecordingMode

/** 4×1: disc, today's count and the latest note in one strip. */
class CombinedWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = context.widgets()
        provideContent {
            val snapshot by widgets.snapshot.collectAsState()
            CombinedContent(snapshot)
        }
    }
}

class CombinedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CombinedWidget()
}

@Composable
private fun CombinedContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val mode = snapshot.recording.mode
    Row(
        modifier = GlanceModifier.fillMaxSize().widgetCard().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordDisc(snapshot, 56.dp)
        when (mode) {
            RecordingMode.Idle -> Column(
                modifier = GlanceModifier.width(104.dp).fillMaxHeight().padding(horizontal = 10.dp).clickable(openToday(context)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(snapshot.today.size.toString(), style = text(20.sp, WidgetColors.tx, FontWeight.Medium))
                Text("due today", style = text(12.sp, WidgetColors.tx2))
            }
            RecordingMode.Recording -> Column(
                modifier = GlanceModifier.width(104.dp).padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Elapsed(snapshot.recording, large = true)
                Text("Recording", style = text(12.sp, WidgetColors.tx2))
            }
            RecordingMode.Paused -> {
                Column(modifier = GlanceModifier.width(76.dp).padding(start = 14.dp)) {
                    Elapsed(snapshot.recording, large = true)
                    Text("Paused", style = text(12.sp, WidgetColors.tx2))
                }
                Box(
                    modifier = GlanceModifier.size(44.dp).background(ImageProvider(R.drawable.widget_disc_sunk))
                        .clickable(stopAction(context)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(GlanceModifier.size(12.dp).background(ImageProvider(R.drawable.widget_stop_square_small))) {}
                }
                Spacer(GlanceModifier.width(4.dp))
            }
        }
        Box(GlanceModifier.width(1.dp).height(36.dp).background(WidgetColors.hair)) {}
        val last = snapshot.recent.firstOrNull()
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 10.dp)
                .clickable(if (last != null) openNote(context, last.id) else openEcho(context)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(last?.title ?: "No notes yet", style = text(14.sp, WidgetColors.tx, FontWeight.Medium), maxLines = 1)
            if (last != null) Text(last.date, style = text(12.sp, WidgetColors.tx2), maxLines = 1)
        }
    }
}
