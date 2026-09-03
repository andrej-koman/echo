package dev.andrej.echo.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.delay

/**
 * Gemini Nano through AICore. The weights are part of the system image, so there is nothing to
 * ship and nothing for the user to download — but only a published list of devices carries the
 * Prompt feature at all.
 */
class MlKitLlmRunner(
    private val model: GenerativeModel = Generation.getClient(),
) : LlmRunner {

    override suspend fun availability(): LlmAvailability = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> LlmAvailability.Ready
            FeatureStatus.DOWNLOADABLE -> LlmAvailability.NeedsDownload(bytes = 0)
            FeatureStatus.DOWNLOADING -> LlmAvailability.Downloading(fraction = 0f)
            else -> LlmAvailability.Unsupported("AICore reports the feature unavailable")
        }
    } catch (e: GenAiException) {
        // Devices off the supported list throw here rather than returning UNAVAILABLE. Catching
        // this is what lets an unsupported phone fall through to the next runner instead of
        // crashing: a Galaxy S24 has AICore but not the Prompt feature, and reports
        // "606-FEATURE_NOT_FOUND: Feature 636 is not available".
        Log.i(TAG, "Gemini Nano unavailable on this device: ${e.message}")
        LlmAvailability.Unsupported(e.message ?: "AICore rejected the request")
    }

    override suspend fun warmup() {
        try {
            model.warmup()
        } catch (e: GenAiException) {
            Log.w(TAG, "warmup failed, the first call will be slow: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String, temperature: Float): String {
        val request = generateContentRequest(TextPart(prompt)) {
            this.temperature = temperature
            topK = TOP_K
        }

        repeat(BUSY_ATTEMPTS) { attempt ->
            try {
                val response = model.generateContent(request)
                return response.candidates.firstOrNull()?.text.orEmpty()
            } catch (e: GenAiException) {
                // BUSY is another app's inference finishing, so it is worth waiting out. A quota
                // or background block is not — those throw straight through and the caller
                // degrades to a transcript with no analysis.
                if (e.errorCode != GenAiException.ErrorCode.BUSY) throw e
                if (attempt == BUSY_ATTEMPTS - 1) throw e
                delay(BUSY_BACKOFF_MS shl attempt)
            }
        }

        error("unreachable: the retry loop either returns or throws")
    }

    private companion object {
        const val TAG = "MlKitLlmRunner"
        const val TOP_K = 10
        const val BUSY_ATTEMPTS = 3
        const val BUSY_BACKOFF_MS = 250L
    }
}
