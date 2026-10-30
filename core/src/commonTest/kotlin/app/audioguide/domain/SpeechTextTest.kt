package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTextTest {
    @Test
    fun everyParagraphEndsWithAPunctuationMark() {
        assertEquals("Olympia.\n\nÉdouard Manet.\n\nUne phrase finie !", SpeechText.prepare("Olympia\n\nÉdouard Manet\n\nUne phrase finie !"))
    }

    @Test
    fun linesOfAParagraphAreJoined() {
        assertEquals("Le tableau reprend le motif de la Vénus couchée.", SpeechText.prepare("Le tableau reprend le motif\nde la Vénus couchée"))
    }

    @Test
    fun capitalsAreSoftenedSoTheyAreNotSpelledOut() {
        assertEquals("Jean-René Gauguin (1881-1961) Sculpteur et Céramiste.", SpeechText.prepare("JEAN-RENÉ GAUGUIN (1881-1961) SCULPTEUR ET CÉRAMISTE"))
    }

    @Test
    fun shortCapitalsAndRomanNumeralsAreKept() {
        assertEquals("Le XIV siècle, le MI et l'ADN.", SpeechText.prepare("Le XIV siècle, le MI et l'ADN"))
    }

    @Test
    fun emptyAndBlankTextGivesNothing() {
        assertEquals("", SpeechText.prepare(""))
        assertEquals("", SpeechText.prepare("  \n\n  "))
    }

    @Test
    fun paragraphsAreSplitForPauses() {
        assertEquals(listOf("Un.", "Deux.", "Trois."), SpeechText.paragraphs(SpeechText.prepare("Un\n\nDeux\n\nTrois")))
    }

    @Test
    fun alreadyPunctuatedTextIsLeftAlone() {
        assertEquals("Fin de phrase.", SpeechText.prepare("Fin de phrase."))
        assertEquals("Il demande : « pourquoi ? »", SpeechText.prepare("Il demande : « pourquoi ? »"))
    }
}
