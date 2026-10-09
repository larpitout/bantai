package com.bantay.app.core

data class RuleResult(val score: Int, val signals: List<String>)

data class ScamVerdict(val isScam: Boolean, val dahilan: String, val gawin: String)

data class ScreenContext(val appName: String, val labels: List<String>)
