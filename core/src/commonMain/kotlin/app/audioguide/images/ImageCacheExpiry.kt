package app.audioguide.images

/**
 * Durée de vie des images téléchargées (Wikimedia Commons). Le cache est vidé en entier une fois par semaine :
 * une image n'y reste donc jamais plus de [MAX_AGE_MILLIS], plus le temps qui sépare de la prochaine ouverture de l'app.
 */
object ImageCacheExpiry {
    const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    sealed interface Decision {
        /** Rien à faire : le cache a moins d'une semaine. */
        data object Keep : Decision

        /** Premier lancement : noter [since] comme date de départ du cache, sans rien supprimer. */
        data class Start(val since: Long) : Decision

        /** Vider le cache, puis repartir de [since]. */
        data class Clear(val since: Long) : Decision
    }

    /** [since] : date de départ du cache, `null` si elle n'a jamais été notée. */
    fun decide(since: Long?, now: Long): Decision = when {
        since == null -> Decision.Start(now)
        // Horloge reculée (changement manuel de date) : on ne peut plus rien affirmer sur l'âge des images.
        since > now -> Decision.Clear(now)
        now - since >= MAX_AGE_MILLIS -> Decision.Clear(now)
        else -> Decision.Keep
    }
}
