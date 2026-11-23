package com.example.data.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val TTS_TAG = "NarrationSynth"

class NarrationSynthesizer(private val context: Context) {

    suspend fun synthesizeSceneWav(
        text: String,
        languageLabel: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        val result = withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine { cont ->
                var tts: TextToSpeech? = null
                val utteranceId = UUID.randomUUID().toString()

                fun cleanup() {
                    runCatching {
                        tts?.stop()
                        tts?.shutdown()
                    }
                }

                cont.invokeOnCancellation { cleanup() }

                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.w(TTS_TAG, "TTS initialization status: $status")
                        cleanup()
                        if (cont.isActive) cont.resume(false)
                        return@TextToSpeech
                    }
                    val engine = tts ?: return@TextToSpeech
                    val locale = localeForLanguage(languageLabel)
                    val langResult = engine.setLanguage(locale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                        langResult == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.US)
                    }
                    engine.setSpeechRate(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId) {
                                cleanup()
                                if (cont.isActive) cont.resume(outputFile.exists() && outputFile.length() > 44L)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId) {
                                cleanup()
                                if (cont.isActive) cont.resume(false)
                            }
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId) {
                                Log.w(TTS_TAG, "TTS synthesis error code: $errorCode")
                                cleanup()
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                    })

                    val params = Bundle()
                    val synthStatus = engine.synthesizeToFile(text, params, outputFile, utteranceId)
                    if (synthStatus != TextToSpeech.SUCCESS) {
                        cleanup()
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
        }
        result == true
    }

    private fun localeForLanguage(label: String): Locale = when (label.lowercase()) {
        "swahili", "kiswahili" -> Locale("sw", "TZ")
        "spanish", "español" -> Locale("es", "ES")
        "french", "français" -> Locale.FRENCH
        "german", "deutsch" -> Locale.GERMAN
        "portuguese", "português" -> Locale("pt", "BR")
        "arabic" -> Locale("ar")
        "hindi" -> Locale("hi", "IN")
        "japanese" -> Locale.JAPANESE
        else -> Locale.US
    }
}
