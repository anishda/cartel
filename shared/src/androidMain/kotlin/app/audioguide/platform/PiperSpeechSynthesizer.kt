package app.audioguide.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.audioguide.catalog.NarrationPhonemes
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceStatus
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * Lecture à la voix neuronale (Piper SIWIS, via sherpa-onnx) quand elle est installée et que le pack donne les phonèmes
 * du texte ; sinon, lecture par [system], la voix du système. Le français uniquement : la voix n'en parle pas d'autre.
 *
 * Les phonèmes viennent du pack (voir `NarrationPhonemes`) : le moteur ne phonétise pas lui-même. Un texte inconnu du
 * pack (cartel scanné, favori ancien…) retombe donc sur la voix du système, sans erreur.
 */
class PiperSpeechSynthesizer(
    private val system: SpeechSynthesizer,
    private val model: PiperVoiceModel,
    /** Phonèmes précalculés du segment dont la clé est donnée (voir `Catalog.phonemes`). */
    private val phonemes: suspend (key: String) -> String?,
    /** Le réglage « voix naturelle » est actif. */
    private val enabled: () -> Boolean,
    private val scope: CoroutineScope,
) : SpeechSynthesizer by system {
    private val piperSpeaking = MutableStateFlow(false)

    override val isSpeaking: StateFlow<Boolean> =
        combine(system.isSpeaking, piperSpeaking) { a, b -> a || b }.stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile private var rate = 1.0f

    /** Change à chaque lecture lancée ou arrêtée : une lecture périmée s'interrompt d'elle-même. */
    @Volatile private var generation = 0

    @Volatile private var track: AudioTrack? = null
    private var engine: OfflineTts? = null

    private fun usable(languageTag: String) =
        enabled() && model.state.value == NaturalVoiceState.Ready && languageTag.startsWith("fr", ignoreCase = true)

    override fun voiceStatus(languageTag: String): VoiceStatus =
        if (usable(languageTag)) VoiceStatus.Ready else system.voiceStatus(languageTag)

    override fun voices(languageTag: String): List<VoiceOption> = system.voices(languageTag)

    override fun setRate(rate: Float) {
        this.rate = rate
        system.setRate(rate)
    }

    override fun speak(text: String, languageTag: String, onDone: (() -> Unit)?) {
        stop()
        if (!usable(languageTag)) {
            system.speak(text, languageTag, onDone)
            return
        }
        val mine = ++generation
        scope.launch(Dispatchers.Default) {
            val found = runCatching { phonemes(NarrationPhonemes.key(text)) }.getOrNull()
            if (mine != generation) return@launch
            if (found == null) {
                // Texte absent du pack : la voix du système le lit.
                system.speak(text, languageTag, onDone)
                return@launch
            }
            val completed = runCatching { synthesize(found, mine) }.getOrDefault(false)
            if (mine == generation) {
                piperSpeaking.value = false
                if (completed) onDone?.invoke()
            }
        }
    }

    override fun stop() {
        generation++
        piperSpeaking.value = false
        track?.let { runCatching { it.pause(); it.flush() } }
        system.stop()
    }

    /** Synthétise et joue au fil de l'eau. Retourne vrai si la lecture est allée jusqu'au bout. */
    private fun synthesize(phonemeText: String, mine: Int): Boolean {
        val tts = engine ?: createEngine().also { engine = it }
        val config = GenerationConfig(
            speed = rate,
            phonemeCodepoints = phonemeText.lines().filter { it.isNotBlank() }
                .map { sentence -> sentence.codePoints().toArray() }
                .toTypedArray(),
        )
        val audio = createTrack(tts.sampleRate())
        track = audio
        piperSpeaking.value = true
        audio.play()
        var frames = 0L
        try {
            tts.generateWithConfigAndCallback("", config) { samples ->
                if (mine != generation) {
                    0
                } else {
                    // Bloque quand le tampon audio est plein : la synthèse suit la lecture.
                    audio.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    frames += samples.size
                    1
                }
            }
            // Laisse finir ce qui reste dans le tampon avant de dire que c'est terminé.
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            while (mine == generation && (audio.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < frames && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            return mine == generation
        } finally {
            runCatching { audio.stop() }
            audio.release()
            if (track === audio) track = null
        }
    }

    private fun createEngine(): OfflineTts = OfflineTts(
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = model.modelFile.absolutePath, tokens = model.tokensFile.absolutePath),
                numThreads = THREADS,
            ),
        ),
    )

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private companion object {
        const val THREADS = 2
        const val DRAIN_TIMEOUT_MS = 10_000L
    }
}
