package dev.andrej.echo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import dev.andrej.echo.R

/** Today's tasks: tick and snooze in place. Narrow shows text only, a row opens its task. */
class TasksWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(NARROW, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgets = context.widgets()
        provideContent {
            val snapshot by widgets.snapshot.collectAsState()
            TasksContent(snapshot)
        }
    }

    private companion object {
        val NARROW = DpSize(160.dp, 200.dp)
        val WIDE = DpSize(320.dp, 200.dp)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TasksWidget()
}

@Composable
private fun TasksContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= 250.dp
    val tasks = snapshot.today
    // Header 40, the "+N more" line 18, padding 22; whatever is left holds 44 dp rows.
    val fits = ((size.height.value - 80) / 44).toInt().coerceAtLeast(1)
    val shown = if (tasks.size > fits) tasks.take(fits) else tasks
    val more = tasks.count { !it.done } - shown.count { !it.done }

    Column(
        modifier = GlanceModifier.fillMaxSize().widgetCard()
            .padding(start = if (wide) 14.dp else 12.dp, end = if (wide) 12.dp else 10.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(40.dp).padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.clickable(openToday(context)), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = text(16.sp, WidgetColors.tx, FontWeight.Medium))
                val open = tasks.count { !it.done }
                if (open > 0) {
                    Spacer(GlanceModifier.width(if (wide) 8.dp else 6.dp))
                    Text(
                        open.toString(),
                        style = text(12.sp, WidgetColors.accTx, FontWeight.Medium),
                        modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_pill_acc_soft))
                            .padding(horizontal = if (wide) 8.dp else 7.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            RecordDisc(snapshot, if (wide) 40.dp else 36.dp, icon = if (wide) 18.dp else 16.dp)
        }
        if (tasks.isEmpty()) {
            Empty()
        } else {
            shown.forEach { TaskRow(it, wide) }
            Spacer(GlanceModifier.defaultWeight())
            if (more > 0) {
                Text(
                    "+$more more",
                    style = text(12.sp, WidgetColors.accTx, FontWeight.Medium),
                    modifier = GlanceModifier.padding(start = if (wide) 8.dp else 6.dp).clickable(openToday(context)),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTask, wide: Boolean) {
    val context = LocalContext.current
    val row = GlanceModifier.fillMaxWidth().height(44.dp).padding(start = 6.dp, end = if (wide) 2.dp else 4.dp)
        .let { if (task.overdue && !task.done) it.background(ImageProvider(R.drawable.widget_row_warn)) else it }
        .let { if (!wide && !task.done) it.clickable(openTask(context, task.id)) else it }
    Row(modifier = row, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier.size(22.dp)
                .background(ImageProvider(if (task.done) R.drawable.widget_check_done else R.drawable.widget_check_empty))
                .clickable(tickAction(task.id)),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) Glyph(R.drawable.ic_check, 14.dp, WidgetColors.onBronze, "Done")
        }
        Spacer(GlanceModifier.width(if (wide) 10.dp else 8.dp))
        Text(
            task.text,
            style = text(14.sp, if (task.done) WidgetColors.tx3 else WidgetColors.tx, strike = task.done),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!wide) return@Row
        when {
            task.done -> Text("Done", style = text(12.sp, WidgetColors.tx3), modifier = GlanceModifier.padding(start = 10.dp, end = 8.dp))
            task.snoozed -> Text(
                listOfNotNull(task.time, "+1h").joinToString(" · "),
                style = text(12.sp, WidgetColors.accTx, FontWeight.Medium),
                modifier = GlanceModifier.padding(start = 10.dp).background(ImageProvider(R.drawable.widget_pill_acc_soft))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            else -> {
                task.time?.let {
                    Text(
                        it,
                        style = text(12.sp, if (task.overdue) WidgetColors.warn else WidgetColors.tx2, FontWeight.Medium),
                        modifier = GlanceModifier.padding(start = 10.dp),
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    "+1h",
                    style = text(12.sp, WidgetColors.tx2, FontWeight.Medium),
                    modifier = GlanceModifier.background(ImageProvider(R.drawable.widget_pill_sunk))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .clickable(snoozeAction(task.id)),
                )
            }
        }
    }
}

@Composable
private fun Empty() {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_circle_acc_soft)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(R.drawable.ic_check, 20.dp, WidgetColors.accTx)
        }
        Spacer(GlanceModifier.height(8.dp))
        Text("Nothing due", style = text(15.sp, WidgetColors.tx, FontWeight.Medium))
        Text("Tasks from your notes show up here", style = text(13.sp, WidgetColors.tx2))
    }
}
