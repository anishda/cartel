package app.audioguide.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryCleanerTest {
    @Test
    fun readingDropsInterwikiLinksAndEmptyParentheses() = assertEquals(
        "Elle demeure une icône médiatique au fil de ses détournements. Olympia est un tableau.",
        StoryCleaner.forReading("Elle demeure une icône médiatique (de) au fil de ses détournements. Olympia () est un tableau."),
    )

    @Test
    fun listeningDropsParenthesesAndLifespans() = assertEquals(
        "La Joconde, ou Portrait de Mona Lisa, est un tableau de Léonard de Vinci. Le Radeau de la Méduse est peint par Théodore Géricault.",
        StoryCleaner.forListening(
            "La Joconde (en italien : La Gioconda ou Monna Lisa), ou Portrait de Mona Lisa, est un tableau de Léonard de Vinci. " +
                "Le Radeau de la Méduse est peint par Théodore Géricault (1791-1824).",
        ),
    )

    @Test
    fun listeningDropsShortSentencesThatOnlySayWhereTheWorkIs() = assertEquals(
        "Une rue à Laghouat est un tableau d'Étienne Dinet.",
        StoryCleaner.forListening("Une rue à Laghouat est un tableau d'Étienne Dinet. Il est conservé au musée d'Orsay à Paris."),
    )

    @Test
    fun keepsLongSentencesThatMentionTheMuseumAmongOtherFacts() {
        val sentence = "Très probablement acquise par François Ier, cette peinture à l'huile sur panneau de bois de peuplier de 79 × 53 cm " +
            "est exposée au musée du Louvre à Paris depuis la Révolution, après avoir orné les appartements du roi."
        assertEquals(sentence, StoryCleaner.forListening(sentence))
    }

    @Test
    fun keepsParagraphs() = assertEquals(
        "Premier paragraphe.\nSecond paragraphe.",
        StoryCleaner.forListening("Premier paragraphe.\n\nSecond paragraphe."),
    )

    @Test
    fun firstSentencesStopAfterTheCount() = assertEquals(
        "Le tableau fut acquis par François Ier. Puis par Louis XIV.",
        StoryCleaner.firstSentences("Le tableau fut acquis par François Ier. Puis par Louis XIV. Ensuite autre chose.", 2),
    )

    @Test
    fun firstSentencesStopBeforeTheLengthLimitButKeepTheFirst() {
        assertEquals("Une phrase.", StoryCleaner.firstSentences("Une phrase. Une autre phrase.", 3, maxChars = 15))
        assertEquals("Une phrase bien trop longue.", StoryCleaner.firstSentences("Une phrase bien trop longue. Fin.", 3, maxChars = 5))
    }

    @Test
    fun sentencesKeepInitials() = assertEquals(
        listOf("Peint par J. Dupont en 1820.", "Fin."),
        StoryCleaner.sentences("Peint par J. Dupont en 1820. Fin."),
    )
}
