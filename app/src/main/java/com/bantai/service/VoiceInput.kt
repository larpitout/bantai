package com.bantai.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Pakikinig kay Nanay (speech-to-text), sa phone mismo kapag may offline na Filipino.
 * Isang tanong bawat tawag. Tawagin sa main thread.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /** [onText] kapag may narinig; [onFail] kapag wala o pumalya (walang mic permission, walang boses, atbp.). */
    fun listen(onPartial: (String) -> Unit, onText: (String) -> Unit, onFail: (Int) -> Unit, language: String = workingLanguage) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return onFail(SpeechRecognizer.ERROR_CLIENT)

        // Ang on-device recognizer (Android System Intelligence) ay "Input streams unavailable" sa TECNO;
        // ang default na Speech Services by Google ang gamit, offline pa rin kapag may language pack (EXTRA_PREFER_OFFLINE).
        val onDevice = false
        val r = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        Log.i(TAG, "listen onDevice=$onDevice lang=$language")
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                stop()
                if (text.isBlank()) onFail(SpeechRecognizer.ERROR_NO_MATCH) else onText(text)
            }

            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(onPartial)
            }

            override fun onError(error: Int) {
                Log.e(TAG, "error=$error lang=$language")
                stop()
                // Walang offline na Filipino sa on-device recognizer ng Google (en-US lang ang kasama):
                // English na lang, offline pa rin. Sa Taglish, English naman ang "camera", "video call", atbp.
                val noLanguage = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                // Maikling pahinga: kapag agad na gumawa ng bagong recognizer, "server disconnected" (11).
                if (noLanguage && language != FALLBACK_LANGUAGE) {
                    workingLanguage = FALLBACK_LANGUAGE
                    Handler(Looper.getMainLooper()).postDelayed({ listen(onPartial, onText, onFail, FALLBACK_LANGUAGE) }, 500)
                }
                else onFail(error)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private const val TAG = "BantaiVoice"
        const val LANGUAGE = "fil-PH"
        const val FALLBACK_LANGUAGE = "en-US"

        /** Natatandaan kapag walang Filipino ang phone, para hindi na sumubok ulit bawat tanong. */
        private var workingLanguage = LANGUAGE

        // SpeechRecognizer.ERROR_LANGUAGE_* (API 31); kopya para gumana rin ang compile sa luma.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
