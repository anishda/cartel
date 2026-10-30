package app.audioguide.feature.scan

import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkMatch
import app.audioguide.catalog.Catalog
import app.audioguide.domain.CartelCleaner
import app.audioguide.domain.LabelExtractor
import app.audioguide.domain.LabelLanguages
import app.audioguide.domain.LabelReader
import app.audioguide.domain.LabelVersion
import app.audioguide.domain.LanguageGuesser
import app.audioguide.domain.RecognizedText
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.VoiceStatus
import app.audioguide.favorites.FavoritesStore
import app.audioguide.platform.systemLanguageTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStep { Idle, Recognizing, Done, Failed }

enum class ScanFailure { NoText, Error }

data class ScanUiState(
    val step: ScanStep = ScanStep.Idle,
    val rawText: String = "",
    /** Texte lu : celui de la langue choisie quand le cartel est bilingue. */
    val cleanedText: String = "",
    val languageTag: String = "fr-FR",
    /** Une entrée par langue si le cartel est bilingue, sinon vide. */
    val versions: List<LabelVersion> = emptyList(),
    val voiceStatus: VoiceStatus = VoiceStatus.Checking,
    val isSpeaking: Boolean = false,
    val failure: ScanFailure? = null,
    val failureDetail: String? = null,
    val matches: List<ArtworkMatch> = emptyList(),
    /** Œuvres favorites (clé `musée/référence`), pour afficher l'état du cœur. */
    val favoriteKeys: Set<String> = emptySet(),
    /** Le texte de ce cartel a déjà été gardé en favori. */
    val cartelSaved: Boolean = false,
) {
    fun isFavorite(artwork: Artwork) = "${artwork.museumId}/${artwork.reference}" in favoriteKeys
}

class SpeechLanguage(val tag: String, val label: String)

val speechLanguages = listOf(
    SpeechLanguage("fr-FR", "Français"),
    SpeechLanguage("en-US", "English"),
    SpeechLanguage("es-ES", "Español"),
    SpeechLanguage("it-IT", "Italiano"),
    SpeechLanguage("de-DE", "Deutsch"),
    SpeechLanguage("nl-NL", "Nederlands"),
)

/**
 * Le scan en cours : il survit aux changements d'onglet, pour qu'on puisse ouvrir une fiche
 * puis revenir au cartel. Une seule instance dans l'app.
 */
class ScanSession(
    private val recognizer: TextRecognizer,
    private val synthesizer: SpeechSynthesizer,
    private val catalog: Catalog,
    private val favorites: FavoritesStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reader = LabelReader(recognizer)
    private var job: Job? = null

    private val local = MutableStateFlow(ScanUiState(languageTag = defaultLanguage()))

    val state: StateFlow<ScanUiState> =
        combine(local, synthesizer.isSpeaking, synthesizer.engineReady, favorites.favorites) { state, speaking, _, saved ->
            state.copy(
                isSpeaking = speaking,
                voiceStatus = synthesizer.voiceStatus(state.languageTag),
                favoriteKeys = saved.mapNotNull { f -> f.reference?.let { "${f.museumId}/$it" } }.toSet(),
            )
        }.stateIn(scope, SharingStarted.Eagerly, local.value)

    /** Photo d'un cartel : OCR (en deux passes si le texte est petit), puis même traitement que le scan en direct. */
    fun onImage(image: ByteArray) {
        begin()
        job = scope.launch {
            reader.read(image)
                .onSuccess { process(it) }
                .onFailure { error ->
                    local.update {
                        it.copy(step = ScanStep.Failed, failure = ScanFailure.Error, failureDetail = error.message)
                    }
                }
        }
    }

    /** Lignes déjà lues par le scan en direct. */
    fun onRecognized(recognized: RecognizedText) {
        begin()
        job = scope.launch { process(recognized) }
    }

    /** Le texte corrigé à la main : c'est lui qui est lu et gardé en favori. */
    fun setText(text: String) {
        local.update { state ->
            val versions = state.versions.map { if (it.languageTag == state.languageTag) it.copy(text = text) else it }
            state.copy(cleanedText = text, versions = versions, cartelSaved = false)
        }
    }

    /** Choisit la voix ; sur un cartel bilingue, bascule aussi sur le texte de cette langue. */
    fun setLanguage(tag: String) {
        synthesizer.stop()
        local.update { state ->
            val version = state.versions.firstOrNull { it.languageTag == tag }
            state.copy(languageTag = tag, cleanedText = version?.text ?: state.cleanedText, cartelSaved = false)
        }
    }

    fun speak() {
        val current = local.value
        if (current.cleanedText.isNotBlank()) synthesizer.speak(SpeechText.prepare(current.cleanedText), current.languageTag)
    }

    fun stop() = synthesizer.stop()

    fun clear() {
        job?.cancel()
        synthesizer.stop()
        local.update { ScanUiState(languageTag = it.languageTag) }
    }

    /** Garde le texte lu comme favori « cartel ». */
    fun saveCartel() {
        val current = local.value
        if (current.cleanedText.isBlank() || current.cartelSaved) return
        val title = current.cleanedText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80).orEmpty()
        scope.launch {
            favorites.addCartel(title, current.cleanedText, current.languageTag)
            local.update { it.copy(cartelSaved = true) }
        }
    }

    /** Ajoute ou retire l'œuvre reconnue des favoris. */
    fun toggleFavorite(artwork: Artwork) {
        val museumName = catalog.museumName(artwork.museumId) ?: return
        scope.launch { favorites.toggleArtwork(artwork, museumName) }
    }

    private fun begin() {
        job?.cancel()
        synthesizer.stop()
        local.update {
            it.copy(step = ScanStep.Recognizing, failure = null, failureDetail = null, matches = emptyList(), versions = emptyList(), cartelSaved = false)
        }
    }

    private suspend fun process(recognized: RecognizedText) {
        val extracted = LabelExtractor.extract(recognized)
        // Si l'extraction n'a rien retenu mais que l'OCR a lu quelque chose, on lit tout plutôt que rien.
        val raw = recognized.plainText
        val body = if (extracted.isEmpty) raw else extracted.text
        if (body.isBlank()) {
            local.update { it.copy(step = ScanStep.Failed, failure = ScanFailure.NoText, rawText = "", cleanedText = "") }
            return
        }
        val cleaned = CartelCleaner.clean(body)
        val matches = runCatching { catalog.match(raw) }.getOrDefault(emptyList())
        // On sépare les langues avant de nettoyer : le nettoyeur recolle les lignes qui se suivent, et recollerait
        // des lignes de deux colonnes. Chaque version est ensuite nettoyée seule.
        // Colonnes reconnues : une langue par colonne. Sinon (les deux langues sont l'une sous l'autre, ou leurs lignes
        // s'alternent) on les trie ligne à ligne.
        val lines = extracted.lineText.ifBlank { body }
        val split = LabelLanguages.splitColumns(extracted.header, extracted.columns).ifEmpty { LabelLanguages.split(lines) }
        val versions = split.map { LabelVersion(it.languageTag, CartelCleaner.clean(it.text)) }
        // Cartel bilingue : la langue du téléphone si elle y est, sinon la première du cartel.
        val system = systemLanguageTag().substringBefore('-')
        val chosen = versions.firstOrNull { it.languageTag.substringBefore('-') == system } ?: versions.firstOrNull()
        local.update {
            it.copy(
                step = ScanStep.Done,
                rawText = raw,
                cleanedText = chosen?.text ?: cleaned,
                versions = versions,
                languageTag = chosen?.languageTag ?: LanguageGuesser.guess(cleaned) ?: it.languageTag,
                matches = matches,
            )
        }
    }

    private fun defaultLanguage(): String {
        val system = systemLanguageTag()
        return speechLanguages.firstOrNull { it.tag.substringBefore('-') == system.substringBefore('-') }?.tag ?: "en-US"
    }
}
