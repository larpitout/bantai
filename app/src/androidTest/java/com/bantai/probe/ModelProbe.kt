package com.bantai.probe

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bantay.app.ai.LiteRtEngine
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

// Pansamantala (hindi ico-commit): Qwen3.5-2B vs Gemma 1B sa mga alanganing message, gamit ang LiteRtEngine ng app.
@RunWith(AndroidJUnit4::class)
class ModelProbe {
    private val cases = listOf(
        "Nay nakuha mo na ba yung pinadala ko sa gcash?" to false,
        "Ma nagbayad na ako ng kuryente kanina" to false,
        "Lola yung load mo naubos na po ba? lalagyan ko po mamaya" to false,
        "Pa yung pera para sa tuition naipadala ko na sa bank mo" to false,
        "Ma pa-load naman 500 sa number na to, emergency lang" to true,
        "Your package is on hold. Update address: jnt-delivery.top" to true,
        "Pautang muna 2k ibabalik ko bukas promise send mo sa gcash ko" to true,
        "Account mo ay na-suspend. I-reply ang PIN mo para ma-activate" to true,
    )

    private fun prompt(msg: String) = """Is this text message a scam? A scam asks the reader to send money or load, share an OTP or PIN, or open a suspicious link. A message that only says someone already sent or paid something is not a scam.

Message: "${msg.replace('"', '\'')}"

Answer with one word: SCAM or SAFE."""

    private fun explain(msg: String) = """Ipaliwanag kay Lola sa simpleng Tagalog, sa isang maikling pangungusap, kung bakit mukhang scam ang mensaheng ito at kung ano ang dapat niyang gawin.

Mensahe: "${msg.replace('"', '\'')}""""

    @Test fun compare() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("model") ?: LiteRtEngine.DEFAULT_MODEL_PATH
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LiteRtEngine(modelPath = path, cacheDir = ctx.cacheDir.path)
        val loadMs = measureTimeMillis { engine.load() }
        Log.i(TAG, "model=${path.substringAfterLast('/')} load_ms=$loadMs state=${engine.state.value}")
        var ok = 0
        var total = 0L
        for ((msg, scam) in cases) {
            var out = ""
            val ms = measureTimeMillis { out = engine.generate(prompt(msg)) }
            total += ms
            val said = out.uppercase().contains("SCAM")
            if (said == scam) ok++
            Log.i(TAG, "${if (said == scam) "OK " else "BAD"} want=${if (scam) "SCAM" else "SAFE"} got=${out.trim().take(20).replace('\n', ' ')} ${ms}ms")
        }
        Log.i(TAG, "RESULT $ok/${cases.size} avg_ms=${total / cases.size}")
        var out = ""
        val ms = measureTimeMillis { out = engine.generate(explain(cases[6].first)) }
        Log.i(TAG, "EXPLAIN ${ms}ms: ${out.trim().replace('\n', ' ')}")
        engine.close()
    }

    companion object { const val TAG = "ModelProbe" }
}
