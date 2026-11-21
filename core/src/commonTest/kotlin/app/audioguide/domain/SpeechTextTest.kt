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

    @Test
    fun spellsRomanNumeralsInFrenchAndEnglish() {
        assertEquals(
            "Peint au dix-huitième siècle pour Louis quinze.",
            SpeechText.prepare("Peint au XVIII e siècle pour Louis XV", "fr-FR"),
        )
        assertEquals("Painted in the eighteenth century.", SpeechText.prepare("Painted in the XVIIIth century", "en-GB"))
        assertEquals("Le XVIII e siècle.", SpeechText.prepare("Le XVIII e siècle", "de-DE"))
    }

    @Test
    fun readsOnlyTheBaseOfInclusiveOrOptionalEndings() {
        assertEquals("Prêt par un collectionneur.", SpeechText.prepare("Prêt(e) par un collectionneur", "fr-FR"))
        assertEquals("Les artiste nés à Paris.", SpeechText.prepare("Les artiste(s) né(e)s à Paris", "fr-FR"))
        assertEquals("Prêté par l'auteur.", SpeechText.prepare("Prêté·e par l'auteur.ice", "fr-FR"))
        assertEquals("Les invités sont là.", SpeechText.prepare("Les invité·e·s sont là", "fr-FR"))
        assertEquals("Lent by the artist.", SpeechText.prepare("Lent by the artist(s)", "en-GB"))
        // Une vraie fin de phrase ou une abréviation ne bouge pas.
        assertEquals("Fin de la salle. Entrée libre.", SpeechText.prepare("Fin de la salle. Entrée libre.", "fr-FR"))
    }

    @Test
    fun fixesTildesReadByTheOcrInFrench() {
        assertEquals("Ils fonctionnent grâce à l'ombre.", SpeechText.prepare("Ils fonctionnent grãce à l'ombre", "fr-FR"))
        assertEquals("São Paulo.", SpeechText.prepare("São Paulo", "pt-BR"))
    }
}
