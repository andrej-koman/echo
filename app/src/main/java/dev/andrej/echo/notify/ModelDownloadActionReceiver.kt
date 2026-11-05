package dev.andrej.echo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication

/** Pause/Cancel buttons on [ModelDownloadNotifier]'s progress notification. */
class ModelDownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as EchoApplication).container
        val modelId = intent.getStringExtra(ModelDownloadNotifier.EXTRA_MODEL_ID)
        when (intent.action) {
            ACTION_PAUSE -> container.cancelDownload(modelId)
            ACTION_CANCEL -> {
                container.cancelDownload(modelId)
                modelId?.let(container::deleteModel)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "dev.andrej.echo.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "dev.andrej.echo.action.CANCEL_DOWNLOAD"
    }
}
