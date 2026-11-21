package app.audioguide.domain

/** Reconnaissance des numéros d'inventaire des musées nationaux (RF, INV, MI, MR, OA…) dans un texte. */
internal object InventoryNumbers {
    private const val CODES = "RF|INV|MI|MR|OA|AGR|MNR|LP|MV"
    private const val DIGITS = """\d+(?:[ .\-/]\d+)*(?:\s[A-Z]\b)?"""

    /** Ligne qui ne contient qu'un numéro d'inventaire, éventuellement précédé de « Inv. ». */
    val wholeLine = Regex(
        """^\s*(?:inv(?:entaire)?\.?\s*:?\s*)?(?:$CODES)\s?$DIGITS\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Numéro à l'intérieur d'une phrase. Sensible à la casse : les codes sont en majuscules. */
    val inline = Regex("""\b(?:$CODES)\s?$DIGITS""")

    /**
     * Mention « Inv. », « Inventaire : » restée seule après le retrait du numéro. Un mot entier suivi d'un point, de
     * deux-points ou de la fin : « inventé » et « l'inventaire du roi » ne bougent pas.
     */
    val prefix = Regex("""\b(?:inventaire|inv)\b(?:\s*[.:]+\s*|\s*$)""", RegexOption.IGNORE_CASE)

    fun find(text: String): List<String> = inline.findAll(text).map { it.value.trim() }.toList()
}
