package app.audioguide.domain

import kotlinx.coroutines.flow.StateFlow

enum class VoiceStatus {
    /** Le moteur vocal n'a pas fini de s'initialiser. */
    Checking,

    /** Une voix hors ligne est disponible pour la langue. */
    Ready,

    /** La langue est gérée mais ses données vocales sont à télécharger (ou en ligne seulement). */
    NeedsDownload,

    /** Aucune voix pour cette langue. */
    Unsupported,
}

enum class VoiceQuality { Standard, Enhanced, Premium }

/** Une voix installée sur l'appareil. */
data class VoiceOption(
    val id: String,
    val name: String,
    val languageTag: String,
    val quality: VoiceQuality,
    /** Voix qui ne marche pas sans réseau (certaines voix Android). */
    val needsNetwork: Boolean,
)

/** Lecture vocale sur l'appareil (`TextToSpeech` sur Android, `AVSpeechSynthesizer` sur iOS). */
interface SpeechSynthesizer {
    val isSpeaking: StateFlow<Boolean>

    /** Passe à `true` quand le moteur vocal est initialisé : [voiceStatus] devient fiable. */
    val engineReady: StateFlow<Boolean>

    /** [languageTag] : étiquette BCP 47, par exemple `fr-FR`. */
    fun voiceStatus(languageTag: String): VoiceStatus

    /** Vitesse de lecture : 1,0 = normale. S'applique aux lectures suivantes. */
    fun setRate(rate: Float)

    /** Voix installées pour la langue, de la meilleure à la moins bonne. */
    fun voices(languageTag: String): List<VoiceOption>

    /** Voix à utiliser pour [languageTag] ; `null` : la meilleure voix installée. */
    fun setPreferredVoice(languageTag: String, voiceId: String?)

    /**
     * Lit [text] ; une ligne vide sépare les paragraphes, entre lesquels le synthétiseur marque une pause.
     * [onDone] est appelé (sur un thread quelconque) quand la lecture va jusqu'au bout, jamais si [stop] ou un
     * nouvel appel de [speak] l'interrompt.
     */
    fun speak(text: String, languageTag: String, onDone: (() -> Unit)? = null)

    fun stop()
}
