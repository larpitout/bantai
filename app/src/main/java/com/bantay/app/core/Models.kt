package com.bantay.app.core

data class RuleResult(val score: Int, val signals: List<String>)

data class ScamVerdict(val isScam: Boolean, val dahilan: String, val gawin: String)
