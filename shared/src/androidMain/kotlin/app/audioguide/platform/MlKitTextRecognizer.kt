package app.audioguide.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import app.audioguide.domain.GutterSplitter
import app.audioguide.domain.RecognizedText
import app.audioguide.domain.Region
import app.audioguide.domain.TextLine
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.WordBox
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** ML Kit Text Recognition (modèle latin embarqué dans l'APK : fonctionne sans réseau). */
class MlKitTextRecognizer : TextRecognizer {
    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(image: ByteArray, region: Region?, scale: Float): Result<RecognizedText> = runCatching {
        val bitmap = withContext(Dispatchers.Default) { prepare(image, region, scale) }
        val text = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        text.toRecognizedText(bitmap.width, bitmap.height)
    }

    /** Image à l'endroit, recadrée sur [region] puis agrandie de [scale] ; une photo énorme est ramenée à 4 096 px. */
    private fun prepare(bytes: ByteArray, region: Region?, scale: Float): Bitmap {
        var bitmap = upright(bytes)
        if (region != null) {
            val x = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val w = (region.width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
            val h = (region.height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
            bitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
        }
        val longest = maxOf(bitmap.width, bitmap.height)
        val factor = when {
            scale > 1.01f -> scale
            longest > MAX_FIRST_PASS_SIDE -> MAX_FIRST_PASS_SIDE.toFloat() / longest
            else -> 1f
        }
        if (factor != 1f) {
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
        }
        return bitmap
    }

    private fun upright(bytes: ByteArray): Bitmap {
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Image illisible" }
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_FIRST_PASS_SIDE = 4096
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/**
 * Les lignes lues par ML Kit, avec leur position. ML Kit réunit parfois deux colonnes séparées par une gouttière en
 * une seule ligne : on la recoupe là où un grand espace sépare les mots (voir [GutterSplitter]). [width] et [height] :
 * taille en pixels de l'image telle que ML Kit l'a lue (après rotation).
 */
internal fun Text.toRecognizedText(width: Int, height: Int): RecognizedText {
    val w = width.coerceAtLeast(1).toFloat()
    val h = height.coerceAtLeast(1).toFloat()
    val lines = textBlocks.flatMap { it.lines }.flatMap { line ->
        val box = line.boundingBox ?: return@flatMap emptyList()
        val confidence = line.confidence
        val elements = line.elements.filter { it.boundingBox != null }
        if (elements.size < 2 || elements.size != line.elements.size) {
            return@flatMap listOf(TextLine(line.text, box.left / w, box.top / h, box.right / w, box.bottom / h, confidence))
        }
        val words = elements.map { WordBox(it.text, it.boundingBox!!.left / w, it.boundingBox!!.right / w) }
        // Un groupe de mots = un morceau de ligne ; sa boîte réunit celles de ses mots.
        var next = 0
        GutterSplitter.group(words).map { group ->
            val part = elements.subList(next, next + group.size)
            next += group.size
            TextLine(
                text = group.joinToString(" ") { it.text },
                left = part.minOf { it.boundingBox!!.left } / w,
                top = part.minOf { it.boundingBox!!.top } / h,
                right = part.maxOf { it.boundingBox!!.right } / w,
                bottom = part.maxOf { it.boundingBox!!.bottom } / h,
                confidence = confidence,
            )
        }
    }
    return RecognizedText(lines, width, height)
}
