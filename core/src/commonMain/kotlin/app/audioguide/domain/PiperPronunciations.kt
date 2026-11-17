package app.audioguide.domain

/**
 * Corrections de prononciation pour une voix neuronale française (Piper), appliquées au texte avant la phonétisation.
 * Les erreurs relevées à l'écoute : « dix-neuvième » dit sans le z, et le z final de « Méduse » ou « treize » perdu
 * devant une voyelle. Une courte virgule rend le z ; elle ne se voit pas, le texte affiché ne change pas.
 * La liste s'allonge au fil des erreurs entendues.
 */
object PiperPronunciations {
    /** Mots dont le z final se perd devant une voyelle. */
    private val FINAL_Z = listOf("Méduse", "treize", "quatorze", "quinze", "seize", "douze", "onze")

    private val finalZBeforeVowel = Regex("""\b(${FINAL_Z.joinToString("|")})( (?=[aeéèêiouyhAEÉIOU]))""")

    /** Écritures qui obligent le phonétiseur à dire le z de « dix » devant une voyelle. */
    private val respelled = mapOf(
        "dix-neuvième" to "dizneuvième",
        "dix-neuvièmes" to "dizneuvièmes",
    )

    fun apply(text: String): String {
        var result = text
        respelled.forEach { (written, spoken) -> result = result.replace(written, spoken, ignoreCase = true) }
        return finalZBeforeVowel.replace(result) { "${it.groupValues[1]},${it.groupValues[2]}" }
    }
}
