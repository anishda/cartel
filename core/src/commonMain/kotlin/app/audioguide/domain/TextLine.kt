package app.audioguide.domain

/**
 * Une ligne lue par l'OCR avec sa position. Coordonnées normalisées entre 0 et 1, origine en haut à gauche
 * (en haut = 0), rapportées à l'image telle qu'elle a été lue, après rotation.
 */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Confiance de l'OCR de 0 à 1 (1 si le moteur n'en donne pas). */
    val confidence: Float = 1f,
)

/** Résultat d'un OCR : les lignes et la taille de l'image, qui donne le rapport largeur/hauteur. */
data class RecognizedText(
    val lines: List<TextLine>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    /** Tout ce que l'OCR a vu, de haut en bas puis de gauche à droite (« texte brut »). */
    val plainText: String
        get() = lines.sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { it.text }

    companion object {
        /**
         * Texte sans position (tests, ou moteur qui n'en donne pas) : une ligne par ligne du texte, empilées ; une
         * ligne vide laisse un blanc, qui sépare deux paragraphes.
         */
        fun fromPlainText(text: String): RecognizedText {
            val rows = text.lines().map { it.trim() }
            val pitch = 1f / (rows.size + 1)
            return RecognizedText(
                lines = rows.mapIndexedNotNull { i, row ->
                    if (row.isEmpty()) null else TextLine(row, left = 0.1f, top = pitch * (i + 0.5f), right = 0.9f, bottom = pitch * (i + 0.5f) + pitch * 0.6f)
                },
                imageWidth = 1000,
                imageHeight = 1000,
            )
        }
    }
}
