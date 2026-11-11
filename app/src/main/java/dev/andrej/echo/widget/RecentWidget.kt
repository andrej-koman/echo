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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import dev.andrej.echo.R
import dev.andrej.echo.notify.RecordingMode

/** The last three notes, with how far along each is. */
class RecentWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = context.widgets()
        provideContent {
            val snapshot by widgets.snapshot.collectAsState()
            RecentContent(snapshot)
        }
    }
}

class RecentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecentWidget()
}

@Composable
private fun RecentContent(snapshot: WidgetSnapshot) {
    Column(
        modifier = GlanceModifier.fillMaxSize().widgetCard().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(40.dp).padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent", style = text(16.sp, WidgetColors.tx, FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot.recent.isNotEmpty()) RecordDisc(snapshot, 40.dp, icon = 18.dp)
        }
        if (snapshot.recent.isEmpty()) {
            Empty(snapshot)
        } else {
            snapshot.recent.forEachIndexed { index, note ->
                if (index > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.hair)) {}
                NoteRow(note)
            }
        }
    }
}

@Composable
private fun NoteRow(note: WidgetNote) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp).clickable(openNote(context, note.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title,
                style = text(14.sp, WidgetColors.tx, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            when (val tag = note.tag) {
                null -> Text(note.shortDate, style = text(12.sp, WidgetColors.tx3))
                is NoteTag.Transcribing -> Tag("Transcribing ${tag.percent}%", R.drawable.widget_tag_sunk, WidgetColors.tx2)
                NoteTag.Writing -> Tag("Writing summary", R.drawable.widget_tag_cream, WidgetColors.accTx)
                NoteTag.Failed -> Tag("Couldn’t finish", R.drawable.widget_tag_danger, WidgetColors.danger)
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        val tag = note.tag
        if (tag is NoteTag.Transcribing) {
            LinearProgressIndicator(
                progress = tag.percent / 100f,
                color = WidgetColors.bronze,
                backgroundColor = WidgetColors.track,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
            )
        } else {
            Text(note.line, style = text(12.sp, WidgetColors.tx2), maxLines = 1)
        }
    }
}

@Composable
private fun Tag(label: String, background: Int, color: androidx.glance.unit.ColorProvider) {
    Text(
        label,
        style = text(11.sp, color, FontWeight.Medium),
        maxLines = 1,
        modifier = GlanceModifier.background(ImageProvider(background)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun Empty(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing recorded yet", style = text(15.sp, WidgetColors.tx, FontWeight.Medium))
        Spacer(GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.height(44.dp).background(ImageProvider(R.drawable.widget_pill_bronze))
                .padding(start = 14.dp, end = 18.dp)
                .clickable(if (snapshot.recording.mode == RecordingMode.Idle) recordAction(context, snapshot) else openEcho(context)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(R.drawable.ic_mic, 18.dp, WidgetColors.onBronze)
            Spacer(GlanceModifier.width(8.dp))
            Text("Record a note", style = text(14.sp, WidgetColors.onBronze, FontWeight.Medium))
        }
    }
}
