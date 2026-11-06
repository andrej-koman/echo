package dev.andrej.echo.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CancellationException

/**
 * Onboarding's "Download" button as a job, so Wi-Fi-only downloads wait for a network and survive
 * the app being killed. Partial files resume; a failed model is retried a few times, then the job
 * fails and onboarding offers Retry.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as EchoApplication).container
        return try {
            if (container.downloadSetupModels()) {
                Result.success()
            } else if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "setup download failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "onboarding-downloads"
        private const val TAG = "ModelDownloadWorker"
        private const val MAX_RETRIES = 3
    }
}
