package com.bantay.app.ai

import android.util.Log
import com.bantay.app.core.EngineState
import com.bantay.app.core.LlmEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Gemma 3 1B sa LiteRT-LM, CPU backend. Isang inference lang sabay-sabay (Mutex). */
class LiteRtEngine(
    private val modelPath: String = DEFAULT_MODEL_PATH,
    private val cacheDir: String? = null,
) : LlmEngine {

    private val mutex = Mutex()
    private var engine: Engine? = null

    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state

    override suspend fun load() = mutex.withLock {
        if (engine != null) return@withLock
        _state.value = EngineState.LOADING
        try {
            withContext(Dispatchers.IO) {
                require(File(modelPath).canRead()) { "Hindi mabasa ang model: $modelPath" }
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir,
                )
                engine = Engine(config).also { it.initialize() }
            }
            _state.value = EngineState.READY
        } catch (t: Throwable) {
            Log.e(TAG, "Model load failed", t)
            engine = null
            _state.value = EngineState.FAILED
        }
    }

    override suspend fun generate(prompt: String): String = mutex.withLock {
        val e = checkNotNull(engine) { "Engine not ready: ${_state.value}" }
        withContext(Dispatchers.Default) {
            // Bagong conversation kada tawag para walang naiiwang history.
            e.createConversation(ConversationConfig(samplerConfig = SAMPLER)).use { conversation ->
                val out = StringBuilder()
                conversation.sendMessageAsync(prompt).collect { out.append(it.toString()) }
                out.toString().trim()
            }
        }
    }

    override fun close() {
        engine?.close()
        engine = null
        _state.value = EngineState.IDLE
    }

    companion object {
        private const val TAG = "LiteRtEngine"
        const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/gemma3-1b-it-int4.litertlm"

        // Mababa ang temperature para konsistent ang HATOL/DAHILAN/GAWIN format.
        private val SAMPLER = SamplerConfig(topK = 10, topP = 0.9, temperature = 0.2)
    }
}
