package app.audioguide.catalog

import app.audioguide.domain.CartelCleaner

/** Texte lu à voix haute pour la fiche d'une œuvre. Les données sources sont en français : la lecture aussi. */
object ArtworkNarration {
    const val LANGUAGE_TAG = "fr-FR"

    fun build(artwork: Artwork, museumName: String): String {
        val parts = mutableListOf<String>()
        parts += sentence(artwork.title)
        artwork.artist?.let { parts += sentence("Par $it") }
        artwork.dateText?.let { parts += sentence(it) }
        artwork.school?.let { parts += sentence("École : $it") }
        artwork.technique?.let { parts += sentence("Technique : $it") }
        dimensions(artwork.heightCm, artwork.widthCm)?.let { parts += sentence(it) }
        parts += sentence(
            "Conservé au $museumName" + (artwork.location?.let { ", $it" } ?: ""),
        )
        artwork.provenance?.let { parts += sentence("Historique : $it") }
        // Le nettoyeur du cartel développe siècles et abréviations ; il n'y a pas de numéro d'inventaire à lire.
        return CartelCleaner.clean(parts.joinToString("\n"))
    }

    fun dimensions(heightCm: Double?, widthCm: Double?): String? = when {
        heightCm != null && widthCm != null -> "Dimensions : ${cm(heightCm)} centimètres de haut sur ${cm(widthCm)} de large"
        heightCm != null -> "Hauteur : ${cm(heightCm)} centimètres"
        widthCm != null -> "Largeur : ${cm(widthCm)} centimètres"
        else -> null
    }

    private fun cm(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

    private fun sentence(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.last() in ".!?") trimmed else "$trimmed."
    }
}
