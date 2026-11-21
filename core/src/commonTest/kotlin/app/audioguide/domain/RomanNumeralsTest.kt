package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RomanNumeralsTest {
    @Test
    fun parsesWellFormedNumeralsOnly() {
        assertEquals(18, RomanNumerals.value("XVIII"))
        assertEquals(14, RomanNumerals.value("XIV"))
        assertEquals(39, RomanNumerals.value("XXXIX"))
        assertNull(RomanNumerals.value("IIX"))
        assertNull(RomanNumerals.value("VV"))
        assertNull(RomanNumerals.value(""))
    }

    @Test
    fun spellsFrenchOrdinals() {
        assertEquals("premier", RomanNumerals.frenchOrdinal(1))
        assertEquals("cinquième", RomanNumerals.frenchOrdinal(5))
        assertEquals("neuvième", RomanNumerals.frenchOrdinal(9))
        assertEquals("onzième", RomanNumerals.frenchOrdinal(11))
        assertEquals("dix-huitième", RomanNumerals.frenchOrdinal(18))
        assertEquals("dix-neuvième", RomanNumerals.frenchOrdinal(19))
        assertEquals("vingtième", RomanNumerals.frenchOrdinal(20))
        assertEquals("vingt et unième", RomanNumerals.frenchOrdinal(21))
    }

    @Test
    fun spellsFrenchCenturiesWrittenProperly() {
        assertEquals("Au dix-huitième siècle.", RomanNumerals.frenchCenturies("Au XVIIIe siècle."))
        assertEquals("dix-neuvième siècle", RomanNumerals.frenchCenturies("XIXème siècle"))
        assertEquals("dix-septième siècle", RomanNumerals.frenchCenturies("XVIIᵉ siècle"))
        assertEquals("premier siècle", RomanNumerals.frenchCenturies("Ier siècle"))
        assertEquals("quinzième et seizième siècles", RomanNumerals.frenchCenturies("XVe et XVIe siècles"))
    }

    @Test
    fun spellsCenturiesAsTheOcrReadsThem() {
        // Le « e » en exposant d'un cartel : détaché, lu comme un degré, ou perdu.
        assertEquals("dix-huitième siècle", RomanNumerals.frenchCenturies("XVIII e siècle"))
        assertEquals("dix-huitième siècle", RomanNumerals.frenchCenturies("XVIII° siècle"))
        assertEquals("dix-huitième siècle", RomanNumerals.frenchCenturies("XVIII siècle"))
        assertEquals("seizième siècles", RomanNumerals.frenchCenturies("XVI siècles"))
    }

    @Test
    fun leavesFrenchLookalikesAlone() {
        assertEquals("Vie de Marie", RomanNumerals.frenchCenturies("Vie de Marie"))
        assertEquals("Le MI et l'ADN", RomanNumerals.spellFrench("Le MI et l'ADN"))
        assertEquals("Chapitre V", RomanNumerals.spellFrench("Chapitre V"))
        assertEquals("Le XV de France", RomanNumerals.spellFrench("Le XV de France"))
    }

    @Test
    fun spellsFrenchRegnalNumbers() {
        assertEquals("acquise par Louis quatorze en 1662", RomanNumerals.spellFrench("acquise par Louis XIV en 1662"))
        assertEquals("Henri deux et Charles sept.", RomanNumerals.spellFrench("Henri II et Charles VII."))
        assertEquals("Louis dix-huit", RomanNumerals.spellFrench("Louis XVIII"))
        assertEquals("François premier", RomanNumerals.spellFrench("François Ier"))
    }

    @Test
    fun spellsEnglishCenturiesAndRegnalNumbers() {
        assertEquals("the eighteenth century", RomanNumerals.spellEnglish("the XVIIIth century"))
        assertEquals("the eighteenth century", RomanNumerals.spellEnglish("the XVIII century"))
        assertEquals("twenty-first century", RomanNumerals.spellEnglish("XXIst century"))
        assertEquals("twentieth centuries", RomanNumerals.spellEnglish("XX centuries"))
        assertEquals("bought by Louis the Fourteenth", RomanNumerals.spellEnglish("bought by Louis XIV"))
        assertEquals("World War II", RomanNumerals.spellEnglish("World War II"))
        assertEquals("Act V", RomanNumerals.spellEnglish("Act V"))
    }
}
