package app.audioguide.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceStatus
import app.audioguide.platform.AppInfo
import app.audioguide.settings.Settings
import app.audioguide.settings.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore,
    private val synthesizer: SpeechSynthesizer,
    val appInfo: AppInfo,
) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { store.update { it.copy(speechRate = rate) } }
    }

    /** Passe à vrai quand le moteur vocal est prêt : la liste des voix n'est fiable qu'à partir de là. */
    val engineReady: StateFlow<Boolean> = synthesizer.engineReady

    fun voices(languageTag: String): List<VoiceOption> = synthesizer.voices(languageTag)

    /** Choisit la voix de [languageTag] ; `null` : la meilleure voix installée. */
    fun setPreferredVoice(languageTag: String, voiceId: String?) {
        synthesizer.setPreferredVoice(languageTag, voiceId)
        viewModelScope.launch {
            store.update { settings ->
                val voices = if (voiceId == null) settings.preferredVoices - languageTag else settings.preferredVoices + (languageTag to voiceId)
                settings.copy(preferredVoices = voices)
            }
        }
    }

    /**
     * Lit [text] à la vitesse choisie, pour l'essayer. [voiceId] : une voix précise à écouter, sinon celle des réglages.
     * Retourne faux si aucune voix n'est disponible.
     */
    fun preview(text: String, languageTag: String, voiceId: String? = null): Boolean {
        if (synthesizer.voiceStatus(languageTag) != VoiceStatus.Ready) return false
        synthesizer.setRate(store.settings.value.speechRate)
        val saved = store.settings.value.preferredVoices[languageTag]
        synthesizer.setPreferredVoice(languageTag, voiceId ?: saved)
        synthesizer.speak(SpeechText.prepare(text), languageTag)
        // La voix est prise au moment de lancer la lecture : on remet celle des réglages pour la suite.
        synthesizer.setPreferredVoice(languageTag, saved)
        return true
    }

    override fun onCleared() {
        synthesizer.stop()
    }
}
