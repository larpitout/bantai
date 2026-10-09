package com.bantai

import android.content.Context
import android.util.Log
import com.bantai.pipeline.ScamPipeline
import com.bantai.service.Speaker
import com.bantay.app.ai.LiteRtEngine
import com.bantay.app.core.EngineState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Iisang Gemma para sa buong app: Scam Alert ang gumagamit.
 * ponytail: nananatiling naka-load (~575 MB) habang buhay ang process; i-unload kapag matagal walang gamit
 * kung pinapatay ng low-memory killer sa 4 GB na phone.
 */
object Bantai {

    private const val TAG = "Bantai"
    private const val MODEL_ASSET = "gemma3-1b-it-int4.litertlm"

    /** Main thread: dito tumatakbo ang overlay; ang inference ay nasa sariling scope ng engine. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var engine: LiteRtEngine? = null

    private fun hasBundledModel(context: Context) =
        context.assets.list("")?.contains(MODEL_ASSET) == true

    private fun bundledModelFile(context: Context) = File(context.filesDir, MODEL_ASSET)

    private fun engine(context: Context): LiteRtEngine = engine ?: run {
        val app = context.applicationContext
        // Release: model na kasama sa APK. Debug: model na in-adb push.
        val path = if (hasBundledModel(app)) bundledModelFile(app).path else LiteRtEngine.DEFAULT_MODEL_PATH
        LiteRtEngine(modelPath = path, cacheDir = app.cacheDir.path).also { engine = it }
    }

    /** Sinisimulan ang pag-load sa background. Ligtas tawagin nang paulit-ulit. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        val e = engine(app)
        if (e.state.value != EngineState.IDLE && e.state.value != EngineState.FAILED) return
        scope.launch {
            if (hasBundledModel(app)) {
                // Hal. puno ang storage: tuloy pa rin, FAILED ang engine at rules-only ang Scam Alert.
                runCatching { withContext(Dispatchers.IO) { copyBundledModel(app) } }
                    .onFailure { Log.e(TAG, "Hindi nakopya ang model", it) }
            }
            e.load()
        }
    }

    /**
     * Kailangan ng LiteRT ng totoong file path, kaya kinokopya ang model mula sa APK isang beses.
     * ponytail: dodoble ang storage (APK + kopya, ~1.2 GB); i-download na lang sa setup kapag lumipat sa Play Store.
     */
    private fun copyBundledModel(context: Context) {
        val target = bundledModelFile(context)
        val expected = context.assets.openFd(MODEL_ASSET).use { it.length }
        if (target.length() == expected) return

        Log.i(TAG, "Kinokopya ang model mula sa APK ($expected bytes)")
        val tmp = File(target.path + ".part")
        context.assets.open(MODEL_ASSET).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
        check(tmp.renameTo(target)) { "Hindi mailipat ang model sa $target" }
    }

    private var speaker: Speaker? = null

    /** Iisang TextToSpeech para sa babala. */
    fun speaker(context: Context): Speaker =
        speaker ?: Speaker(context.applicationContext).also { speaker = it }

    fun scamPipeline(context: Context) = ScamPipeline(engine(context))

}
