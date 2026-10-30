package app.audioguide.platform

import app.audioguide.domain.GutterSplitter
import app.audioguide.domain.RecognizedText
import app.audioguide.domain.Region
import app.audioguide.domain.TextLine
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.WordBox
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSError
import platform.Foundation.NSMakeRange
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** Reconnaissance de texte Apple Vision, entièrement sur l'appareil. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class VisionTextRecognizer : TextRecognizer {
    override suspend fun recognize(image: ByteArray, region: Region?, scale: Float): Result<RecognizedText> =
        withContext(Dispatchers.Default) {
            runCatching {
                val source = checkNotNull(UIImage.imageWithData(image.toNSData())) { "Image illisible" }
                val (width, height) = source.size.useContents { width.roundToInt() to height.roundToInt() }
                if (region == null && scale <= 1.01f) {
                    // Image entière, telle quelle : Vision lit l'orientation EXIF dans les données.
                    read(VNImageRequestHandler(data = image.toNSData(), options = emptyMap<Any?, Any?>()), width, height)
                } else {
                    val (cropped, w, h) = render(source, width, height, region, scale)
                    read(VNImageRequestHandler(cropped.CGImage!!, emptyMap<Any?, Any?>()), w, h)
                }
            }
        }

    /** Le morceau [region] de l'image, agrandi de [scale] (au plus 4 096 px de côté), dessiné à l'endroit. */
    private fun render(source: UIImage, width: Int, height: Int, region: Region?, scale: Float): Triple<UIImage, Int, Int> {
        val x = ((region?.left ?: 0f) * width).coerceIn(0f, width - 1f)
        val y = ((region?.top ?: 0f) * height).coerceIn(0f, height - 1f)
        val cropWidth = ((region?.width ?: 1f) * width).coerceIn(1f, width - x)
        val cropHeight = ((region?.height ?: 1f) * height).coerceIn(1f, height - y)
        val factor = minOf(scale, MAX_SIDE / max(cropWidth, cropHeight)).coerceAtLeast(0.1f)
        val outWidth = max(1, (cropWidth * factor).roundToInt())
        val outHeight = max(1, (cropHeight * factor).roundToInt())

        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            this.scale = 1.0 // sinon l'image est rendue à l'échelle de l'écran (×3)
            opaque = true
        }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(outWidth.toDouble(), outHeight.toDouble()), format = format)
        val rendered = renderer.imageWithActions {
            source.drawInRect(CGRectMake((-x * factor).toDouble(), (-y * factor).toDouble(), (width * factor).toDouble(), (height * factor).toDouble()))
        }
        return Triple(rendered, outWidth, outHeight)
    }

    private fun read(handler: VNImageRequestHandler, width: Int, height: Int): RecognizedText {
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            recognitionLanguages = listOf("fr-FR", "en-US", "es-ES", "it-IT", "de-DE", "nl-NL", "pt-PT")
            usesLanguageCorrection = true
        }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val performed = handler.performRequests(listOf(request), error.ptr)
            check(performed) { error.value?.localizedDescription ?: "Vision a échoué" }
        }
        val lines = request.results.orEmpty()
            .filterIsInstance<VNRecognizedTextObservation>()
            .flatMap(::toLines)
        return RecognizedText(lines, width, height)
    }

    private companion object {
        const val MAX_SIDE = 4096f
    }
}

/**
 * Les lignes d'une observation, avec leur position. Vision lit parfois deux colonnes séparées par une gouttière
 * comme une seule ligne : on la recoupe aux grands espaces, d'après la position de chaque mot, pour que chaque
 * colonne reste dans sa langue. L'origine de Vision est en bas à gauche ; la nôtre, en haut.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun toLines(observation: VNRecognizedTextObservation): List<TextLine> {
    val candidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText ?: return emptyList()
    val whole = observation.boundingBox.useContents { Box(origin.x.toFloat(), origin.y.toFloat(), (origin.x + size.width).toFloat(), (origin.y + size.height).toFloat()) }
    val confidence = candidate.confidence
    val text = candidate.string
    fun line(text: String, box: Box) = TextLine(text, box.left, 1f - box.top, box.right, 1f - box.bottom, confidence)

    val words = Regex("""\S+""").findAll(text).toList()
    if (words.size < 2) return listOf(line(text, whole))
    val boxes = words.map { word ->
        // Les index d'une String Kotlin (UTF-16) sont ceux d'un NSString.
        val range = NSMakeRange(word.range.first.toULong(), word.value.length.toULong())
        val box = candidate.boundingBoxForRange(range, null)?.boundingBox ?: return listOf(line(text, whole))
        box.useContents { Box(origin.x.toFloat(), origin.y.toFloat(), (origin.x + size.width).toFloat(), (origin.y + size.height).toFloat()) }
    }
    val groups = GutterSplitter.group(words.mapIndexed { i, word -> WordBox(word.value, boxes[i].left, boxes[i].right) })
    if (groups.size == 1) return listOf(line(text, whole))
    var next = 0
    return groups.map { group ->
        val part = boxes.subList(next, next + group.size)
        next += group.size
        line(
            group.joinToString(" ") { it.text },
            Box(part.minOf { it.left }, part.minOf { it.bottom }, part.maxOf { it.right }, part.maxOf { it.top }),
        )
    }
}

/** Une boîte au repère de Vision (origine en bas à gauche) : [bottom] est le bord bas, [top] le bord haut. */
private class Box(val left: Float, val bottom: Float, val right: Float, val top: Float)
