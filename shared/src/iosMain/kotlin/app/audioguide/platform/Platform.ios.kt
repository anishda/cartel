package app.audioguide.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import app.audioguide.domain.RecognizedText
import app.audioguide.domain.TextLine
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.preferredLanguages
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

actual fun systemLanguageTag(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en-US"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun localTimeOffsetMillis(): Long = NSTimeZone.localTimeZone.secondsFromGMT * 1000L

/** Feuille de partage du système, pour exporter les favoris en Markdown. */
@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = remember {
    { _, text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }
}

/** Photothèque du système via `UIImagePickerController` (le sélecteur tourne hors de l'app : pas de permission). */
@Composable
actual fun rememberPhotoImporter(onImage: (ByteArray) -> Unit): () -> Unit {
    val currentOnImage by rememberUpdatedState(onImage)
    val delegate = remember { PickerDelegate { currentOnImage(it) } }
    return remember { { presentLibrary(delegate) } }
}

private fun presentLibrary(delegate: PickerDelegate) {
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull()
    var controller = scene?.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

// `UIImagePickerController.delegate` est une référence faible : le delegate est gardé par `remember`.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PickerDelegate(
    private val onImage: (ByteArray) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val data = image?.let { UIImageJPEGRepresentation(it, 0.9) } ?: return
        onImage(data.toByteArray())
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/**
 * Le scan en direct s'appuie sur `DataScannerViewController` (VisionKit), écrit en Swift dans `iosApp`
 * (cette classe n'est pas exposée à Kotlin/Native). Swift enregistre son implémentation au démarrage.
 */
interface LiveScannerProvider {
    /** Vrai si l'appareil sait scanner (iPhone récent, caméra autorisée). Faux sur simulateur. */
    fun isSupported(): Boolean

    /** [onText] reçoit les lignes visibles, avec leur position, chaque fois qu'elles changent. */
    fun makeViewController(onText: (LiveTextFrame) -> Unit): UIViewController
}

/**
 * Une ligne lue par VisionKit, côté Swift. On passe par ces classes (et non par les types de `:core`, qui ne sont pas
 * exportés vers Swift) ; la position est normalisée de 0 à 1 dans la vue, l'origine en haut à gauche.
 */
class LiveLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float,
)

/** Toutes les lignes visibles à un instant ; [width] et [height] : taille de la vue, en points. */
class LiveTextFrame(val lines: List<LiveLine>, val width: Int, val height: Int)

object LiveScannerRegistry {
    var provider: LiveScannerProvider? = null
}

@Composable
actual fun LiveTextScanner(modifier: Modifier, onText: (RecognizedText) -> Unit, onStatus: (LiveScannerStatus) -> Unit) {
    val provider = LiveScannerRegistry.provider
    val currentOnText by rememberUpdatedState(onText)
    val currentOnStatus by rememberUpdatedState(onStatus)

    if (provider == null || !provider.isSupported()) {
        LaunchedEffect(Unit) { currentOnStatus(LiveScannerStatus.Unsupported) }
        return
    }
    UIKitViewController(
        factory = {
            provider.makeViewController { frame ->
                currentOnText(
                    RecognizedText(
                        lines = frame.lines.map { TextLine(it.text, it.left, it.top, it.right, it.bottom, it.confidence) },
                        imageWidth = frame.width,
                        imageHeight = frame.height,
                    ),
                )
            }
        },
        modifier = modifier,
    )
    LaunchedEffect(Unit) { currentOnStatus(LiveScannerStatus.Scanning) }
}
