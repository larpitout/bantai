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

/** SUSPICIOUS: kakilala (nasa contacts) na humihingi ng pera. Hindi tinatawag na scam, pero pinapa-verify. */
enum class Verdict {
    SCAM,
    SUSPICIOUS,
    SAFE
}

data class ScamVerdict(
    val isScam: Boolean,
    val reason: String,
    val action: String,
    val level: Verdict = if (isScam) Verdict.SCAM else Verdict.SAFE
) {
    val dahilan: String get() = reason
    val gawin: String get() = action
}
