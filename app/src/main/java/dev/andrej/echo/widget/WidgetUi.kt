package dev.andrej.echo.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.andrej.echo.R
import dev.andrej.echo.notify.RecordingMode
import dev.andrej.echo.notify.clock

/** Day/night pairs live in `widget_colors.xml`; Glance resolves them against the system theme. */
internal object WidgetColors {
    val tx = ColorProvider(R.color.widget_tx)
    val tx2 = ColorProvider(R.color.widget_tx2)
    val tx3 = ColorProvider(R.color.widget_tx3)
    val hair = ColorProvider(R.color.widget_hair)
    val accTx = ColorProvider(R.color.widget_acc_tx)
    val warn = ColorProvider(R.color.widget_warn)
    val liveTx = ColorProvider(R.color.widget_live_tx)
    val danger = ColorProvider(R.color.widget_danger)
    val track = ColorProvider(R.color.widget_track)
    val bronze = ColorProvider(R.color.widget_bronze)
    val onBronze = ColorProvider(R.color.widget_on_bronze)
    val white = ColorProvider(R.color.widget_white)
}

/** The card every widget sits on; the drawable rounds it below Android 12 where cornerRadius is ignored. */
internal fun GlanceModifier.widgetCard(): GlanceModifier =
    appWidgetBackground().background(ImageProvider(R.drawable.widget_card)).cornerRadius(24.dp)

internal fun text(size: TextUnit, color: ColorProvider, weight: FontWeight = FontWeight.Normal, strike: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size,
        fontWeight = weight,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )

@Composable
internal fun Glyph(res: Int, size: Dp, tint: ColorProvider, description: String? = null) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.size(size),
    )
}

/**
 * The record disc in all four states. [diameter] is the disc itself; while live the clay halo adds
 * 5 dp around it, as drawn.
 */
@Composable
internal fun RecordDisc(snapshot: WidgetSnapshot, diameter: Dp, icon: Dp = diameter * 0.4f) {
    val context = LocalContext.current
    val mode = snapshot.recording.mode
    val (background, size) = when {
        mode == RecordingMode.Recording -> R.drawable.widget_disc_live to diameter + 10.dp
        mode == RecordingMode.Paused -> R.drawable.widget_disc_paused to diameter
        !snapshot.micAllowed -> R.drawable.widget_disc_sunk to diameter
        else -> R.drawable.widget_disc_bronze to diameter
    }
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(background)).clickable(recordAction(context, snapshot)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            mode == RecordingMode.Recording -> Box(
                modifier = GlanceModifier.size(diameter * 0.3f).background(ImageProvider(R.drawable.widget_stop_square)),
            ) {}
            mode == RecordingMode.Paused -> Glyph(R.drawable.ic_pause, icon, WidgetColors.onBronze, "Resume recording")
            !snapshot.micAllowed -> Glyph(R.drawable.ic_mic_off, icon, WidgetColors.tx3, "Microphone access needed, open Echo")
            else -> Glyph(R.drawable.ic_mic, icon, WidgetColors.onBronze, "Record")
        }
    }
}

/** Live elapsed time: a framework Chronometer ticks on its own, so the widget needs no per-second updates. */
@Composable
internal fun Elapsed(recording: WidgetRecording, large: Boolean) {
    if (recording.mode == RecordingMode.Recording) {
        AndroidRemoteViews(chronometer(LocalContext.current, recording.base, large))
    } else {
        Text(
            text = clock(recording.elapsedMs),
            style = text(if (large) 20.sp else 13.sp, WidgetColors.tx2, FontWeight.Medium),
        )
    }
}

private fun chronometer(context: Context, base: Long, large: Boolean) = RemoteViews(
    context.packageName,
    if (large) R.layout.widget_chronometer_large else R.layout.widget_chronometer_small,
).apply { setChronometer(R.id.chronometer, base, null, true) }
