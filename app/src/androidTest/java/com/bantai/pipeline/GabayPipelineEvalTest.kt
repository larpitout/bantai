package com.bantai.pipeline

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bantai.model.ScreenContext
import com.bantai.rules.GabayParser
import com.bantai.rules.PromptBuilder
import com.bantay.app.ai.LiteRtEngine
import com.bantay.app.core.EngineState
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A7 tuning run for the Gabay prompt over a few fixed screens. Needs the model on the device:
 *   adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/
 * Results: adb logcat -s BantayEval
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.bantai.pipeline.GabayPipelineEvalTest
 *
 * Nothing here asserts on the answers; the replies and timings are logged as measured.
 */
@RunWith(AndroidJUnit4::class)
class GabayPipelineEvalTest {

    private class Case(val id: String, val question: String, val screen: ScreenContext)

    /** The real pipeline (20 s timeout, parser applied). */
    @Test
    fun evaluatePipeline() = withEngine { engine ->
        val pipeline = GabayPipeline(engine)
        val latenciesMs = mutableListOf<Long>()
        var answered = 0
        for (case in CASES) {
            var result: GabayResult? = null
            val ms = measureTimeMillis { result = pipeline.guide(case.question, case.screen, "Junjun") }
            val r = checkNotNull(result)
            latenciesMs += ms
            if (r.source == GabaySource.LLM) answered++
            Log.i(
                TAG,
                "gabay[${case.id}] source=${r.source} steps=${r.steps.size} ms=$ms " +
                    "sagot=${r.spokenText.replace('\n', '|')}",
            )
        }
        Log.i(
            TAG,
            "SUMMARY[gabay] llm_answers=$answered/${CASES.size} " +
                "avg_ms=${latenciesMs.average().toLong()} max_ms=${latenciesMs.maxOrNull() ?: 0}",
        )
    }

    /** The raw reply next to what the parser keeps, with a longer timeout, to see what the prompt really produces. */
    @Test
    fun evaluateRawReplies() = withEngine { engine ->
        for (case in CASES) {
            val labels = PromptBuilder.gabayLabels(case.screen.labels)
            if (labels.isEmpty()) continue
            val prompt = PromptBuilder.buildGabayPrompt(case.question, labels)
            Log.i(TAG, "raw[${case.id}] prompt=${prompt.replace('\n', '|')}")

            var raw: String? = null
            val ms = measureTimeMillis { raw = withTimeoutOrNull(RAW_TIMEOUT_MS) { engine.generate(prompt) } }
            val answer = raw
            val kept = answer?.let { GabayParser.parse(it, labels) }.orEmpty()
            Log.i(
                TAG,
                "raw[${case.id}] ms=$ms kept=${kept.size} sagot=${answer?.replace('\n', '|') ?: "TIMEOUT"}",
            )
        }
    }

    private fun withEngine(block: suspend (LiteRtEngine) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LiteRtEngine(cacheDir = context.cacheDir.path)
        Log.i(TAG, "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        val loadMs = measureTimeMillis { engine.load() }
        Log.i(TAG, "load_ms=$loadMs state=${engine.state.value}")
        assertEquals(EngineState.READY, engine.state.value)

        try {
            block(engine)
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val TAG = "BantayEval"
        const val RAW_TIMEOUT_MS = 60_000L

        val CASES = listOf(
            Case(
                "messenger-picture",
                "Paano mag-send ng picture?",
                ScreenContext(
                    "com.facebook.orca",
                    listOf("Junjun", "Active now", "Voice call", "Video call", "Camera", "Gallery", "Microphone", "Message", "Send"),
                ),
            ),
            Case(
                "messenger-call",
                "Paano tumawag kay Junjun?",
                ScreenContext(
                    "com.facebook.orca",
                    listOf("Junjun", "Active now", "Voice call", "Video call", "Camera", "Gallery", "Microphone", "Message", "Send"),
                ),
            ),
            Case(
                "settings-volume",
                "Paano lakasan ang tunog?",
                ScreenContext(
                    "com.android.settings",
                    listOf("Settings", "Search settings", "Network & internet", "Connected devices", "Apps", "Notifications", "Battery", "Sound & vibration", "Display"),
                ),
            ),
            Case(
                "facebook-post",
                "Paano mag-post ng picture?",
                ScreenContext(
                    "com.facebook.katana",
                    listOf("Home", "Friends", "Video", "Notifications", "Menu", "What's on your mind?", "Photo", "Search"),
                ),
            ),
            Case(
                "dialer-call",
                "Paano tumawag sa anak ko?",
                ScreenContext(
                    "com.google.android.dialer",
                    listOf("Search contacts", "Favorites", "Recents", "Contacts", "Keypad", "Voicemail"),
                ),
            ),
            Case(
                "home-facebook",
                "Bumalik sa Facebook",
                ScreenContext(
                    "com.android.launcher3",
                    listOf("Phone", "Messages", "Facebook", "Messenger", "Camera", "Gallery", "Settings"),
                ),
            ),
            Case("bank-no-labels", "Paano magpadala ng pera?", ScreenContext("com.bank.app", emptyList())),
        )
    }
}
