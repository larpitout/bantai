package com.bantay.app.ai

import android.util.Log
import com.bantay.app.core.EngineState
import com.bantay.app.core.LlmEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    override suspend fun generate(prompt: String): String {
        val active = AtomicReference<Conversation?>()
        val abandoned = AtomicBoolean(false)
        // Hiwalay na scope: kapag nag-timeout ang tumawag, hindi napuputol sa gitna ang native inference.
        // Ang pagsara ng conversation habang tumatakbo pa ito ay nagse-segfault sa liblitertlm_jni.
        val job = scope.async {
            mutex.withLock {
                val e = checkNotNull(engine) { "Engine not ready: ${_state.value}" }
                if (abandoned.get()) throw CancellationException("Umalis na ang tumawag")
                // Bagong conversation kada tawag para walang naiiwang history.
                val conversation = e.createConversation(ConversationConfig(samplerConfig = SAMPLER))
                active.set(conversation)
                try {
                    val out = StringBuilder()
                    val start = System.currentTimeMillis()
                    conversation.sendMessageAsync(prompt).collect { out.append(it.toString()) }
                    // Para masukat ang bilis sa totoong phone (walang laman ng mensahe, sagot lang ng model).
                    Log.w(TAG, "generate ms=${System.currentTimeMillis() - start} promptChars=${prompt.length} answer=${out.toString().trim().replace('\n', '|').take(160)}")
                    out.toString().trim()
                } finally {
                    synchronized(active) {
                        active.set(null)
                        conversation.close()
                    }
                }
            }
        }
        try {
            return job.await()
        } catch (c: CancellationException) {
            abandoned.set(true)
            // Pinapahinto nang maayos ang inference para hindi maghintay nang matagal ang susunod na tawag.
            synchronized(active) { active.get()?.let { runCatching { it.cancelProcess() } } }
            throw c
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
