package app.audioguide.platform

import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceQuality
import app.audioguide.domain.VoiceStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.AVFAudio.setActive
import platform.darwin.NSObject

/** `AVSpeechSynthesizer` : les voix Apple tournent sur l'appareil, sans réseau. */
@OptIn(ExperimentalForeignApi::class)
class AppleSpeechSynthesizer : SpeechSynthesizer {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Les voix Apple sont disponibles immédiatement.
    override val engineReady: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    /** Les paragraphes pas encore terminés : on parle tant qu'il en reste. */
    private val pending = mutableSetOf<AVSpeechUtterance>()

    // `AVSpeechSynthesizer.delegate` est faible : on garde le delegate dans un champ.
    private val delegate = SpeechDelegate(
        onStarted = { _isSpeaking.value = true },
        onEnded = { utterance ->
            pending.remove(utterance)
            if (pending.isEmpty()) _isSpeaking.value = false
        },
    )
    private val synthesizer = AVSpeechSynthesizer().also { it.delegate = delegate }

    private val preferred = mutableMapOf<String, String>()

    override fun voiceStatus(languageTag: String): VoiceStatus =
        if (installedVoices(languageTag).isEmpty()) VoiceStatus.Unsupported else VoiceStatus.Ready

    private var rate = 1.0f

    override fun setRate(rate: Float) {
        this.rate = rate
    }

    override fun voices(languageTag: String): List<VoiceOption> = installedVoices(languageTag).map { voice ->
        VoiceOption(
            id = voice.identifier,
            name = voice.name,
            languageTag = voice.language.replace('_', '-'),
            quality = when (voice.quality.toInt()) {
                QUALITY_PREMIUM -> VoiceQuality.Premium
                QUALITY_ENHANCED -> VoiceQuality.Enhanced
                else -> VoiceQuality.Standard
            },
            needsNetwork = false,
        )
    }

    override fun setPreferredVoice(languageTag: String, voiceId: String?) {
        if (voiceId == null) preferred.remove(languageTag) else preferred[languageTag] = voiceId
    }

    override fun speak(text: String, languageTag: String) {
        stop()
        // Lecture même quand le téléphone est en mode silencieux.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        val voice = chosenVoice(languageTag)
        // 0,5 est la vitesse normale d'Apple (AVSpeechUtteranceDefaultSpeechRate) ; l'échelle va de 0 à 1.
        val speed = (AVSpeechUtteranceDefaultSpeechRate * rate).coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        val paragraphs = SpeechText.paragraphs(text).ifEmpty { listOf(text) }
        val utterances = paragraphs.mapIndexed { index, paragraph ->
            AVSpeechUtterance(string = paragraph).also {
                it.voice = voice
                it.rate = speed
                // Une respiration entre deux paragraphes, comme un lecteur humain.
                if (index < paragraphs.lastIndex) it.postUtteranceDelay = PARAGRAPH_PAUSE_SECONDS
            }
        }
        pending.addAll(utterances)
        utterances.forEach(synthesizer::speakUtterance)
    }

    override fun stop() {
        // Vider avant d'arrêter : les « annulé » qui arrivent ensuite ne concernent plus aucun paragraphe en attente.
        pending.clear()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        _isSpeaking.value = false
    }

    private fun chosenVoice(languageTag: String): AVSpeechSynthesisVoice? =
        preferred[languageTag]?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?: installedVoices(languageTag).firstOrNull()

    /**
     * Voix de la langue demandée, de la meilleure qualité (premium, améliorée, standard) à la moins bonne. Les voix
     * « fantaisie » (Grandma, Bells…) sont écartées : elles n'ont rien à faire dans un audioguide.
     */
    private fun installedVoices(languageTag: String): List<AVSpeechSynthesisVoice> {
        val all = AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filterNot { it.identifier.contains("eloquence", ignoreCase = true) || it.identifier.contains("speech.synthesis.voice") }
        val exact = all.filter { it.language.replace('_', '-') == languageTag }
        val sameLanguage = all.filter { it.language.substringBefore('-').substringBefore('_') == languageTag.substringBefore('-') }
        // Région demandée d'abord (fr-FR avant fr-CA), puis la qualité.
        return (exact + (sameLanguage - exact.toSet())).sortedByDescending { it.quality }
            .sortedBy { if (it in exact) 0 else 1 }
    }

    private companion object {
        const val PARAGRAPH_PAUSE_SECONDS = 0.5
        const val QUALITY_ENHANCED = 2
        const val QUALITY_PREMIUM = 3
    }
}

// Kotlin n'autorise pas de mélanger supertypes Kotlin et Objective-C : le delegate est une classe à part.
@OptIn(ExperimentalForeignApi::class)
private class SpeechDelegate(
    private val onStarted: () -> Unit,
    private val onEnded: (AVSpeechUtterance) -> Unit,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didStartSpeechUtterance: AVSpeechUtterance) =
        onStarted()

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) =
        onEnded(didFinishSpeechUtterance)

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) =
        onEnded(didCancelSpeechUtterance)
}
