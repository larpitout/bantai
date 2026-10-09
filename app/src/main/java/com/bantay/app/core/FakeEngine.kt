package com.bantay.app.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pamalit sa totoong model para sa tests at sa mga lane na wala pang device. */
class FakeEngine(
    private val response: String = DEFAULT_RESPONSE,
    private val delayMs: Long = 2000,
) : LlmEngine {

    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state

    override suspend fun load() {
        _state.value = EngineState.LOADING
        delay(delayMs)
        _state.value = EngineState.READY
    }

    override suspend fun generate(prompt: String): String {
        check(_state.value == EngineState.READY) { "Engine not ready: ${_state.value}" }
        delay(delayMs)
        return response
    }

    override fun close() {
        _state.value = EngineState.IDLE
    }

    companion object {
        const val DEFAULT_RESPONSE =
            "HATOL: SCAM\n" +
                "DAHILAN: Humihingi ng pera mula sa bagong number.\n" +
                "GAWIN: Huwag magpadala. Tawagan muna ang kamag-anak sa dati niyang number."
    }
}
