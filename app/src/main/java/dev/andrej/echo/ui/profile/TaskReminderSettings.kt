package dev.andrej.echo.ui.profile

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.notify.AndroidTaskReminderDelivery

@Composable
fun TaskReminderSettings() {
    val context = LocalContext.current
    val alarms = context.getSystemService(AlarmManager::class.java)
    val notifications = context.getSystemService(NotificationManager::class.java)
    fun notificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        notifications.getNotificationChannel(AndroidTaskReminderDelivery.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    fun exactEnabled() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    var enabled by remember { mutableStateOf(notificationsEnabled()) }
    var exact by remember { mutableStateOf(exactEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = notificationsEnabled()
        exact = exactEnabled()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notificationsEnabled()
    }
    val openNotifications = {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !(context.getSharedPreferences("task_notifications", android.content.Context.MODE_PRIVATE).getBoolean("permission_requested", false))
        ) {
            context.getSharedPreferences("task_notifications", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("permission_requested", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidTaskReminderDelivery.CHANNEL_ID))
        }
    }
    Section(
        title = "TASK REMINDERS",
        rows = listOf(
            {
                // Android owns this switch; tapping it asks for permission or opens the channel settings.
                SwitchSettingsRow(
                    iconRes = R.drawable.ic_bell,
                    label = "Notifications",
                    subtitle = "When a task has a time",
                    checked = enabled,
                    onCheckedChange = { openNotifications() },
                )
            },
        ),
        footer = if (exact) null else {
            {
                ExactAlarmWarning(onAllow = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    }
                })
            }
        },
    )
}

@Composable
private fun ExactAlarmWarning(onAllow: () -> Unit) {
    val foreground = EchoTheme.colors.statusWarningFg
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EchoTheme.colors.statusWarningBg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alarm_clock),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Reminders may arrive late", style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold), color = foreground)
            Text(
                "Android can hold them back unless Echo is allowed to set exact alarms.",
                style = EchoTheme.typography.caption,
                color = foreground,
            )
        }
        Text(
            "Allow",
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = foreground,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clip(CircleShape)
                .border(1.5.dp, foreground, CircleShape)
                .clickable(onClick = onAllow)
                .heightIn(min = 34.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
