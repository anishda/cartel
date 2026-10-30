package app.audioguide.domain

/** Zone d'une image, en fractions de sa largeur et de sa hauteur (0 à 1, origine en haut à gauche). */
data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** OCR sur l'appareil, sans réseau. Implémenté en natif (ML Kit sur Android, Vision sur iOS). */
interface TextRecognizer {
    /**
     * Lit [image] (JPEG ou PNG, orientation EXIF comprise) : lignes avec leur position, taille de l'image lue.
     * Si [region] est donnée, seule cette zone est lue ; [scale] l'agrandit avant la lecture (les petits textes se lisent
     * bien mieux agrandis). Les positions rendues se rapportent à l'image ainsi recadrée et agrandie.
     */
    suspend fun recognize(image: ByteArray, region: Region? = null, scale: Float = 1f): Result<RecognizedText>
}
