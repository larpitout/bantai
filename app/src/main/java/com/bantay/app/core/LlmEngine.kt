package com.bantay.app.core

import kotlinx.coroutines.flow.StateFlow

enum class EngineState { IDLE, LOADING, READY, FAILED }

/** Kontrata ng local LLM. Ang mga pipeline ay dito lang kakapit, hindi sa LiteRT mismo. */
interface LlmEngine {
    val state: StateFlow<EngineState>

    /** Nilo-load ang model. Hindi nagta-throw; tingnan ang [state] (READY o FAILED). */
    suspend fun load()

    /** Isang prompt, isang buong sagot. Nagta-throw kapag hindi READY o pumalya ang inference. */
    suspend fun generate(prompt: String): String

    fun close()
}
