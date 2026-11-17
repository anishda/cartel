package app.audioguide.catalog

/** Une œuvre telle que décrite dans un pack musée. */
data class Artwork(
    /** Identifiant dans le pack : ne survit pas à une nouvelle version du pack. */
    val id: Long,
    val museumId: String,
    /** Référence de la notice source (Joconde) : stable d'une version à l'autre, sert de clé aux favoris. */
    val reference: String,
    val inventoryNo: String,
    val title: String,
    val artist: String?,
    val dateText: String?,
    val domain: String?,
    val denomination: String?,
    val school: String?,
    val technique: String?,
    val heightCm: Double?,
    val widthCm: Double?,
    val location: String?,
    val provenance: String?,
    val description: String?,
    val hasImage: Boolean,
    /** Élément Wikidata de l'œuvre (« Q12418 »), si la liaison par inventaire a été confirmée. */
    val wikidataId: String? = null,
    /** Notoriété : nombre d'articles Wikimedia sur l'œuvre (0 si inconnue). Sert au classement de la recherche. */
    val notability: Int = 0,
    /** Récit de l'œuvre, repris d'un article Wikipedia. */
    val story: ArtworkStory? = null,
    /** Image libre de l'œuvre sur Wikimedia Commons ; l'image elle-même n'est pas dans le pack. */
    val image: ArtworkImage? = null,
)

/**
 * Image d'une œuvre sur Wikimedia Commons, sous licence libre. L'app la charge en ligne quand la fiche s'ouvre
 * et affiche [author] et [license] : les licences CC BY et CC BY-SA l'exigent.
 */
data class ArtworkImage(
    /** Nom du fichier sur Commons, sans le préfixe « File: » : « Mona Lisa.jpg ». */
    val fileName: String,
    val author: String?,
    /** « Public domain », « CC0 », « CC BY-SA 4.0»… */
    val license: String,
) {
    /** Adresse d'une miniature de [width] pixels de large. */
    fun thumbnailUrl(width: Int = 500): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/" + encodeFileName(fileName) + "?width=$width"

    /** Page de l'image sur Commons : auteur, licence et historique complets. */
    val pageUrl: String get() = "https://commons.wikimedia.org/wiki/File:" + encodeFileName(fileName)

    private fun encodeFileName(name: String): String = buildString {
        for (b in name.replace(' ', '_').encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val safe = c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~(),")
            if (safe) append(c.toChar()) else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }
}

/** Récit d'une œuvre, extrait d'un article Wikipedia : à afficher avec son attribution (CC BY-SA). */
data class ArtworkStory(
    val text: String,
    /** Titre de l'article source. */
    val articleTitle: String,
    val articleUrl: String,
)

/** Œuvre prête à être insérée dans un pack (sans identifiant, ni musée : fixés par le pack). */
data class ArtworkDraft(
    val reference: String,
    val inventoryNo: String,
    val title: String,
    val artist: String? = null,
    val dateText: String? = null,
    val domain: String? = null,
    val denomination: String? = null,
    val school: String? = null,
    val technique: String? = null,
    val heightCm: Double? = null,
    val widthCm: Double? = null,
    val location: String? = null,
    val provenance: String? = null,
    val description: String? = null,
    val hasImage: Boolean = false,
    val wikidataId: String? = null,
    val notability: Int = 0,
    val story: ArtworkStory? = null,
    val image: ArtworkImage? = null,
)

/** Description d'un pack, lue dans sa table `meta`. */
data class PackInfo(
    val museumId: String,
    val museumName: String,
    val city: String?,
    val version: String,
    val builtAt: String,
    val sourceName: String,
    val sourceUrl: String?,
    val license: String,
    val sourceUpdatedAt: String?,
    val recordCount: Int,
    /** Licence des récits du pack (« CC BY-SA 4.0 »), absente s'il n'en contient pas. */
    val storyLicense: String? = null,
)
