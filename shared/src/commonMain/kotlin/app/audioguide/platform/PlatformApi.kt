package app.audioguide.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.audioguide.domain.RecognizedText

/** Où ranger les données de l'app sur l'appareil. */
class AppPaths(val packsDir: String, val userDb: String, val settingsFile: String)

/** Informations sur l'app installée. */
class AppInfo(val versionName: String)

/** Adresse du serveur qui publie `manifest.json` et les packs. */
class PackConfig(val baseUrl: String)

/** Étiquette BCP 47 de la langue du système, par exemple `fr-FR`. */
expect fun systemLanguageTag(): String

/**
 * Sélecteur de photo du système. Retourne l'action qui l'ouvre ; [onImage] reçoit l'image choisie
 * (JPEG ou PNG, orientation EXIF comprise).
 */
@Composable
expect fun rememberPhotoImporter(onImage: (ByteArray) -> Unit): () -> Unit

/** Ouvre la feuille de partage du système (« Enregistrer dans Fichiers », Obsidian, Messages…). */
@Composable
expect fun rememberTextSharer(): (title: String, text: String) -> Unit

/** Millisecondes depuis 1970. */
expect fun currentTimeMillis(): Long

/** Décalage du fuseau horaire de l'appareil par rapport à UTC, en millisecondes (heure d'été comprise). */
expect fun localTimeOffsetMillis(): Long

enum class LiveScannerStatus { Starting, Scanning, PermissionDenied, Unsupported }

/**
 * Aperçu de l'appareil photo avec lecture du texte en continu. [onText] reçoit les lignes actuellement visibles,
 * avec leur position, chaque fois qu'elles changent.
 */
@Composable
expect fun LiveTextScanner(
    modifier: Modifier,
    onText: (RecognizedText) -> Unit,
    onStatus: (LiveScannerStatus) -> Unit,
)
