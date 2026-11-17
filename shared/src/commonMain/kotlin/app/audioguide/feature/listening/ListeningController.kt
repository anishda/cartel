package app.audioguide.feature.listening

import app.audioguide.domain.PlayerStatus
import app.audioguide.domain.ReadingPlan
import app.audioguide.domain.ReadingPlayer
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceStatus
import app.audioguide.platform.currentTimeMillis
import app.audioguide.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListeningState(
    /** La voix de la langue du texte est installée. */
    val voiceReady: Boolean = false,
    /** Durée estimée de l'écoute courte ; `null` s'il n'y en a pas (cartel, favori ancien). */
    val shortMillis: Long? = null,
    /** Durée estimée de l'écoute complète ; `null` tant qu'il n'y a rien à lire. */
    val fullMillis: Long? = null,
    val status: PlayerStatus = PlayerStatus.Idle,
    val positionMillis: Long = 0,
    val totalMillis: Long = 0,
) {
    /** Une lecture est en cours ou en pause : on affiche le lecteur plutôt que les boutons d'écoute. */
    val isActive: Boolean get() = status != PlayerStatus.Idle
}

/**
 * Tout ce qu'il faut à un écran pour lire un texte avec pause, reprise, saut et durée : fiche d'une œuvre, favori…
 * [configure] donne les textes à lire ; l'écran affiche [state] avec `ListeningBar`.
 */
class ListeningController(
    private val synthesizer: SpeechSynthesizer,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
) {
    private val player = ReadingPlayer(synthesizer, scope, ::currentTimeMillis)
    private val position = MutableStateFlow(0L)
    private val texts = MutableStateFlow<Texts?>(null)

    private class Texts(val languageTag: String, val short: String?, val full: String?)

    val state: StateFlow<ListeningState> = combine(
        texts, player.state, position, settings.settings, synthesizer.engineReady,
    ) { texts, playing, positionMillis, prefs, _ ->
        ListeningState(
            voiceReady = texts != null && synthesizer.voiceStatus(texts.languageTag) == VoiceStatus.Ready,
            shortMillis = texts?.short?.let { plan(it, texts.languageTag, prefs.speechRate).totalMillis },
            fullMillis = texts?.full?.let { plan(it, texts.languageTag, prefs.speechRate).totalMillis },
            status = playing.status,
            positionMillis = if (playing.status == PlayerStatus.Idle) 0 else positionMillis,
            totalMillis = playing.totalMillis,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ListeningState())

    init {
        // La position avance en continu pendant la lecture : on la rafraîchit quatre fois par seconde.
        scope.launch {
            while (true) {
                delay(POSITION_TICK_MILLIS)
                if (player.state.value.status != PlayerStatus.Idle) position.value = player.positionMillis()
            }
        }
    }

    /** Textes à lire (déjà complets : la préparation pour la voix se fait ici). `null` : pas de version. */
    fun configure(languageTag: String, short: String?, full: String?) {
        val next = Texts(languageTag, short?.takeIf { it.isNotBlank() }, full?.takeIf { it.isNotBlank() })
        val current = texts.value
        // Même contenu : rien à faire, et surtout pas d'interruption d'une lecture en cours.
        if (current != null && current.languageTag == next.languageTag && current.short == next.short && current.full == next.full) return
        texts.value = next
    }

    /** Lance l'écoute [full] (complète) ou courte ; sans version courte, c'est la complète. */
    fun play(full: Boolean) {
        val current = texts.value ?: return
        val text = (if (full) current.full else current.short ?: current.full) ?: return
        player.load(plan(text, current.languageTag, settings.settings.value.speechRate), current.languageTag)
        position.value = 0
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.resume()

    fun seekTo(millis: Long) {
        player.seekTo(millis)
        position.value = player.positionMillis()
    }

    fun skip(deltaMillis: Long) {
        player.skip(deltaMillis)
        position.value = player.positionMillis()
    }

    fun stop() = player.stop()

    private fun plan(text: String, languageTag: String, rate: Float) = ReadingPlan.build(SpeechText.prepare(text, languageTag), rate)

    private companion object {
        const val POSITION_TICK_MILLIS = 250L
    }
}
