package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PiperPronunciationsTest {
    @Test
    fun saysTheZOfDixBeforeAVowel() = assertEquals(
        "du début du dizneuvième siècle",
        PiperPronunciations.apply("du début du dix-neuvième siècle"),
    )

    @Test
    fun aShortCommaKeepsTheFinalZBeforeAVowel() {
        assertEquals("la frégate Méduse, a marqué", PiperPronunciations.apply("la frégate Méduse a marqué"))
        assertEquals("entre mille cinq cent treize, et mille", PiperPronunciations.apply("entre mille cinq cent treize et mille"))
    }

    @Test
    fun leavesOtherSituationsAlone() {
        assertEquals("treize par un maître", PiperPronunciations.apply("treize par un maître"))
        assertEquals("Le Radeau de la Méduse.", PiperPronunciations.apply("Le Radeau de la Méduse."))
    }
}
