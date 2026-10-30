package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechChunksTest {
    @Test
    fun shortTextStaysWhole() {
        assertEquals(listOf("Bonjour."), splitForSpeech("Bonjour.", 100))
    }

    @Test
    fun cutsAtSentenceEnd() {
        val chunks = splitForSpeech("Première phrase. Seconde phrase plus longue.", 25)
        assertEquals(listOf("Première phrase.", "Seconde phrase plus", "longue."), chunks)
    }

    @Test
    fun neverExceedsMaxLength() {
        val text = "mot ".repeat(500)
        val chunks = splitForSpeech(text, 60)
        assertTrue(chunks.all { it.length <= 60 }, chunks.maxOf { it.length }.toString())
        assertEquals(text.trim().replace(Regex("\\s+"), " "), chunks.joinToString(" "))
    }

    @Test
    fun hardCutsWordLongerThanLimit() {
        val chunks = splitForSpeech("a".repeat(25), 10)
        assertEquals(listOf("a".repeat(10), "a".repeat(10), "a".repeat(5)), chunks)
    }
}
