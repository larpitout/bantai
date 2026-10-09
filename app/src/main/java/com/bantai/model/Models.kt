package com.bantai.model

import kotlinx.coroutines.flow.StateFlow

enum class EngineState {
    LOADING,
    READY,
    FAILED
}

interface LlmEngine {
    val state: StateFlow<EngineState>
    suspend fun generate(system: String, user: String, maxTokens: Int): String
}

data class RuleResult(
    val score: Int,
    val signals: List<String>
)

data class ScamVerdict(
    val isScam: Boolean,
    val reason: String,
    val action: String
) {
    val dahilan: String get() = reason
    val gawin: String get() = action
}

data class ScreenContext(
    val appName: String,
    val labels: List<String>
)
