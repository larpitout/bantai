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
 * Iisang on-device model para sa buong app (Qwen o Gemma, ayon sa RAM): Scam Alert ang gumagamit.
 * ponytail: nananatiling naka-load habang buhay ang process; i-unload kapag matagal walang gamit
 * kung pinapatay ng low-memory killer.
 */
object Bantai {

    private const val TAG = "Bantai"
    /**
     * Mga model ayon sa una sa pipiliin: Qwen3.5-2B (mas matalino, ~2 GB) kapag kaya ng RAM, saka Gemma 3 1B.
     * Ang bawat isa ay may sariling minimum na totoong RAM; kulang sa lahat → rules-only.
     */
    private class Model(val file: String, val minRamMb: Long)

    private val MODELS = listOf(
        Model("Qwen3.5-2B_int8.litertlm", 6_000),
        Model("gemma3-1b-it-int4.litertlm", 5_500),
    )

    private const val DEV_MODEL_DIR = "/data/local/tmp/llm"

    /** Main thread: dito tumatakbo ang overlay; ang inference ay nasa sariling scope ng engine. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var engine: LiteRtEngine? = null

    /** Ang model na gagamitin sa phone na ito, o null kung rules-only. */
    private var chosen: Model? = null

    private fun totalRamMb(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    private fun isBundled(context: Context, m: Model) = context.assets.list("")?.contains(m.file) == true

    /** Release: model na kasama sa APK (kinokopya sa filesDir). Debug: model na in-adb push. */
    private fun pathOf(context: Context, m: Model): String? = when {
        isBundled(context, m) -> File(context.filesDir, m.file).path
        File(DEV_MODEL_DIR, m.file).canRead() -> File(DEV_MODEL_DIR, m.file).path
        else -> null
    }

    private fun pickModel(context: Context): Model? {
        val ram = totalRamMb(context)
        return MODELS.firstOrNull { ram >= it.minRamMb && pathOf(context, it) != null }
    }

    private fun engine(context: Context, m: Model): LiteRtEngine = engine ?: run {
        val app = context.applicationContext
        LiteRtEngine(modelPath = pathOf(app, m)!!, cacheDir = app.cacheDir.path).also { engine = it }
    }

    /**
     * Totoong RAM lang ang binibilang (hindi ang "virtual RAM" na swap sa storage).
     * Sa 4 GB na phone (TECNO, OPPO), pinapatay ng low-memory killer ang Bantai mismo kapag may model,
     * kaya rules-only doon: laging may bantay.
     */
    fun hasRamForAi(context: Context): Boolean = totalRamMb(context) >= MODELS.minOf { it.minRamMb }

    /** Pangalan ng model na tumatakbo, para sa logs at dashboard. */
    fun modelName(): String? = chosen?.file?.substringBefore('_')

    /** Sinisimulan ang pag-load sa background. Ligtas tawagin nang paulit-ulit. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        val m = chosen ?: pickModel(app)
        if (m == null) {
            Log.w(TAG, "Walang model na kasya sa RAM (${totalRamMb(app)} MB): rules-only ang Scam Alert")
            return
        }
        chosen = m
        val e = engine(app, m)
        if (e.state.value != EngineState.IDLE && e.state.value != EngineState.FAILED) return
        Log.w(TAG, "Model: ${m.file} (RAM ${totalRamMb(app)} MB)")
        scope.launch {
            if (isBundled(app, m)) {
                // Hal. puno ang storage: tuloy pa rin, FAILED ang engine at rules-only ang Scam Alert.
                runCatching { withContext(Dispatchers.IO) { copyBundledModel(app, m) } }
                    .onFailure { Log.e(TAG, "Hindi nakopya ang model", it) }
            }
            e.load()
            Log.w(TAG, "Model ${m.file}: ${e.state.value}")
        }
    }

    /**
     * Kailangan ng LiteRT ng totoong file path, kaya kinokopya ang model mula sa APK isang beses.
     * ponytail: dodoble ang storage (APK + kopya); i-download na lang sa setup kapag lumipat sa Play Store.
     */
    private fun copyBundledModel(context: Context, m: Model) {
        val target = File(context.filesDir, m.file)
        val expected = context.assets.openFd(m.file).use { it.length }
        if (target.length() == expected) return

        Log.i(TAG, "Kinokopya ang model mula sa APK ($expected bytes)")
        val tmp = File(target.path + ".part")
        context.assets.open(m.file).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
        check(tmp.renameTo(target)) { "Hindi mailipat ang model sa $target" }
    }

    /**
     * Context na sumusunod sa wikang pinili para sa app (Settings → Apps → Bantai → Language).
     * Mga Activity lang ang kusang sumusunod dito; ang babala ay galing sa service kaya kailangan ito.
     * Walang napili: English. Tagalog kapag iyon ang pinili sa Language ng app.
     */
    fun localized(context: Context): Context {
        val chosen = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else LocaleList.getEmptyLocaleList()
        val locales = if (chosen.isEmpty) LocaleList(Locale.ENGLISH) else chosen
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    /** Mga app na tinitingnan ng babala (pareho ng binabasang notification). */
    val MESSAGING_APPS: Set<String> = NotificationExtractor.ALLOWED_PACKAGES

    /** Na-flag na mensahe; ipapakita ang babala kapag nakita ito sa bukas na chat. */
    class Flagged(
        val key: String, val reason: String, val action: String, var fromAi: Boolean, val message: String, val signals: List<String>,
        /** Bilang ng senyales mula sa rules: 3+ mataas, 2 katamtaman, 1 mababa. */
        val score: Int,
    ) {
        var dismissed = false
    }

    // ponytail: nasa memory lang (huling 20); mawawala kapag na-restart ang app. I-save kung kailangang tumagal.
    private val flagged = ArrayDeque<Flagged>()

    private fun keyOf(text: String) = text.lowercase().replace(Regex("\\s+"), " ").trim().take(40)

    /** Itabi o i-update ang babala para sa [message]. */
    fun flag(message: String, reason: String, action: String, fromAi: Boolean, signals: List<String>, score: Int): Flagged {
        val key = keyOf(message)
        flagged.firstOrNull { it.key == key }?.let { it.fromAi = it.fromAi || fromAi; return it }
        if (flagged.size >= 20) flagged.removeFirst()
        return Flagged(key, reason, action, fromAi, message, signals, score).also(flagged::addLast)
    }

    /** Ang na-flag na mensaheng nakikita sa screen ngayon, kung meron. */
    fun flaggedOnScreen(texts: List<String>): Flagged? {
        if (flagged.isEmpty() || texts.isEmpty()) return null
        return flagged.lastOrNull { f -> !f.dismissed && texts.any { matches(f, it) } }
    }

    /** Ito ba ang bubble ng na-flag na mensahe? (pareho ng [flaggedOnScreen]) */
    fun matches(f: Flagged, text: String): Boolean {
        val t = keyOf(text)
        return f.key in t || (t.length >= 20 && t in f.key)
    }

    private var speaker: Speaker? = null

    /** Iisang TextToSpeech para sa babala. */
    fun speaker(context: Context): Speaker =
        speaker ?: Speaker(context.applicationContext).also { speaker = it }

    /** Walang model (rules-only): engine na hindi kailanman READY, kaya rules ang laging sagot. */
    fun scamPipeline(context: Context, allowAiDowngrade: Boolean = false) = ScamPipeline(
        engine ?: LiteRtEngine(modelPath = "", cacheDir = null),
        language = if (localized(context).resources.configuration.locales[0].language in setOf("tl", "fil")) "Filipino" else "English",
        allowAiDowngrade = allowAiDowngrade,
    )

}
