package dev.andrej.echo.data

class RecordingNotificationScheduler : NotificationScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override fun schedule(item: TodoItem) {
        scheduled.add(item.id)
        cancelled.remove(item.id)
    }

    override fun cancel(itemId: String) {
        cancelled.add(itemId)
        scheduled.remove(itemId)
    }
}
