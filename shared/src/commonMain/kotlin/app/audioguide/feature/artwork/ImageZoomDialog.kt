package app.audioguide.feature.artwork

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.audioguide.catalog.ArtworkImage
import app.audioguide.resources.Res
import app.audioguide.resources.artwork_image_credit
import app.audioguide.resources.artwork_image_credit_anonymous
import app.audioguide.resources.artwork_image_description
import app.audioguide.resources.image_zoom_close
import app.audioguide.resources.image_zoom_offline
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.stringResource

/** Largeur de l'image en grand : une des tailles de miniature standard de Wikimedia. */
private const val ZOOM_WIDTH = 1920
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Photo de l'œuvre en plein écran, agrandissable (pincer, double appui). Elle est téléchargée à la demande depuis
 * Commons : sans réseau (et hors du cache d'une semaine), un message le dit, et la fiche reste utilisable.
 */
@Composable
fun ImageZoomDialog(image: ArtworkImage, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // La miniature de la fiche (déjà en mémoire) s'affiche tout de suite, en attendant la grande image.
            val preview = rememberAsyncImagePainter(image.thumbnailUrl())
            val full = rememberAsyncImagePainter(image.thumbnailUrl(ZOOM_WIDTH))
            val fullState by full.state.collectAsState()
            val previewState by preview.state.collectAsState()
            val painter = if (fullState is AsyncImagePainter.State.Success) full else preview

            ZoomableImage(painter = painter, contentDescription = stringResource(Res.string.artwork_image_description))

            when {
                fullState is AsyncImagePainter.State.Loading || fullState is AsyncImagePainter.State.Empty ->
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                fullState is AsyncImagePainter.State.Error && previewState !is AsyncImagePainter.State.Success ->
                    Text(
                        stringResource(Res.string.image_zoom_offline),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                fullState is AsyncImagePainter.State.Error ->
                    Text(
                        stringResource(Res.string.image_zoom_offline),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp, start = 24.dp, end = 24.dp),
                    )
                else -> Unit
            }

            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(4.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.image_zoom_close), tint = Color.White)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f)).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    image.author?.let { stringResource(Res.string.artwork_image_credit, it, image.license) }
                        ?: stringResource(Res.string.artwork_image_credit_anonymous, image.license),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Image qu'on agrandit en pinçant, qu'on déplace une fois agrandie, et qu'on agrandit ou rétablit d'un double appui. */
@Composable
private fun ZoomableImage(painter: androidx.compose.ui.graphics.painter.Painter, contentDescription: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Au-delà, l'image sortirait entièrement de l'écran.
    fun clamp(value: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                            // Agrandit autour du point touché.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, MAX_SCALE)
                    scale = next
                    offset = if (next == 1f) Offset.Zero else clamp(offset + pan, next)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
