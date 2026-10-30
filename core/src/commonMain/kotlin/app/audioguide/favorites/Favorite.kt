package app.audioguide.favorites

enum class FavoriteKind {
    /** Une œuvre d'un pack musée (retrouvable dans le catalogue tant que le pack est installé). */
    Artwork,

    /** Le texte d'un cartel scanné, qui ne correspond à aucune œuvre connue. */
    Cartel,
}

/**
 * Un favori, avec un **instantané** de ce qu'il faut pour l'afficher et le lire : il reste utilisable
 * même si le pack du musée est supprimé ou mis à jour.
 */
data class Favorite(
    val id: Long,
    val kind: FavoriteKind,
    val museumId: String?,
    val museumName: String?,
    /** Référence Joconde de l'œuvre : stable d'une version de pack à l'autre. */
    val reference: String?,
    val title: String,
    /** Artiste, date, inventaire… */
    val subtitle: String?,
    /** Texte lu à voix haute. */
    val body: String,
    val languageTag: String,
    val note: String?,
    /** Millisecondes depuis 1970. */
    val createdAt: Long,
)
