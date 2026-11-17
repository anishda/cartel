package app.audioguide.feature.scan

import app.audioguide.catalog.ArtworkDraft
import app.audioguide.catalog.Catalog
import app.audioguide.catalog.PackInfo
import app.audioguide.catalog.PackWriter
import app.audioguide.domain.RecognizedText
import app.audioguide.domain.Region
import app.audioguide.domain.TextLine
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.TextTranslator
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceStatus
import app.audioguide.favorites.FavoriteKind
import app.audioguide.favorites.FavoritesStore
import app.audioguide.packs.PackStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ScanSessionTest {
    private val root = Files.createTempDirectory("audioguide-session-test")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        // La base des favoris peut encore s'ouvrir en arrière-plan : on réessaie la suppression.
        repeat(5) {
            if (runCatching { root.deleteRecursively() }.isSuccess) return
            Thread.sleep(100)
        }
    }

    private class FakeRecognizer(var result: Result<RecognizedText> = Result.success(RecognizedText(emptyList(), 1000, 1000))) : TextRecognizer {
        override suspend fun recognize(image: ByteArray, region: Region?, scale: Float) = result
    }

    private class FakeSynthesizer : SpeechSynthesizer {
        override val isSpeaking = MutableStateFlow(false)
        override val engineReady = MutableStateFlow(true)
        val spoken = mutableListOf<Pair<String, String>>()
        override fun voiceStatus(languageTag: String) = VoiceStatus.Ready
        override fun setRate(rate: Float) = Unit
        override fun voices(languageTag: String) = emptyList<VoiceOption>()
        override fun setPreferredVoice(languageTag: String, voiceId: String?) = Unit
        override fun speak(text: String, languageTag: String, onDone: (() -> Unit)?) { spoken += text to languageTag }
        override fun stop() = Unit
    }

    private val recognizer = FakeRecognizer()
    private val synthesizer = FakeSynthesizer()
    private val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
    private val catalog = Catalog(store)
    private val favorites = FavoritesStore(root.resolve("user.db").toString(), { 1_000L }, scope)

    private class FakeTranslator(var result: Result<String> = Result.success("Le tableau reprend le motif de la Vénus couchée.")) : TextTranslator {
        override val isAvailable = true
        val requests = mutableListOf<Triple<String, String, String>>()
        override fun supports(from: String, to: String) = from != to
        override suspend fun translate(text: String, from: String, to: String): Result<String> {
            requests += Triple(text, from, to)
            return result
        }
    }

    private val translator = FakeTranslator()

    private fun session() = ScanSession(recognizer, synthesizer, catalog, favorites, translator)

    private fun installOrsay() = runBlocking {
        val file = root.resolve("orsay.db").toString()
        PackWriter.create(file).use { writer ->
            writer.add(ArtworkDraft(reference = "R1", inventoryNo = "RF 644", title = "Olympia", artist = "Édouard Manet", dateText = "1863"))
            writer.finish(
                PackInfo("orsay", "musée d'Orsay", "Paris", "2026-10-12", "2026-10-12T00:00:00Z", "Joconde", null, "Licence Ouverte 2.0", null, 0),
            )
        }
        store.install("orsay", file.let { java.nio.file.Path.of(it).toOkioPath() }, null)
    }

    private suspend fun <T> StateFlow<T>.await(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(10_000) { first(condition) } }

    private val frenchCartel = "Édouard MANET (1832-1883)\nOlympia\n1863\n\nLe tableau reprend le motif de la Vénus\ncouchée de la Renaissance, mais en le\ntransposant dans le Paris du XIXe siècle.\nParis, musée d'Orsay. Inv. RF 644"

    private val bilingualCartel = listOf(
        "Édouard MANET (1832-1883)", "Olympia", "1863",
        "Le tableau reprend le motif de la Vénus", "couchée de la Renaissance, mais en le", "transposant dans le Paris du XIXe siècle.",
        "Présenté au Salon de 1865, il provoque", "un scandale que la presse a relayé.",
        "The painting takes up the motif of the", "reclining Venus of the Renaissance, but", "sets it in the Paris of the nineteenth century.",
        "Shown at the Salon of 1865, it caused", "a scandal that the press has relayed.",
    ).joinToString("\n")

    private val englishCartel = "Édouard MANET\nOlympia\n\nThe painting takes up the motif of the\nreclining Venus of the Renaissance, but\nsets it in the Paris of the nineteenth century.\nShown at the Salon of 1865, it caused\na scandal that the press has relayed."

    @Test
    fun anEnglishCartelCanBeTranslatedToFrenchAndRead() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(englishCartel))
        val ready = session.state.await { it.step == ScanStep.Done }
        assertEquals("en-US", ready.languageTag)
        assertTrue(ready.canTranslate)

        session.translate()
        val done = session.state.await { it.translation is TranslationState.Done }
        assertEquals("Le tableau reprend le motif de la Vénus couchée.", (done.translation as TranslationState.Done).text)
        assertEquals("en", translator.requests.single().second)
        assertEquals("fr", translator.requests.single().third)

        session.speakTranslation()
        assertEquals("fr-FR", synthesizer.spoken.last().second)
    }

    @Test
    fun aFailedTranslationCanBeRetriedAndCartelChangesResetIt() = runBlocking {
        translator.result = Result.failure(java.io.IOException("offline"))
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(englishCartel))
        session.state.await { it.step == ScanStep.Done }
        session.translate()
        session.state.await { it.translation == TranslationState.Failed }

        translator.result = Result.success("Traduit.")
        session.translate()
        session.state.await { it.translation == TranslationState.Done("Traduit.") }
        session.clear()
        assertEquals(TranslationState.None, session.state.await { it.step == ScanStep.Idle }.translation)
    }

    @Test
    fun aFrenchCartelHasNothingToTranslate() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        assertEquals(false, session.state.await { it.step == ScanStep.Done }.canTranslate)
    }

    @Test
    fun aRecognizedTextIsCleanedAndMatched() = runBlocking {
        installOrsay()
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))

        val state = session.state.await { it.step == ScanStep.Done }
        assertEquals("fr-FR", state.languageTag)
        assertTrue(state.cleanedText.contains("dix-neuvième siècle"), state.cleanedText)
        assertTrue(!state.cleanedText.contains("RF"), state.cleanedText)
        assertEquals("Olympia", state.matches.first().artwork.title)
        assertTrue(state.versions.isEmpty())
    }

    @Test
    fun anImageGoesThroughTheRecognizer() = runBlocking {
        recognizer.result = Result.success(RecognizedText.fromPlainText(frenchCartel))
        val session = session()
        session.onImage(byteArrayOf(1, 2, 3))
        assertEquals(ScanStep.Done, session.state.await { it.step == ScanStep.Done }.step)
    }

    @Test
    fun anEmptyTextIsReportedAsNoText() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText("   "))
        assertEquals(ScanFailure.NoText, session.state.await { it.step == ScanStep.Failed }.failure)
    }

    @Test
    fun aRecognizerFailureIsReported() = runBlocking {
        recognizer.result = Result.failure(IllegalStateException("caméra illisible"))
        val session = session()
        session.onImage(byteArrayOf(1))
        val state = session.state.await { it.step == ScanStep.Failed }
        assertEquals(ScanFailure.Error, state.failure)
        assertEquals("caméra illisible", state.failureDetail)
    }

    @Test
    fun aBilingualLabelOffersEachLanguageAndSwitchesText() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(bilingualCartel))

        val state = session.state.await { it.step == ScanStep.Done }
        assertEquals(setOf("fr-FR", "en-US"), state.versions.map { it.languageTag }.toSet())
        assertEquals(state.versions.first { it.languageTag == state.languageTag }.text, state.cleanedText)

        session.setLanguage("en-US")
        val english = session.state.await { it.languageTag == "en-US" && it.cleanedText.contains("painting") }
        assertTrue(!english.cleanedText.contains("tableau"), english.cleanedText)

        session.setLanguage("fr-FR")
        val french = session.state.await { it.languageTag == "fr-FR" && it.cleanedText.contains("tableau") }
        assertTrue(!french.cleanedText.contains("painting"), french.cleanedText)

        session.speak()
        assertEquals("fr-FR", synthesizer.spoken.last().second)
        assertTrue(synthesizer.spoken.last().first.contains("tableau"))
    }

    @Test
    fun interleavedColumnsAreSeparatedBeforeLinesAreReflowed() = runBlocking {
        // Lecture ligne à ligne de deux colonnes : une ligne qui finit sans ponctuation est suivie, côté
        // français, d'une ligne en minuscule. Le nettoyeur la recollerait à la ligne anglaise voisine.
        val interleaved = listOf(
            "Édouard MANET (1832-1883)", "Olympia", "1863",
            "Le tableau reprend le motif de la Vénus", "The painting takes up the motif of the",
            "couchée de la Renaissance, mais en le", "reclining Venus of the Renaissance, but",
            "transposant dans le Paris du XIXe siècle.", "sets it in the Paris of the nineteenth century.",
            "Présenté au Salon de 1865, il provoque", "Shown at the Salon of 1865, it caused",
            "un scandale que la presse a relayé.", "a scandal that the press has relayed.",
        ).joinToString("\n")
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(interleaved))
        session.state.await { it.step == ScanStep.Done }

        session.setLanguage("fr-FR")
        val french = session.state.await { it.languageTag == "fr-FR" && it.cleanedText.contains("tableau") }
        assertTrue(!french.cleanedText.contains("painting") && !french.cleanedText.contains("reclining"), french.cleanedText)
        assertTrue(french.cleanedText.contains("couchée de la Renaissance"), french.cleanedText)

        session.setLanguage("en-US")
        val english = session.state.await { it.languageTag == "en-US" && it.cleanedText.contains("painting") }
        assertTrue(!english.cleanedText.contains("tableau") && !english.cleanedText.contains("couchée"), english.cleanedText)
        assertTrue(english.cleanedText.contains("reclining Venus of the Renaissance"), english.cleanedText)
    }

    @Test
    fun theRecognizedArtworkCanBeFavoritedAndUnfavorited() = runBlocking {
        installOrsay()
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        val artwork = session.state.await { it.step == ScanStep.Done }.matches.first().artwork

        session.toggleFavorite(artwork)
        assertTrue(session.state.await { it.isFavorite(artwork) }.isFavorite(artwork))
        assertEquals("musée d'Orsay", favorites.favorites.value.single().museumName)

        session.toggleFavorite(artwork)
        session.state.await { !it.isFavorite(artwork) }
        assertTrue(favorites.favorites.value.isEmpty())
    }

    @Test
    fun keepingACartelSavesItsTextOnce() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        session.state.await { it.step == ScanStep.Done }

        session.saveCartel()
        val saved = session.state.await { it.cartelSaved }
        session.saveCartel()
        session.saveCartel()

        val favorite = favorites.favorites.await { it.isNotEmpty() }.single()
        assertEquals(FavoriteKind.Cartel, favorite.kind)
        assertEquals("Édouard MANET (de 1832 à 1883) Olympia 1863", favorite.title)
        assertEquals(saved.cleanedText, favorite.body)
    }

    @Test
    fun aNewScanForgetsThePreviousResult() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        session.state.await { it.step == ScanStep.Done }
        session.clear()
        val state = session.state.await { it.step == ScanStep.Idle }
        assertTrue(state.cleanedText.isEmpty() && state.matches.isEmpty())
    }

    @Test
    fun theCorrectedTextIsWhatGetsSpokenAndKept() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        session.state.await { it.step == ScanStep.Done }

        session.setText("Un texte corrigé à la main")
        session.speak()
        assertEquals("Un texte corrigé à la main.", synthesizer.spoken.last().first)

        session.saveCartel()
        assertEquals("Un texte corrigé à la main", favorites.favorites.await { it.isNotEmpty() }.single().body)
    }

    @Test
    fun spokenTextGetsItsParagraphsAndFinalPunctuation() = runBlocking {
        val session = session()
        session.onRecognized(RecognizedText.fromPlainText(frenchCartel))
        session.state.await { it.step == ScanStep.Done }
        session.speak()
        val spoken = synthesizer.spoken.last().first
        assertTrue(spoken.contains("\n\n"), spoken)
        assertTrue(spoken.endsWith("."), spoken)
    }

    @Test
    fun aDoubtfulReadingIsStillOfferedWhenNothingBetterIsFound() = runBlocking {
        // Toutes les lignes sont jugées peu sûres : l'extraction ne retient rien, mais on préfère montrer ce que l'OCR a lu.
        val doubtful = RecognizedText(
            listOf(TextLine("Une petite phrase lue avec peu de confiance", 0.2f, 0.3f, 0.7f, 0.33f, confidence = 0.2f)),
            1000, 1000,
        )
        val session = session()
        session.onRecognized(doubtful)
        val state = session.state.await { it.step == ScanStep.Done }
        assertTrue(state.cleanedText.contains("petite phrase"), state.cleanedText)
    }
}
