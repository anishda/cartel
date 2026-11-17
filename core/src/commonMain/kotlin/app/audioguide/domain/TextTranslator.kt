package app.audioguide.domain

/**
 * Traduction de texte sur l'appareil. Android : ML Kit Translation, dont le modèle de chaque langue (environ 30 Mo) se
 * télécharge une première fois puis reste sur l'appareil. Les langues sont données par leur code court (`en`, `fr`).
 */
interface TextTranslator {
    /** Faux quand la plateforme ne sait pas traduire : l'app ne propose alors pas la traduction. */
    val isAvailable: Boolean

    /** Vrai si [from] peut être traduit vers [to]. */
    fun supports(from: String, to: String): Boolean

    /** Traduit [text]. Peut télécharger le modèle de langue (réseau nécessaire la première fois seulement). */
    suspend fun translate(text: String, from: String, to: String): Result<String>
}

/** Pour les plateformes sans traduction embarquée. */
object NoTranslator : TextTranslator {
    override val isAvailable = false
    override fun supports(from: String, to: String) = false
    override suspend fun translate(text: String, from: String, to: String): Result<String> =
        Result.failure(UnsupportedOperationException("Translation is not available on this platform"))
}
