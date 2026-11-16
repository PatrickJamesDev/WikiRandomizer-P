package com.wikirandomizerp.app.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class TtsController(context: Context) {
    var isSpeaking by mutableStateOf(false)
        private set

    private var ready = false
    private var pending: Pending? = null

    private data class Pending(val text: String, val lang: String, val onUnavailable: () -> Unit)

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            val tts = engine
            if (ready && tts != null) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                    }
                })
                pending?.let {
                    pending = null
                    speak(it.text, it.lang, it.onUnavailable)
                }
            } else {
                pending?.let {
                    pending = null
                    it.onUnavailable()
                }
            }
        }
    }

    fun speak(text: String, lang: String, onUnavailable: () -> Unit) {
        if (text.isBlank()) return
        if (!ready) {
            pending = Pending(text, lang, onUnavailable)
            return
        }
        val tts = engine ?: run {
            onUnavailable()
            return
        }
        val locale = Locale.forLanguageTag(lang.replace('_', '-'))
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        }
        isSpeaking = true
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "article")
        if (queued != TextToSpeech.SUCCESS) {
            isSpeaking = false
            onUnavailable()
        }
    }

    fun stop() {
        pending = null
        if (ready) engine?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        pending = null
        engine?.let { tts ->
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
        engine = null
        isSpeaking = false
    }
}

@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}
