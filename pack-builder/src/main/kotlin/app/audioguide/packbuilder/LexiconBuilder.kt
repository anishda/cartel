package app.audioguide.packbuilder

import app.audioguide.catalog.ArtworkDraft
import app.audioguide.domain.FrenchLexicon
import app.audioguide.domain.SpeechText
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Fabrique le lexique français de la voix neuronale (`lexicon-fr.tsv.gz`), que l'app utilise pour phonétiser les textes
 * absents des packs (cartels scannés) : voir `FrenchLexicon` et `FrenchPhonemizer` dans `:core`.
 *
 * Les mots viennent d'une liste de vocabulaire (la colonne des mots de `fr_lexicon.tsv`, base espeak de gruut, MIT :
 * https://huggingface.co/beshkenadze/kokoro-ipa-lexicons) et des textes des packs (titres, artistes, récits…). Leur
 * prononciation est recalculée mot par mot par le phonétiseur des packs (espeak-ng, outil seulement) : mêmes symboles
 * que les phonèmes des packs, sans les liaisons figées de la liste d'origine.
 */
class LexiconBuilder(private val phonemizer: Phonemizer, private val log: (String) -> Unit = ::println) {
    /** Les mots d'un pack, en minuscules, tels que la voix les lira. */
    fun wordsOf(drafts: List<ArtworkDraft>): Set<String> = drafts.asSequence()
        .flatMap { listOfNotNull(it.title, it.artist, it.technique, it.school, it.location, it.denomination, it.domain, it.dateText, it.story?.text) }
        .flatMap { words(SpeechText.prepare(it, "fr-FR")) }
        .toSet()

    /** Les mots de la première colonne d'un fichier `mot\tphonèmes`. */
    fun wordsOfFile(file: Path): Set<String> =
        Files.readAllLines(file).mapNotNull { line -> line.substringBefore('\t').lowercase().takeIf(::isWord) }.toSet()

    fun build(words: Set<String>, out: Path): Int {
        val sorted = words.filter(::isWord).sorted()
        log("Lexique : phonétisation de ${sorted.size} mots…")
        val phonemes = phonemizer.phonemize(sorted).map { it.replace('\n', ' ').trim() }
        // h aspiré : espeak ne fait pas la liaison dans « les héros ».
        val hWords = sorted.filter { it.startsWith('h') }
        val aspirated = phonemizer.phonemize(hWords.map { "les $it" }).zip(hWords)
            .filter { (spoken, _) -> !spoken.startsWith("le-z") && !spoken.startsWith("lez") }
            .map { it.second }.toSet()
        var count = 0
        Files.createDirectories(out.parent)
        GZIPOutputStream(Files.newOutputStream(out)).bufferedWriter(Charsets.UTF_8).use { writer ->
            sorted.forEachIndexed { i, word ->
                if (phonemes[i].isEmpty()) return@forEachIndexed
                writer.write(FrenchLexicon.line(word, phonemes[i], word in aspirated))
                writer.write("\n")
                count++
            }
        }
        log("  → $count mots, ${aspirated.size} h aspirés, ${Files.size(out) / 1024} Ko : $out")
        return count
    }

    companion object {
        private val WORD = Regex("""[\p{L}]+(?:['’\-][\p{L}]+)*""")

        fun words(text: String): Sequence<String> = WORD.findAll(text).map { it.value.lowercase().replace('’', '\'') }

        fun isWord(word: String): Boolean = word.length in 1..30 && WORD.matches(word)
    }
}
