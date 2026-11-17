package app.audioguide.packbuilder

/** Un musée à empaqueter et la façon de retrouver ses notices dans Joconde. */
data class MuseumSpec(
    val id: String,
    /** Nom officiel dans Joconde (sert aussi à retirer le nom du musée des localisations). */
    val name: String,
    val city: String,
    /** Code Muséofile du musée. */
    val museofileCode: String,
    /** Domaines Joconde retenus : on garde les œuvres exposées d'ordinaire, pas les réserves ni les objets. */
    val domains: List<String> = listOf("peinture", "sculpture"),
    /** Élément Wikidata du musée : ses collections servent à relier les œuvres à Wikidata et Wikipedia. */
    val wikidataId: String? = null,
)

object Museums {
    val all = listOf(
        MuseumSpec("louvre", "musée du Louvre", "Paris", "M5031", wikidataId = "Q19675"),
        MuseumSpec("orsay", "musée d'Orsay", "Paris", "M5060", wikidataId = "Q23402"),
        MuseumSpec("orangerie", "musée de l'Orangerie des Tuileries", "Paris", "M5030", wikidataId = "Q726781"),
    )

    fun byId(id: String): MuseumSpec? = all.firstOrNull { it.id == id }
}

object JocondeSource {
    const val NAME = "Base Joconde, ministère de la Culture (POP)"
    const val DATASET_ID = "5b448216a3a729752eb11d6f"
    const val DATASET_URL = "https://www.data.gouv.fr/fr/datasets/$DATASET_ID"
    const val LICENSE = "Licence Ouverte 2.0 (Etalab)"
}
