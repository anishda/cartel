package app.audioguide.catalog

import app.audioguide.domain.PiperPronunciations
import app.audioguide.domain.ReadingPlan
import app.audioguide.domain.SpeechText

/**
 * Phonèmes précalculés des textes lus, pour la voix neuronale (Piper). Le moteur ne sait plus transformer du texte
 * français en phonèmes (espeak-ng, sous GPL, n'est pas dans l'app) : le pack-builder le fait à la fabrication, segment
 * de lecture par segment, et l'app retrouve les phonèmes d'un segment par sa [key].
 */
object NarrationPhonemes {
    /** Les segments lus pour [artwork], dans les deux écoutes : exactement ceux que découpe `ReadingPlan` dans l'app. */
    fun segments(artwork: Artwork, museumName: String): Set<String> {
        val texts = listOf(ArtworkNarration.buildShort(artwork), ArtworkNarration.build(artwork, museumName))
        return texts.flatMap { segmentsOf(it) }.toSet()
    }

    /** Segments d'un texte tel que l'app le prépare avant lecture (rythme de lecture sans effet sur le découpage). */
    fun segmentsOf(text: String): List<String> =
        ReadingPlan.build(SpeechText.prepare(text, ArtworkNarration.LANGUAGE_TAG), 1f).segments

    /** Ce que le phonétiseur reçoit pour un segment : le texte avec les corrections de prononciation de la voix. */
    fun textToPhonemize(segment: String): String = PiperPronunciations.apply(segment)

    /**
     * Clé d'un segment dans le pack : empreinte FNV-1a 64 bits de son texte en UTF-8, en hexadécimal. Calculée à
     * la main : la même valeur sur la JVM, Android et iOS, sans dépendance.
     */
    fun key(segment: String): String {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (byte in segment.encodeToByteArray()) {
            hash = (hash xor (byte.toLong() and 0xFF)) * 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}

/** L'œuvre telle qu'elle sera lue depuis un pack : sans identifiant, que le pack fixe à l'écriture. */
fun ArtworkDraft.toArtwork(museumId: String): Artwork = Artwork(
    id = 0, museumId = museumId, reference = reference, inventoryNo = inventoryNo, title = title, artist = artist,
    dateText = dateText, domain = domain, denomination = denomination, school = school, technique = technique,
    heightCm = heightCm, widthCm = widthCm, location = location, provenance = provenance, description = description,
    hasImage = hasImage, wikidataId = wikidataId, notability = notability, story = story, image = image,
)
