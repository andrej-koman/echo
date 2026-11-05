package dev.andrej.echo.notify

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.MainActivity
import dev.andrej.echo.data.NewTodo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule

class TaskReminderDeliveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun alarmPostsTaskAndCompletionRemovesNotification() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        if (Build.VERSION.SDK_INT >= 33) {
            val result = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            )
            ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
        }
        val container = (context.applicationContext as EchoApplication).container
        val id = "reminder-device-test"
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            container.finishOnboarding()
            container.repository.save(id, "Call the dentist", "en", 0, System.currentTimeMillis(), null, false)
            container.derived.replaceFor(id, listOf(NewTodo("Call the dentist", System.currentTimeMillis() + 3_000, true, true)))
            val task = container.derived.items.first().single { it.sourceTranscriptId == id }
            container.taskReminders.reconcile(force = true)
            val posted = withTimeout(90_000) {
                var notification = manager.activeNotifications.firstOrNull { it.tag == "task:${task.id}" }
                while (notification == null) {
                    delay(200)
                    notification = manager.activeNotifications.firstOrNull { it.tag == "task:${task.id}" }
                }
                notification
            }
            assertEquals("Call the dentist", posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertNotNull(posted.notification.contentIntent)
            posted.notification.contentIntent.send()
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching { compose.onNodeWithText("Edit task").fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithText("Edit task").assertIsDisplayed()
            assertTrue(container.derived.items.first().single { it.id == task.id }.reminderDelivered)
            container.taskReminders.reconcile(force = true)
            assertEquals(1, manager.activeNotifications.count { it.tag == "task:${task.id}" })
            container.derived.setDone(task.id, true)
            container.taskReminders.reconcile()
            assertFalse(manager.activeNotifications.any { it.tag == "task:${task.id}" })
        } finally {
            container.derived.deleteFor(id)
            container.repository.delete(id)
            container.taskReminders.reconcile()
        }
    }
}
