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
import dev.andrej.echo.R
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
    Section(
        title = "TASK REMINDERS",
        rows = listOf(
            {
                DisclosureRow(
                    iconRes = R.drawable.ic_bell,
                    label = "Notifications",
                    value = if (enabled) "On" else "Off",
                    onClick = {
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
                    },
                )
            },
            {
                DisclosureRow(
                    iconRes = R.drawable.ic_clock,
                    label = "Reminder timing",
                    value = if (exact) "Exact" else "May be delayed",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}")))
                        }
                    },
                )
            },
        ),
    )
}
