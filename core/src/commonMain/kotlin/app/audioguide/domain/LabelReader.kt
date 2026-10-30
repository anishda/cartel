package app.audioguide.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Lit une photo de cartel en une ou deux passes. Les moteurs d'OCR lisent mal les petits textes : si la première
 * lecture montre un texte petit ou douteux, on relit la zone du cartel recadrée et agrandie, et on garde la meilleure.
 * (Sur une bannière de 337 px, la confiance passe de 0,87 à 1,00 et « prusieurs » redevient « plusieurs ».)
 */
class LabelReader(private val recognizer: TextRecognizer) {
    suspend fun read(image: ByteArray): Result<RecognizedText> {
        val first = recognizer.recognize(image).getOrElse { return Result.failure(it) }

        val lineHeight = medianLineHeight(first)
        val needsSecondPass = if (first.lines.isEmpty()) {
            max(first.imageWidth, first.imageHeight) <= SMALL_IMAGE_SIDE
        } else {
            lineHeight < SMALL_TEXT_PX || averageConfidence(first) < LOW_CONFIDENCE
        }
        if (!needsSecondPass) return Result.success(first)

        val region = regionOf(first)
        val doubtful = first.lines.isNotEmpty() && averageConfidence(first) < LOW_CONFIDENCE
        val scale = scaleFor(first, region, lineHeight, doubtful)
        if (scale < MIN_USEFUL_SCALE) return Result.success(first)

        val second = recognizer.recognize(image, region, scale).getOrNull() ?: return Result.success(first)
        return Result.success(if (score(second) >= BETTER_BY * score(first)) second else first)
    }

    /** Hauteur typique d'une ligne de texte, en pixels de l'image lue (0 si rien n'est lu). */
    private fun medianLineHeight(text: RecognizedText): Float {
        val heights = text.lines.filter { it.text.length >= 8 }.map { (it.bottom - it.top) * text.imageHeight }.sorted()
        return if (heights.isEmpty()) 0f else heights[heights.size / 2]
    }

    private fun averageConfidence(text: RecognizedText): Float {
        val chars = text.lines.sumOf { it.text.length }
        return if (chars == 0) 0f else text.lines.sumOf { it.confidence.toDouble() * it.text.length }.toFloat() / chars
    }

    /** Ce qui a été lu compte pour sa quantité et sa fiabilité. */
    private fun score(text: RecognizedText): Float =
        text.lines.sumOf { it.text.length * it.confidence.toDouble() * it.confidence }.toFloat()

    /** Zone qui contient tout le texte lu, avec une marge ; toute l'image si rien n'a été lu. */
    private fun regionOf(text: RecognizedText): Region? {
        val lines = text.lines
        if (lines.isEmpty()) return null
        val marginX = 0.06f
        val marginY = 0.06f
        return Region(
            left = max(0f, lines.minOf { it.left } - marginX),
            top = max(0f, lines.minOf { it.top } - marginY),
            right = min(1f, lines.maxOf { it.right } + marginX),
            bottom = min(1f, lines.maxOf { it.bottom } + marginY),
        )
    }

    /** Agrandissement pour que le texte atteigne ~[TARGET_TEXT_PX] de haut, sans dépasser [MAX_SIDE_PX] de côté. */
    private fun scaleFor(text: RecognizedText, region: Region?, lineHeight: Float, doubtful: Boolean): Float {
        val cropWidth = text.imageWidth * (region?.width ?: 1f)
        val cropHeight = text.imageHeight * (region?.height ?: 1f)
        var wanted = if (lineHeight > 0) TARGET_TEXT_PX / lineHeight else DEFAULT_SCALE
        // Texte assez grand mais douteux (flou, reflets) : un léger agrandissement lisse l'image avant la relecture.
        if (doubtful) wanted = max(wanted, DOUBTFUL_SCALE)
        val limit = MAX_SIDE_PX / max(cropWidth, cropHeight)
        return min(min(wanted, MAX_SCALE), limit)
    }

    private companion object {
        const val SMALL_TEXT_PX = 48f
        const val LOW_CONFIDENCE = 0.9f
        const val SMALL_IMAGE_SIDE = 2400
        const val TARGET_TEXT_PX = 44f
        const val DEFAULT_SCALE = 2f
        const val MAX_SCALE = 3f
        const val MAX_SIDE_PX = 3600f
        const val DOUBTFUL_SCALE = 1.5f
        const val MIN_USEFUL_SCALE = 1.3f
        const val BETTER_BY = 1.05f
    }
}
