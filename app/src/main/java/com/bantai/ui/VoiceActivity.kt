package com.bantai.ui

import android.app.Activity
import android.os.Bundle
import com.bantai.service.VoiceInput

/**
 * Walang itsurang activity para sa pakikinig. Hindi binibigyan ng Android ng mic ang app na nasa background
 * (ang chathead ay overlay lang), kaya sandaling nasa harap ang Bantai habang nakikinig, tapos sarado agad.
 */
class VoiceActivity : Activity() {

    /** Mga callback mula sa [GabayOverlay]; isang pakikinig lang sabay-sabay. */
    class Request(val onPartial: (String) -> Unit, val onText: (String) -> Unit, val onFail: (Int) -> Unit)

    private val voice by lazy { VoiceInput(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = pending ?: return finish()
        pending = null
        voice.listen(
            onPartial = req.onPartial,
            onText = { finish(); req.onText(it) },
            onFail = { finish(); req.onFail(it) },
        )
    }

    override fun onDestroy() {
        voice.stop()
        super.onDestroy()
    }

    companion object {
        var pending: Request? = null
    }
}
