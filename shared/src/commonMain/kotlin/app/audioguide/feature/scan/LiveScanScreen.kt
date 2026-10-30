package app.audioguide.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.audioguide.domain.LabelExtractor
import app.audioguide.domain.RecognizedText
import app.audioguide.platform.LiveScannerStatus
import app.audioguide.platform.LiveTextScanner
import app.audioguide.resources.Res
import app.audioguide.resources.close
import app.audioguide.resources.live_hint
import app.audioguide.resources.live_permission
import app.audioguide.resources.live_read
import app.audioguide.resources.live_title
import app.audioguide.resources.live_unsupported
import app.audioguide.resources.live_waiting
import app.audioguide.resources.scan_import
import app.audioguide.platform.rememberPhotoImporter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Texte minimal pour qu'un « Lire » ait un sens (évite de lire du bruit). */
private const val MIN_READABLE_CHARACTERS = 12

@Composable
fun LiveScanScreen(onClose: () -> Unit, session: ScanSession = koinInject()) {
    var recognized by remember { mutableStateOf<RecognizedText?>(null) }
    // Ce que l'app lirait : le cartel isolé du reste de l'image (logos, filigranes, autres panneaux).
    val text = remember(recognized) { recognized?.let { LabelExtractor.extract(it).text }.orEmpty() }
    var status by remember { mutableStateOf(LiveScannerStatus.Starting) }
    val importPhoto = rememberPhotoImporter { image ->
        session.onImage(image)
        onClose()
    }
    val unavailable = status == LiveScannerStatus.PermissionDenied || status == LiveScannerStatus.Unsupported

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!unavailable) {
            LiveTextScanner(
                modifier = Modifier.fillMaxSize(),
                onText = { recognized = it },
                onStatus = { status = it },
            )
        }

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(
                        stringResource(Res.string.live_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            BottomPanel(
                unavailable = unavailable,
                status = status,
                text = text,
                onRead = {
                    recognized?.let(session::onRecognized)
                    onClose()
                },
                onImport = importPhoto,
            )
        }
    }
}

@Composable
private fun BottomPanel(
    unavailable: Boolean,
    status: LiveScannerStatus,
    text: String,
    onRead: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (unavailable) {
                Text(
                    stringResource(if (status == LiveScannerStatus.PermissionDenied) Res.string.live_permission else Res.string.live_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.scan_import))
                }
            } else {
                val readable = text.trim().length >= MIN_READABLE_CHARACTERS
                Text(
                    text = if (readable) text.trim() else stringResource(Res.string.live_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (readable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!readable) {
                    Text(
                        stringResource(Res.string.live_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRead, enabled = readable, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.live_read))
                    }
                    OutlinedButton(onClick = onImport) { Text(stringResource(Res.string.scan_import)) }
                }
            }
        }
    }
}
