package app.audioguide.packbuilder

import app.audioguide.domain.FrenchLexicon
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LexiconBuilderTest {
    /** Phonétiseur factice : « les » + mot en h aspiré ne fait pas de liaison. */
    private val phonemizer = Phonemizer { texts ->
        texts.map { text ->
            when {
                text == "les héros" -> "le- eʁˈo"
                text.startsWith("les h") -> "le-z " + text.removePrefix("les h")
                else -> text.reversed()
            }
        }
    }

    @Test
    fun writesWordsWithTheirPhonemesAndFlagsAspiratedH() {
        val out = Files.createTempDirectory("lexicon").resolve("lexicon-fr.tsv.gz")
        val count = LexiconBuilder(phonemizer, log = {}).build(setOf("homme", "héros", "chat", "pas un mot!", ""), out)
        assertEquals(3, count)
        val lexicon = GZIPInputStream(Files.newInputStream(out)).bufferedReader().useLines { FrenchLexicon.parse(it) }
        assertEquals("tahc", lexicon.phonemes("chat"))
        assertTrue(lexicon.hasAspiratedH("héros"))
        assertFalse(lexicon.hasAspiratedH("homme"))
    }

    @Test
    fun collectsWordsAsTheVoiceWillReadThem() {
        val words = LexiconBuilder.words("L’homme a peint 3 tableaux au XVIIIe siècle").toSet()
        assertTrue("l'homme" in words)
        assertTrue("tableaux" in words)
    }

    @Test
    fun readsTheFirstColumnOfAWordFile() {
        val file = Files.createTempFile("words", ".tsv")
        Files.writeString(file, "Joconde\tʒ o k ˈɔ̃ d\nl'homme\tl ˈɔ m\n123\tx\n")
        assertEquals(setOf("joconde", "l'homme"), LexiconBuilder(phonemizer, log = {}).wordsOfFile(file))
    }
}
