package app.audioguide.domain

/**
 * Découpe [text] en morceaux d'au plus [maxLength] caractères, en coupant de préférence
 * à la fin d'une phrase, sinon sur un espace. Les moteurs vocaux refusent les textes trop longs
 * (Android : `TextToSpeech.getMaxSpeechInputLength()`).
 */
fun splitForSpeech(text: String, maxLength: Int): List<String> {
    require(maxLength > 0)
    val chunks = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > maxLength) {
        val window = rest.substring(0, maxLength)
        val cut = listOf(window.lastIndexOf(". "), window.lastIndexOf("\n"), window.lastIndexOf(' '))
            .firstOrNull { it > 0 }
            ?.let { it + 1 }
            ?: maxLength
        chunks += rest.substring(0, cut).trim()
        rest = rest.substring(cut).trim()
    }
    if (rest.isNotEmpty()) chunks += rest
    return chunks
}
