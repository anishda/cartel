package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrenchPhonemizerTest {
    // Prononciations d'espeak-ng mot par mot, comme les écrit le pack-builder.
    private val lexicon = FrenchLexicon.parse(
        listOf(
            FrenchLexicon.line("les", "lˈe-"), FrenchLexicon.line("le", "lˈə-"), FrenchLexicon.line("un", "ˈœ̃"),
            FrenchLexicon.line("est", "ˈɛ"), FrenchLexicon.line("et", "ˈe"), FrenchLexicon.line("homme", "ˈɔm"),
            FrenchLexicon.line("hommes", "ˈɔm"), FrenchLexicon.line("héros", "eʁˈo", aspiratedH = true),
            FrenchLexicon.line("ami", "amˈi"), FrenchLexicon.line("grand", "ɡʁˈɑ̃"), FrenchLexicon.line("ombre", "ˈɔ̃bʁ"),
            FrenchLexicon.line("soleil", "solˈɛj"), FrenchLexicon.line("ils", "ˈil"), FrenchLexicon.line("ont", "ˈɔ̃"),
            FrenchLexicon.line("enfant", "ɑ̃fˈɑ̃"), FrenchLexicon.line("aujourd'hui", "oʒuʁdyˈi"),
            FrenchLexicon.line("cadran", "kadʁˈɑ̃"), FrenchLexicon.line("solaire", "solˈɛʁ"), FrenchLexicon.line("à", "ˈa"),
            FrenchLexicon.line("deux", "dˈø"), FrenchLexicon.line("cent", "sˈɑ̃"), FrenchLexicon.line("voilà", "vwalˈa"),
            FrenchLexicon.line("prêt", "pʁˈɛ"), FrenchLexicon.line("te", "tə-"),
        ).asSequence(),
    )
    private val phonemizer = FrenchPhonemizer(lexicon)

    @Test
    fun parsesTheLexiconFile() {
        val parsed = FrenchLexicon.parse("homme\tˈɔm\nhéros\teʁˈo\th\n\nmauvaise ligne\n")
        assertEquals(2, parsed.size)
        assertEquals("ˈɔm", parsed.phonemes("Homme"))
        assertTrue(parsed.hasAspiratedH("héros"))
    }

    @Test
    fun functionWordsLoseTheirStress() {
        assertEquals("lə- ɡʁˈɑ̃t ˈɔm.", phonemizer.phonemize("Le grand homme."))
    }

    @Test
    fun makesLiaisonsBeforeAVowelButNotBeforeAnAspiratedH() {
        assertEquals("le-z amˈi", phonemizer.phonemize("les ami"))
        assertEquals("le- eʁˈo", phonemizer.phonemize("les héros"))
        assertEquals("ilz ˈɔ̃t œ̃n ɑ̃fˈɑ̃.", phonemizer.phonemize("Ils ont un enfant."))
        // Pas de liaison par-dessus une virgule.
        assertEquals("le-, amˈi", phonemizer.phonemize("les, ami"))
    }

    @Test
    fun joinsElisionsAndKeepsWholeWordsWithAnApostrophe() {
        assertEquals("a lˈɔ̃bʁ", phonemizer.phonemize("à l'ombre"))
        assertEquals("lˈɔm", phonemizer.phonemize("l’homme"))
        assertEquals("oʒuʁdyˈi", phonemizer.phonemize("aujourd'hui"))
    }

    @Test
    fun splitsHyphenatedWordsAndSentences() {
        assertEquals("kadʁˈɑ̃ solˈɛʁ", phonemizer.phonemize("cadran-solaire"))
        assertEquals("tə- vwalˈa pʁˈɛ!\nlə- solˈɛj.", phonemizer.phonemize("te voilà prêt ! Le soleil."))
    }

    @Test
    fun spellsDigitsLeftInTheText() {
        assertEquals("dˈø sˈɑ̃", phonemizer.phonemize("200"))
    }

    @Test
    fun unknownWordsGoThroughSpellingRules() {
        assertEquals("ʁənwˈaʁ", FrenchSpellingRules.phonemize("Renoir"))
        assertEquals("dəɡˈa", FrenchSpellingRules.phonemize("Degas"))
        assertEquals("ʃaʁdˈɛ̃", FrenchSpellingRules.phonemize("Chardin"))
        assertEquals("monˈɛ", FrenchSpellingRules.phonemize("Monet"))
        assertEquals("ʒokˈɔ̃d", FrenchSpellingRules.phonemize("Joconde"))
        assertEquals("ɡaʁsˈɔ̃", FrenchSpellingRules.phonemize("garçon"))
        assertEquals("ʃɑtˈo", FrenchSpellingRules.phonemize("château"))
        assertEquals("muvəmˈɑ̃", FrenchSpellingRules.phonemize("mouvement"))
        assertEquals("ʁˈoz", FrenchSpellingRules.phonemize("rose"))
    }

    @Test
    fun aScannedLabelSentence() {
        val result = phonemizer.phonemize("Accompagné de ce cadran-solaire boussole, te voilà prêt pour voyager dans l'exposition!")
        assertTrue(result.endsWith("!"), result)
        assertTrue("kadʁˈɑ̃ solˈɛʁ" in result, result)
        assertTrue(result.lines().size == 1, result)
    }
}
