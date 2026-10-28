package app.taxicom.commandos.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Speaks short offer summaries. Falls back silently if TTS data is missing. */
class Voice(context: Context) : TextToSpeech.OnInitListener {
    private val ready = AtomicBoolean(false)
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var enabled = true
    private var locale: Locale = Locale.getDefault()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
        applyLocale(locale)
        ready.set(true)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) tts?.stop()
    }

    fun setLocale(tag: String) {
        locale = when (tag.take(2)) {
            "ru" -> Locale("ru", "RU")
            "pl" -> Locale("pl", "PL")
            else -> Locale.UK
        }
        if (ready.get()) applyLocale(locale)
    }

    fun speak(text: String) {
        if (!enabled || !ready.get() || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "offer")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    private fun applyLocale(l: Locale) {
        val engine = tts ?: return
        val result = engine.setLanguage(l)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.UK
        }
    }
}
