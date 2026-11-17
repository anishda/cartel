package app.audioguide.domain

import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlayerStatus { Idle, Playing, Paused }

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    val totalMillis: Long = 0,
)

/**
 * Lecteur au-dessus de [SpeechSynthesizer] : lecture segment par segment d'un [ReadingPlan], avec pause, reprise et
 * saut. Un moteur vocal ne sait pas se mettre en pause ni avancer à un instant : on arrête la lecture et on la relance
 * au début du segment voulu (au plus une ou deux phrases). Sur l'état de lecture, [scope] doit être confiné à un seul
 * thread (le scope d'un `ViewModel`).
 */
class ReadingPlayer(
    private val synthesizer: SpeechSynthesizer,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    private var plan = ReadingPlan.build("", 1f)
    private var languageTag = "fr-FR"
    private var index = 0
    private var segmentStartedAt = 0L

    /** Change à chaque (re)lancement : une fin de lecture qui arrive d'un segment déjà interrompu est ignorée. */
    private var token = 0

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Prépare [newPlan] sans lancer la lecture. */
    fun load(newPlan: ReadingPlan, languageTag: String) {
        halt()
        plan = newPlan
        this.languageTag = languageTag
        index = 0
        _state.value = PlayerState(PlayerStatus.Idle, newPlan.totalMillis)
    }

    /** Lance la lecture à l'instant [fromMillis]. */
    fun play(fromMillis: Long = 0) {
        if (plan.isEmpty) return
        startSegment(plan.indexAt(fromMillis))
    }

    fun pause() {
        if (_state.value.status != PlayerStatus.Playing) return
        halt()
        _state.value = _state.value.copy(status = PlayerStatus.Paused)
    }

    fun resume() {
        if (_state.value.status == PlayerStatus.Paused) startSegment(index)
    }

    /** Saute à [millis] ; reste en pause si on y était. */
    fun seekTo(millis: Long) {
        val target = plan.indexAt(millis.coerceIn(0, plan.totalMillis))
        when (_state.value.status) {
            PlayerStatus.Playing -> startSegment(target)
            PlayerStatus.Paused -> index = target
            PlayerStatus.Idle -> Unit
        }
    }

    fun skip(deltaMillis: Long) = seekTo(positionMillis() + deltaMillis)

    fun stop() {
        halt()
        index = 0
        _state.value = _state.value.copy(status = PlayerStatus.Idle)
    }

    /** Position estimée : début du segment en cours, plus le temps écoulé depuis (sans dépasser sa durée estimée). */
    fun positionMillis(): Long {
        if (plan.isEmpty) return 0
        val start = plan.startMillis(index)
        return when (_state.value.status) {
            PlayerStatus.Playing -> start + min(now() - segmentStartedAt, (plan.durationMillis(index) * 0.98).toLong()).coerceAtLeast(0)
            else -> start
        }
    }

    private fun startSegment(newIndex: Int) {
        token++
        val mine = token
        index = newIndex
        segmentStartedAt = now()
        _state.value = _state.value.copy(status = PlayerStatus.Playing, totalMillis = plan.totalMillis)
        synthesizer.speak(plan.segments[newIndex], languageTag) { scope.launch { onSegmentDone(mine) } }
    }

    private fun onSegmentDone(mine: Int) {
        if (mine != token || _state.value.status != PlayerStatus.Playing) return
        // On affine l'estimation avec la durée mesurée : le temps affiché se rapproche de la réalité.
        val estimated = plan.durationMillis(index)
        val actual = now() - segmentStartedAt
        if (estimated > 0 && actual > 0) {
            val ratio = actual.toDouble() / estimated
            plan = plan.withFactor(plan.factor * (1 + (ratio - 1) * CALIBRATION_WEIGHT))
        }
        if (index < plan.segments.lastIndex) {
            startSegment(index + 1)
        } else {
            index = 0
            _state.value = _state.value.copy(status = PlayerStatus.Idle, totalMillis = plan.totalMillis)
        }
    }

    private fun halt() {
        token++
        synthesizer.stop()
    }

    private companion object {
        const val CALIBRATION_WEIGHT = 0.3
    }
}
