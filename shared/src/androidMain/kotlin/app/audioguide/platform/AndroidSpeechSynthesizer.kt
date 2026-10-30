package app.audioguide.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceQuality
import app.audioguide.domain.VoiceStatus
import app.audioguide.domain.splitForSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** `TextToSpeech` Android, en ne retenant que des voix qui ne demandent pas de réseau. */
class AndroidSpeechSynthesizer(context: Context) : SpeechSynthesizer {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _engineReady = MutableStateFlow(false)
    override val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    private var queued: (() -> Unit)? = null
    private var lastUtteranceId: String? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastUtteranceId) _isSpeaking.value = false
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _isSpeaking.value = false
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            _isSpeaking.value = false
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.setOnUtteranceProgressListener(listener)
            _engineReady.value = true
            queued?.invoke()
        }
        queued = null
    }

    override fun voiceStatus(languageTag: String): VoiceStatus {
        if (!_engineReady.value) return VoiceStatus.Checking
        val locale = Locale.forLanguageTag(languageTag)
        return when (tts.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_NOT_SUPPORTED -> VoiceStatus.Unsupported
            TextToSpeech.LANG_MISSING_DATA -> VoiceStatus.NeedsDownload
            else -> if (offlineVoice(locale) != null) VoiceStatus.Ready else VoiceStatus.NeedsDownload
        }
    }

    private var rate = 1.0f

    override fun setRate(rate: Float) {
        this.rate = rate
        if (_engineReady.value) tts.setSpeechRate(rate)
    }

    private val preferred = mutableMapOf<String, String>()

    override fun voices(languageTag: String): List<VoiceOption> {
        if (!_engineReady.value) return emptyList()
        val locale = Locale.forLanguageTag(languageTag)
        return installedVoices(locale).map { voice ->
            VoiceOption(
                id = voice.name,
                name = displayName(voice),
                languageTag = voice.locale.toLanguageTag(),
                quality = when {
                    voice.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> VoiceQuality.Premium
                    voice.quality >= android.speech.tts.Voice.QUALITY_HIGH -> VoiceQuality.Enhanced
                    else -> VoiceQuality.Standard
                },
                needsNetwork = voice.isNetworkConnectionRequired,
            )
        }
    }

    override fun setPreferredVoice(languageTag: String, voiceId: String?) {
        if (voiceId == null) preferred.remove(languageTag) else preferred[languageTag] = voiceId
    }

    override fun speak(text: String, languageTag: String) {
        if (!_engineReady.value) {
            queued = { speak(text, languageTag) }
            return
        }
        val locale = Locale.forLanguageTag(languageTag)
        tts.setSpeechRate(rate)
        val voice = preferred[languageTag]?.let { id -> tts.voices.orEmpty().firstOrNull { it.name == id } } ?: offlineVoice(locale)
        voice?.let { tts.voice = it } ?: tts.setLanguage(locale)

        // Un paragraphe à la fois, avec un silence entre deux : sans cela, le moteur enchaîne tout d'une traite.
        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(500)
        val paragraphs = SpeechText.paragraphs(text).ifEmpty { listOf(text) }
        var first = true
        var count = 0
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val chunks = splitForSpeech(paragraph, maxLength)
            chunks.forEachIndexed { chunkIndex, chunk ->
                val id = "cartel-${count++}"
                tts.speak(chunk, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
                first = false
                if (paragraphIndex == paragraphs.lastIndex && chunkIndex == chunks.lastIndex) lastUtteranceId = id
            }
            if (paragraphIndex < paragraphs.lastIndex) {
                tts.playSilentUtterance(PARAGRAPH_PAUSE_MS, TextToSpeech.QUEUE_ADD, "pause-$paragraphIndex")
            }
        }
    }

    override fun stop() {
        if (_engineReady.value) tts.stop()
        _isSpeaking.value = false
    }

    /** Voix installées pour la langue, de la meilleure à la moins bonne : région demandée, hors ligne, qualité. */
    private fun installedVoices(locale: Locale) = tts.voices.orEmpty()
        .filter { it.locale.language == locale.language && "notInstalled" !in it.features.orEmpty() }
        .sortedWith(
            compareByDescending<android.speech.tts.Voice> { it.locale.country == locale.country }
                .thenBy { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality },
        )

    /** Meilleure voix installée pour la langue qui n'exige pas de connexion. */
    private fun offlineVoice(locale: Locale) = installedVoices(locale).firstOrNull { !it.isNetworkConnectionRequired }

    /** « fr-fr-x-frd-local » est illisible : on garde la région et le code de la voix (« Français (France) · FRD »). */
    private fun displayName(voice: android.speech.tts.Voice): String {
        val code = Regex("-x-([a-z0-9]+)").find(voice.name)?.groupValues?.get(1)?.uppercase()
        val place = voice.locale.getDisplayName(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        return if (code != null) "$place · $code" else place
    }

    private companion object {
        const val PARAGRAPH_PAUSE_MS = 500L
    }
}
