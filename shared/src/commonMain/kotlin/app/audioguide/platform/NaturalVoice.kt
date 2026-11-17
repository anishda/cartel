package app.audioguide.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface NaturalVoiceState {
    data object NotInstalled : NaturalVoiceState

    /** [progress] de 0 à 1. */
    data class Downloading(val progress: Float) : NaturalVoiceState

    data object Ready : NaturalVoiceState

    data object Failed : NaturalVoiceState
}

/**
 * Voix neuronale française (Piper, voix SIWIS) : un modèle d'environ 19 Mo téléchargé une fois, puis tout est hors
 * ligne. Elle lit les textes dont le pack contient les phonèmes ; le reste est lu par la voix du système.
 */
interface NaturalVoice {
    /** Faux là où la plateforme n'a pas (encore) cette voix : l'app ne propose alors rien. */
    val available: Boolean

    /** Poids du téléchargement, en Mo, pour l'annoncer avant de le lancer. */
    val downloadSizeMb: Int

    val state: StateFlow<NaturalVoiceState>

    /** Télécharge le modèle ; sans effet s'il est déjà là. Le réseau est nécessaire cette seule fois. */
    suspend fun download()

    fun delete()
}

object NoNaturalVoice : NaturalVoice {
    override val available = false
    override val downloadSizeMb = 0
    override val state: StateFlow<NaturalVoiceState> = MutableStateFlow(NaturalVoiceState.NotInstalled)
    override suspend fun download() = Unit
    override fun delete() = Unit
}
