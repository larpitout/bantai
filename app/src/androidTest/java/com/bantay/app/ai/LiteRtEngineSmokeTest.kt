package com.bantay.app.ai

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bantay.app.core.EngineState
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A4 smoke test. Kailangan munang i-push ang model:
 *   adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/
 * Resulta: adb logcat -s BantaySmoke
 */
@RunWith(AndroidJUnit4::class)
class LiteRtEngineSmokeTest {

    @Test
    fun loadsAndAnswers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LiteRtEngine(cacheDir = context.cacheDir.path)
        Log.i(TAG, "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        val loadMs = measureTimeMillis { engine.load() }
        Log.i(TAG, "load_ms=$loadMs state=${engine.state.value}")
        assertEquals(EngineState.READY, engine.state.value)

        try {
            for ((i, case) in CASES.withIndex()) {
                val (sms, expected) = case
                Log.i(TAG, "generate[$i] sms=$sms expected=$expected")
                var answer = ""
                val ms = measureTimeMillis { answer = engine.generate(scamPrompt(sms)) }
                Log.i(TAG, "generate[$i] ms=$ms chars=${answer.length} answer=$answer")
                assertTrue("Blangko ang sagot sa case $i", answer.isNotBlank())
            }
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val TAG = "BantaySmoke"

        // SMS at inaasahang HATOL. Nilo-log lang ang inaasahan; hindi ina-assert dahil smoke test ito.
        val CASES = listOf(
            "Ma si Junjun to bagong number ko padala ka 5k gcash" to "SCAM",
            "Nay, pauwi na po ako, may gusto po ba kayong ipabili?" to "LIGTAS",
            "GCash: Na-hold ang account mo. I-verify agad sa gcash-verify.com para hindi ma-block." to "SCAM",
            "Lola, happy birthday po! Dadalaw po kami sa Linggo." to "LIGTAS",
        )

        fun scamPrompt(sms: String): String = """
Ikaw si Bantay, tagasuri ng text message para sa mga lolo at lola. Sabihin kung SCAM o LIGTAS ang mensahe.

SCAM kapag may alinman dito: humihingi ng pera o load, nagsasabing bagong number, may link, may premyo, humihingi ng OTP o PIN, o nagmamadali.
LIGTAS kapag karaniwang kumustahan o paalala lang at walang hinihinging pera, link, o code.

Sumagot nang eksakto sa ganitong anyo, tatlong linya lang:
HATOL: SCAM o LIGTAS
DAHILAN: isang maikling pangungusap
GAWIN: isang maikling payo

Mensahe: "Congrats! Nanalo ka ng P50,000 sa raffle. I-click ang bit.ly/claim para makuha."
HATOL: SCAM
DAHILAN: May premyo at link kahit wala kang sinalihang raffle.
GAWIN: Huwag i-click ang link at burahin ang mensahe.

Mensahe: "Lola, nandito na po kami sa bahay. Kain na po tayo mamaya."
HATOL: LIGTAS
DAHILAN: Karaniwang kumustahan lang at walang hinihinging pera o code.
GAWIN: Puwede pong sagutin gaya ng dati.

Mensahe: "BDO Advisory: Na-lock ang account mo. Ibigay ang OTP na matatanggap mo para ma-unlock agad."
HATOL: SCAM
DAHILAN: Humihingi ng OTP at nagmamadali; hindi ito ginagawa ng bangko.
GAWIN: Huwag ibigay ang OTP kahit kanino.

Mensahe: "Paalala po: may check-up kayo bukas ng 9am kay Dr. Santos."
HATOL: LIGTAS
DAHILAN: Paalala lang at walang hinihinging pera, link, o code.
GAWIN: Wala pong kailangang ikabahala.

Mensahe: "$sms"
""".trim()
    }
}
