package com.bantai

import android.content.Context
import com.bantai.pipeline.GabayPipeline
import com.bantai.pipeline.ScamPipeline
import com.bantay.app.ai.LiteRtEngine
import com.bantay.app.core.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Iisang Gemma para sa buong app: Scam Alert at Gabay ang gumagamit.
 * ponytail: nananatiling naka-load (~1.2 GB) habang buhay ang process; i-unload kapag matagal walang gamit
 * kung pinapatay ng low-memory killer sa 4 GB na phone.
 */
object Bantai {

    /** Main thread: dito tumatakbo ang overlay; ang inference ay nasa sariling scope ng engine. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var engine: LiteRtEngine? = null

    private fun engine(context: Context): LiteRtEngine =
        engine ?: LiteRtEngine(cacheDir = context.applicationContext.cacheDir.path).also { engine = it }

    /** Sinisimulan ang pag-load sa background. Ligtas tawagin nang paulit-ulit. */
    fun warmUp(context: Context) {
        val e = engine(context)
        if (e.state.value == EngineState.IDLE || e.state.value == EngineState.FAILED) {
            scope.launch { e.load() }
        }
    }

    fun scamPipeline(context: Context) = ScamPipeline(engine(context))

    fun gabayPipeline(context: Context) = GabayPipeline(engine(context))
}
