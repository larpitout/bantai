package com.bantai.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class Speaker(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "BantaiSpeaker"
        private const val SENIOR_SPEECH_RATE = 0.9f
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val pendingUtteranceCallbacks = mutableMapOf<String, () -> Unit>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val filLocale = Locale("fil", "PH")
            val langResult = tts?.setLanguage(filLocale)

            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Filipino TTS voice not available offline, falling back to English (US)")
                tts?.setLanguage(Locale.US)
            } else {
                Log.i(TAG, "Initialized TextToSpeech with Filipino (fil-PH)")
            }

            tts?.setSpeechRate(SENIOR_SPEECH_RATE)
            isInitialized = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtteranceCallbacks.remove(id)?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { id ->
                        pendingUtteranceCallbacks.remove(id)
                    }
                }
            })
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech engine with status $status")
            isInitialized = false
        }
    }

    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "Cannot speak: TextToSpeech is not yet initialized")
            onDone?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            pendingUtteranceCallbacks[utteranceId] = onDone
        }

        tts?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        pendingUtteranceCallbacks.clear()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }
}
