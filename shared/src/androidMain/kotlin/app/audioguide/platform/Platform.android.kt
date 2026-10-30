package app.audioguide.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.audioguide.domain.RecognizedText
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.Executors

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()

/** Sélecteur de photos du système (`PickVisualMedia`) : aucune permission à demander. */
@Composable
actual fun rememberPhotoImporter(onImage: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnImage by rememberUpdatedState(onImage)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use { currentOnImage(it.readBytes()) }
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/** Feuille de partage du système, pour exporter les favoris en Markdown. */
@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { title, text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, title))
        }
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun localTimeOffsetMillis(): Long = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

/** Une analyse toutes les ~0,6 s : assez réactif sans chauffer le téléphone. */
private const val ANALYSIS_INTERVAL_MS = 600L

/** CameraX (aperçu + analyse d'image) et ML Kit, en continu. Demande la permission caméra au premier affichage. */
@OptIn(ExperimentalGetImage::class)
@Composable
actual fun LiveTextScanner(modifier: Modifier, onText: (RecognizedText) -> Unit, onStatus: (LiveScannerStatus) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnText by rememberUpdatedState(onText)
    val currentOnStatus by rememberUpdatedState(onStatus)

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) currentOnStatus(LiveScannerStatus.PermissionDenied)
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) return

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = providerFuture.get()
            provider = cameraProvider

            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            // CameraX analyse en 640 × 480 par défaut : trop peu pour un cartel. On demande au moins du 1080p.
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            var lastAnalysis = 0L
            analysis.setAnalyzer(analysisExecutor) { frame ->
                val media = frame.image
                val now = SystemClock.elapsedRealtime()
                if (media == null || disposed || now - lastAnalysis < ANALYSIS_INTERVAL_MS) {
                    frame.close()
                    return@setAnalyzer
                }
                lastAnalysis = now
                // ML Kit lit l'image après rotation : en portrait, la largeur est la hauteur du capteur.
                val rotation = frame.imageInfo.rotationDegrees
                val quarterTurn = rotation == 90 || rotation == 270
                val uprightWidth = if (quarterTurn) frame.height else frame.width
                val uprightHeight = if (quarterTurn) frame.width else frame.height
                recognizer.process(InputImage.fromMediaImage(media, rotation))
                    .addOnSuccessListener { currentOnText(it.toRecognizedText(uprightWidth, uprightHeight)) }
                    .addOnCompleteListener { frame.close() }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                currentOnStatus(LiveScannerStatus.Scanning)
            } catch (e: Exception) {
                currentOnStatus(LiveScannerStatus.Unsupported)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analysisExecutor.shutdown()
            recognizer.close()
        }
    }
}
