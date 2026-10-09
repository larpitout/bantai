package com.bantai.pipeline

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bantai.data.TestCase
import com.bantai.data.TestDataset
import com.bantai.rules.PromptBuilder
import com.bantay.app.ai.LiteRtEngine
import com.bantay.app.core.EngineState
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A6 evaluation over the 30 ground-truth messages. Needs the model on the device:
 *   adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/
 * Results: adb logcat -s BantayEval
 * Model only:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.bantai.pipeline.ScamPipelineEvalTest#evaluateModelOnly
 *
 * Nothing here asserts an accuracy threshold; the numbers are logged as measured.
 */
@RunWith(AndroidJUnit4::class)
class ScamPipelineEvalTest {

    private class Tally(val name: String) {
        var correct = 0
        var falsePositives = 0
        var falseNegatives = 0
        var llmAnswers = 0
        var fallbacks = 0
        var noFormat = 0
        val latenciesMs = mutableListOf<Long>()

        fun record(case: TestCase, isScam: Boolean) {
            when {
                isScam == case.isScam -> correct++
                isScam -> falsePositives++
                else -> falseNegatives++
            }
        }

        fun log(total: Int) {
            val avg = if (latenciesMs.isEmpty()) 0 else latenciesMs.average().toLong()
            Log.i(
                TAG,
                "SUMMARY[$name] accuracy=$correct/$total false_pos=$falsePositives false_neg=$falseNegatives " +
                    "llm_answers=$llmAnswers no_format=$noFormat fallbacks=$fallbacks avg_ms=$avg max_ms=${latenciesMs.maxOrNull() ?: 0}",
            )
        }
    }

    /** The real pipeline (rules + model, 15 s timeout). */
    @Test
    fun evaluatePipeline() = withEngine { engine ->
        val cases = TestDataset.messages
        val pipeline = ScamPipeline(engine)
        val tally = Tally("pipeline")
        for (case in cases) {
            var final: ScamCheck? = null
            val ms = measureTimeMillis { final = pipeline.check(case.text, case.sender).toList().last() }
            val result = checkNotNull(final)
            tally.record(case, result.verdict.isScam)
            if (result.rule.score > 0) {
                tally.latenciesMs += ms
                if (result.source == VerdictSource.LLM) tally.llmAnswers++ else tally.fallbacks++
            }
            Log.i(
                TAG,
                "pipeline[${case.id}] expected=${label(case.isScam)} got=${label(result.verdict.isScam)} " +
                    "score=${result.rule.score} source=${result.source} ms=$ms dahilan=${result.verdict.dahilan}",
            )
        }
        tally.log(cases.size)
    }

    /**
     * The model alone on every message: no rules, no rule hints, no fallback.
     * The verdict is read only from the HATOL line; a reply without one counts as WALANG_FORMAT, not as a guess.
     */
    @Test
    fun evaluateModelOnly() = withEngine { engine ->
        val cases = TestDataset.messages
        val tally = Tally("llm")
        for (case in cases) {
            val prompt = PromptBuilder.buildScamPrompt(case.text)
            // The full prompt is the same around every message, so it is logged once.
            if (case === cases.first()) Log.i(TAG, "llm prompt=${prompt.replace('\n', '|')}")
            Log.i(TAG, "llm[${case.id}] tanong=${case.text.replace('\n', ' ')}")

            var raw: String? = null
            val ms = measureTimeMillis {
                raw = withTimeoutOrNull(MODEL_ONLY_TIMEOUT_MS) { engine.generate(prompt) }
            }
            tally.latenciesMs += ms
            val answer = raw
            val isScam = answer?.let(::strictVerdict)
            when {
                answer == null -> tally.fallbacks++
                isScam == null -> tally.noFormat++
                else -> {
                    tally.llmAnswers++
                    tally.record(case, isScam)
                }
            }
            val hatol = when {
                answer == null -> "TIMEOUT"
                isScam == null -> "WALANG_FORMAT"
                else -> label(isScam)
            }
            Log.i(
                TAG,
                "llm[${case.id}] expected=${label(case.isScam)} hatol=$hatol ms=$ms " +
                    "sagot=${answer?.replace('\n', '|') ?: "-"}",
            )
        }
        tally.log(cases.size)
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
        const val MODEL_ONLY_TIMEOUT_MS = 30_000L

        private val HATOL_LINE = Regex("^\\s*(?:HATOL|VERDICT)\\s*:(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        fun label(isScam: Boolean) = if (isScam) "SCAM" else "LIGTAS"

        /** Null when there is no HATOL line or it does not name exactly one verdict. */
        fun strictVerdict(raw: String): Boolean? {
            val hatol = HATOL_LINE.find(raw)?.groupValues?.get(1)?.uppercase() ?: return null
            val scam = "SCAM" in hatol
            val safe = "LIGTAS" in hatol || "SAFE" in hatol
            return if (scam == safe) null else scam
        }
    }
}
