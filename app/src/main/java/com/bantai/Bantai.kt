package com.bantai

import android.app.ActivityManager
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import com.bantai.pipeline.ScamPipeline
import com.bantai.service.NotificationExtractor
import com.bantai.service.Speaker
import com.bantay.app.ai.LiteRtEngine
import com.bantay.app.core.EngineState
import java.io.File
import java.util.Locale
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

    /**
     * Totoong RAM lang ang binibilang (hindi ang "virtual RAM" na swap sa storage).
     * Sa 4 GB na phone (TECNO, OPPO), pinapatay ng low-memory killer ang Bantai mismo kapag nilo-load si Gemma,
     * kaya mas ligtas ang rules-only doon: laging may bantay.
     * ponytail: isang threshold lang; sukatin sa mas maraming phone bago gawing mas pino.
     */
    fun hasRamForAi(context: Context): Boolean {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem >= MIN_AI_RAM_BYTES
    }

    private const val MIN_AI_RAM_BYTES = 5_500L * 1024 * 1024

    /** Sinisimulan ang pag-load sa background. Ligtas tawagin nang paulit-ulit. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        if (!hasRamForAi(app)) {
            Log.w(TAG, "Kulang ang RAM para kay Gemma: rules-only ang Scam Alert")
            return
        }
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

    /**
     * Context na sumusunod sa wikang pinili para sa app (Settings → Apps → Bantai → Language).
     * Mga Activity lang ang kusang sumusunod dito; ang babala ay galing sa service kaya kailangan ito.
     * Walang napili: Tagalog, dahil para kay Nanay ang mga babala.
     */
    fun localized(context: Context): Context {
        val chosen = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else LocaleList.getEmptyLocaleList()
        val locales = if (chosen.isEmpty) LocaleList(Locale("tl")) else chosen
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    /** Mga app na tinitingnan ng babala (pareho ng binabasang notification). */
    val MESSAGING_APPS: Set<String> = NotificationExtractor.ALLOWED_PACKAGES

    /** Na-flag na mensahe; ipapakita ang babala kapag nakita ito sa bukas na chat. */
    class Flagged(val key: String, val reason: String, val action: String, var fromAi: Boolean) {
        var dismissed = false
    }

    // ponytail: nasa memory lang (huling 20); mawawala kapag na-restart ang app. I-save kung kailangang tumagal.
    private val flagged = ArrayDeque<Flagged>()

    private fun keyOf(text: String) = text.lowercase().replace(Regex("\\s+"), " ").trim().take(40)

    /** Itabi o i-update ang babala para sa [message]. */
    fun flag(message: String, reason: String, action: String, fromAi: Boolean): Flagged {
        val key = keyOf(message)
        flagged.firstOrNull { it.key == key }?.let { it.fromAi = it.fromAi || fromAi; return it }
        if (flagged.size >= 20) flagged.removeFirst()
        return Flagged(key, reason, action, fromAi).also(flagged::addLast)
    }

    /** Ang na-flag na mensaheng nakikita sa screen ngayon, kung meron. */
    fun flaggedOnScreen(texts: List<String>): Flagged? {
        if (flagged.isEmpty() || texts.isEmpty()) return null
        val screen = texts.map(::keyOf)
        return flagged.lastOrNull { f -> !f.dismissed && screen.any { f.key in it || (it.length >= 20 && it in f.key) } }
    }

    private var speaker: Speaker? = null

    /** Iisang TextToSpeech para sa babala. */
    fun speaker(context: Context): Speaker =
        speaker ?: Speaker(context.applicationContext).also { speaker = it }

    fun scamPipeline(context: Context) = ScamPipeline(engine(context))

}
