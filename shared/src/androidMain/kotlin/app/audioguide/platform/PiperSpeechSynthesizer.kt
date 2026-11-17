package app.audioguide.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Segment préparé d'avance (texte, échantillons) : celui qui suit le segment en cours de lecture. */
    private var prefetched: Pair<String, FloatArray>? = null
    private val prefetchLock = Mutex()
    private val engineLock = Mutex()

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
            val samples = runCatching { samplesFor(text, mine) }.getOrNull()
            if (mine != generation) return@launch
            if (samples == null) {
                // Texte absent du pack : la voix du système le lit.
                system.speak(text, languageTag, onDone)
                return@launch
            }
            val completed = runCatching { play(samples, mine) }.getOrDefault(false)
            if (mine == generation) {
                piperSpeaking.value = false
                if (completed) onDone?.invoke()
            }
        }
    }

    override fun prepare(text: String, languageTag: String) {
        if (!usable(languageTag)) return
        val mine = generation
        scope.launch(Dispatchers.Default) {
            // Une seule préparation à la fois, pour ne pas ralentir la lecture en cours.
            prefetchLock.withLock {
                if (mine != generation || prefetched?.first == text) return@withLock
                val samples = runCatching { synthesizeText(text) }.getOrNull() ?: return@withLock
                prefetched = text to samples
            }
        }
    }

    override fun stop() {
        generation++
        piperSpeaking.value = false
        track?.let { runCatching { it.pause(); it.flush() } }
        system.stop()
    }

    /** Les échantillons du segment : ceux préparés pendant le segment précédent, sinon à synthétiser maintenant. */
    private suspend fun samplesFor(text: String, mine: Int): FloatArray? {
        prefetchLock.withLock {
            prefetched?.takeIf { it.first == text }?.let { prefetched = null; return it.second }
        }
        return synthesizeText(text)
    }

    /** Phonèmes du pack, puis synthèse du segment en entier : pas de coupure au milieu, à la différence d'un flux. */
    private suspend fun synthesizeText(text: String): FloatArray? {
        val phonemeText = phonemes(NarrationPhonemes.key(text)) ?: return null
        val config = GenerationConfig(
            speed = rate,
            phonemeCodepoints = phonemeText.lines().filter { it.isNotBlank() }
                .map { sentence -> sentence.codePoints().toArray() }
                .toTypedArray(),
        )
        // Le moteur ne fait qu'une synthèse à la fois.
        return engineLock.withLock {
            val tts = engine ?: createEngine().also { engine = it }
            val started = System.currentTimeMillis()
            val audio = tts.generateWithConfig("", config)
            Log.d(TAG, "Piper : ${audio.samples.size * 1000L / audio.sampleRate} ms d'audio synthétisés en ${System.currentTimeMillis() - started} ms")
            sampleRate = audio.sampleRate
            audio.samples
        }
    }

    @Volatile private var sampleRate = 22_050

    /** Joue les échantillons. Retourne vrai si la lecture est allée jusqu'au bout. */
    private fun play(samples: FloatArray, mine: Int): Boolean {
        val audio = createTrack(sampleRate)
        track = audio
        piperSpeaking.value = true
        try {
            audio.play()
            var offset = 0
            while (offset < samples.size && mine == generation) {
                val count = minOf(CHUNK, samples.size - offset)
                audio.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
                offset += count
            }
            // Laisse finir ce qui reste dans le tampon avant de dire que c'est terminé.
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            while (mine == generation && (audio.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < samples.size && System.currentTimeMillis() < deadline) {
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
        const val TAG = "PiperSpeech"
        const val CHUNK = 4096
        const val THREADS = 4
        const val DRAIN_TIMEOUT_MS = 10_000L
    }
}
