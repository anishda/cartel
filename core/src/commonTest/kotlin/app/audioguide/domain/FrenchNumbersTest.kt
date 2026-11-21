package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FrenchNumbersTest {
    @Test
    fun spellsUnitsTeensAndTens() {
        assertEquals("zéro", FrenchNumbers.spell(0))
        assertEquals("treize", FrenchNumbers.spell(13))
        assertEquals("dix-sept", FrenchNumbers.spell(17))
        assertEquals("vingt et un", FrenchNumbers.spell(21))
        assertEquals("trente-deux", FrenchNumbers.spell(32))
        assertEquals("soixante et onze", FrenchNumbers.spell(71))
        assertEquals("soixante-dix-neuf", FrenchNumbers.spell(79))
        assertEquals("quatre-vingts", FrenchNumbers.spell(80))
        assertEquals("quatre-vingt-un", FrenchNumbers.spell(81))
        assertEquals("quatre-vingt-dix-sept", FrenchNumbers.spell(97))
    }

    @Test
    fun spellsHundredsAndThousands() {
        assertEquals("cent", FrenchNumbers.spell(100))
        assertEquals("deux cents", FrenchNumbers.spell(200))
        assertEquals("deux cent trois", FrenchNumbers.spell(203))
        assertEquals("mille", FrenchNumbers.spell(1000))
        assertEquals("mille cinq cent treize", FrenchNumbers.spell(1513))
        assertEquals("mille huit cent quatre-vingt-six", FrenchNumbers.spell(1886))
        assertEquals("deux mille", FrenchNumbers.spell(2000))
        assertEquals("deux mille vingt-six", FrenchNumbers.spell(2026))
        assertEquals("neuf mille neuf cent quatre-vingt-dix-neuf", FrenchNumbers.spell(9999))
    }

    @Test
    fun replacesNumbersInTextButNotDecimalsOfOtherShapes() {
        assertEquals(
            "réalisé entre mille cinq cent trois et mille cinq cent treize",
            FrenchNumbers.spellNumbersIn("réalisé entre 1503 et 1513"),
        )
        assertEquals("de soixante-dix-neuf sur cinquante-trois centimètres", FrenchNumbers.spellNumbersIn("de 79 × 53 centimètres"))
        assertEquals("trente virgule cinq centimètres", FrenchNumbers.spellNumbersIn("30,5 centimètres"))
        assertEquals("zéro virgule zéro cinq", FrenchNumbers.spellNumbersIn("0,05"))
    }

    @Test
    fun keepsLargeNumbersAndThousandGroupsAsDigits() {
        assertEquals("avec 20 000 visiteurs", FrenchNumbers.spellNumbersIn("avec 20 000 visiteurs"))
        assertEquals("le numéro 123456", FrenchNumbers.spellNumbersIn("le numéro 123456"))
        assertEquals("version 007", FrenchNumbers.spellNumbersIn("version 007"))
    }
}
