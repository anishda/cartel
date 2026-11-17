package app.audioguide.domain

import app.audioguide.catalog.StoryCleaner

/**
 * Un texte à lire, découpé en segments qu'on peut lancer un par un : c'est ce qui permet la pause, la reprise et le
 * saut à un instant précis, que les moteurs vocaux ne proposent pas. Les durées sont des **estimations** (le moteur
 * ne dit pas combien de temps il va parler) : [factor] les corrige au fil de la lecture.
 */
class ReadingPlan private constructor(
    val segments: List<String>,
    private val baseMillis: List<Long>,
    /** Corrige l'estimation d'après la durée réellement mesurée : 1,0 = l'estimation de départ. */
    val factor: Double = 1.0,
) {
    val isEmpty get() = segments.isEmpty()

    /** Durée estimée du segment [index]. */
    fun durationMillis(index: Int): Long = (baseMillis[index] * factor).toLong()

    /** Instant où commence le segment [index]. */
    fun startMillis(index: Int): Long = (baseMillis.take(index).sum() * factor).toLong()

    val totalMillis: Long get() = (baseMillis.sum() * factor).toLong()

    /** Segment qu'on lit à l'instant [millis] (le dernier si on dépasse la fin). */
    fun indexAt(millis: Long): Int {
        if (segments.isEmpty()) return 0
        var start = 0.0
        for (i in segments.indices) {
            start += baseMillis[i] * factor
            if (millis < start) return i
        }
        return segments.lastIndex
    }

    fun withFactor(factor: Double) = ReadingPlan(segments, baseMillis, factor.coerceIn(MIN_FACTOR, MAX_FACTOR))

    companion object {
        /** Une voix française parle d'environ 15 caractères par seconde à vitesse normale. */
        const val CHARS_PER_SECOND = 15.0
        const val PARAGRAPH_PAUSE_MILLIS = 500L
        private const val TARGET_CHARS = 220
        private const val MAX_CHARS = 320
        private const val MIN_FACTOR = 0.4
        private const val MAX_FACTOR = 2.5

        /** [prepared] : texte passé par [SpeechText.prepare] ; [rate] : vitesse de lecture (1,0 = normale). */
        fun build(prepared: String, rate: Float): ReadingPlan {
            val segments = mutableListOf<String>()
            val millis = mutableListOf<Long>()
            for (paragraph in SpeechText.paragraphs(prepared)) {
                var current = StringBuilder()
                fun flush() {
                    if (current.isNotBlank()) {
                        segments += current.toString().trim()
                    }
                    current = StringBuilder()
                }
                for (sentence in StoryCleaner.sentences(paragraph)) {
                    if (current.isNotEmpty() && current.length + sentence.length + 1 > MAX_CHARS) flush()
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(sentence)
                    if (current.length >= TARGET_CHARS) flush()
                }
                flush()
                // La pause entre deux paragraphes fait partie du dernier segment du paragraphe.
                val last = segments.size
                while (millis.size < last) {
                    val text = segments[millis.size]
                    val speaking = (text.length / (CHARS_PER_SECOND * rate) * 1000).toLong()
                    millis += if (millis.size == last - 1) speaking + PARAGRAPH_PAUSE_MILLIS else speaking
                }
            }
            return ReadingPlan(segments, millis)
        }
    }
}

/** « 1:05 » pour 65 000 ms. */
fun formatClock(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 500) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
