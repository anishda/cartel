package app.audioguide.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Les champs de la base Joconde (ministère de la Culture) dont l'app a besoin. */
@Serializable
data class JocondeRecord(
    @SerialName("Reference") val reference: String? = null,
    @SerialName("Numero_inventaire") val inventoryNumber: String? = null,
    @SerialName("Titre") val title: String? = null,
    @SerialName("Auteur") val author: String? = null,
    @SerialName("Denomination") val denomination: String? = null,
    @SerialName("Domaine") val domain: String? = null,
    @SerialName("Ecole_pays") val school: String? = null,
    @SerialName("Millesime_de_creation") val creationYear: String? = null,
    @SerialName("Periode_de_creation") val creationPeriod: String? = null,
    @SerialName("Materiaux_techniques") val materials: String? = null,
    @SerialName("Mesures") val measures: String? = null,
    @SerialName("Localisation") val location: String? = null,
    @SerialName("Historique") val history: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Presence_image") val hasImage: Boolean? = null,
)

/** Transforme une notice Joconde en œuvre de pack. */
object JocondeMapper {
    /** Retourne `null` pour une notice inexploitable (sans référence, titre ou inventaire). */
    fun toDraft(record: JocondeRecord, museumName: String): ArtworkDraft? {
        val reference = record.reference?.trim().orEmpty()
        val inventory = record.inventoryNumber?.substringBefore(';')?.trim().orEmpty()
        val title = formatTitle(record.title)
        if (reference.isEmpty() || inventory.isEmpty() || title.isNullOrEmpty()) return null

        val dimensions = parseDimensions(record.measures)
        return ArtworkDraft(
            reference = reference,
            inventoryNo = inventory,
            title = title,
            artist = ArtistFormatter.format(record.author),
            dateText = formatDate(record.creationYear, record.creationPeriod),
            domain = record.domain?.substringBefore(';')?.clean(),
            denomination = record.denomination?.clean(),
            school = record.school?.clean(),
            technique = record.materials?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")?.ifEmpty { null },
            heightCm = dimensions.first,
            widthCm = dimensions.second,
            location = formatLocation(record.location, museumName),
            provenance = record.history?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("; ")?.ifEmpty { null },
            description = record.description?.clean(),
            hasImage = record.hasImage == true,
        )
    }

    /** Les titres en capitales (« LE VERRE DE VIN ») deviennent « Le verre de vin ». */
    fun formatTitle(raw: String?): String? {
        val title = raw?.trim()?.replace(Regex("""\s+"""), " ")?.ifEmpty { null } ?: return null
        val letters = title.filter { it.isLetter() }
        return if (letters.length > 3 && letters.all { it.isUpperCase() }) {
            title.lowercase().replaceFirstChar { it.uppercaseChar() }
        } else {
            title
        }
    }

    /** Un millésime seul (« 1863 ») l'emporte ; sinon la période (« 2e quart XVIIe siècle »). */
    fun formatDate(year: String?, period: String?): String? {
        val y = year?.trim().orEmpty()
        if (Regex("""\d{3,4}""").matches(y)) return y
        val firstPeriod = period?.split(';')?.firstOrNull()?.trim()?.ifEmpty { null } ?: return null
        return centuryToRoman(firstPeriod)
    }

    /** « 17e siècle » → « XVIIe siècle », « 1ère moitié » → « 1re moitié ». */
    private fun centuryToRoman(text: String): String =
        text.replace(Regex("""(\d{1,2})(?:e|ème|ère|er|re)\s+siècle""")) { match ->
            val n = match.groupValues[1].toInt()
            val roman = toRoman(n)
            if (n == 1) "Ier siècle" else "${roman}e siècle"
        }.replace("1ère", "1re")

    private fun toRoman(n: Int): String {
        val numerals = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var rest = n
        return buildString {
            for ((value, symbol) in numerals) while (rest >= value) { append(symbol); rest -= value }
        }
    }

    /** « 77 H ; 53 L » ou « H. 17, l. 30.5 » (centimètres) → hauteur et largeur. */
    fun parseDimensions(measures: String?): Pair<Double?, Double?> {
        if (measures.isNullOrBlank()) return null to null
        val number = """(\d+(?:[.,]\d+)?)"""
        fun find(vararg patterns: String): Double? = patterns.firstNotNullOfOrNull { pattern ->
            Regex(pattern).find(measures)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        }
        val height = find("""$number\s*H\b""", """\bH\.?\s*$number""")
        val width = find("""$number\s*[Ll]\b""", """\b[Ll]\.\s*$number""")
        return height to width
    }

    /** « Paris ; musée du Louvre département des Peintures » → « département des Peintures ». */
    fun formatLocation(raw: String?, museumName: String): String? {
        val afterCity = raw?.substringAfterLast(';')?.trim()?.ifEmpty { null } ?: return null
        val museumNorm = normalizeForSearch(museumName)
        val locationNorm = normalizeForSearch(afterCity)
        if (locationNorm == museumNorm) return null
        val withoutMuseum = if (locationNorm.startsWith(museumNorm)) {
            afterCity.drop(museumName.length).trim().ifEmpty { null }
        } else {
            afterCity
        }
        return withoutMuseum
    }

    private fun String.clean(): String? = trim().replace(Regex("""\s+"""), " ").ifEmpty { null }
}
