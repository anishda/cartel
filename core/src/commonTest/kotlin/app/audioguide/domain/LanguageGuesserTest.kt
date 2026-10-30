package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageGuesserTest {
    @Test
    fun recognisesFrench() {
        assertEquals("fr-FR", LanguageGuesser.guess("Le tableau reprend le motif de la Vénus couchée dans le Paris du XIXe siècle"))
    }

    @Test
    fun recognisesEnglish() {
        assertEquals("en-US", LanguageGuesser.guess("The painting was shown at the Salon of 1865 and it caused a scandal"))
    }

    @Test
    fun recognisesOtherLanguages() {
        assertEquals("es-ES", LanguageGuesser.guess("El cuadro fue presentado en el Salón y es una obra de su autor"))
        assertEquals("it-IT", LanguageGuesser.guess("Il dipinto è stato esposto nel Salon e la sua storia è molto lunga"))
        assertEquals("de-DE", LanguageGuesser.guess("Das Gemälde wurde im Salon gezeigt und ist ein Werk von großer Bedeutung"))
    }

    @Test
    fun staysSilentWhenUnsure() {
        assertNull(LanguageGuesser.guess("Olympia 1863"))
        assertNull(LanguageGuesser.guess(""))
        assertNull(LanguageGuesser.guess("Manet Édouard huile sur toile"))
    }
}
