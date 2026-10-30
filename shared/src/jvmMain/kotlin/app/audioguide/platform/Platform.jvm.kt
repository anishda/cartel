package app.audioguide.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.audioguide.domain.RecognizedText

actual fun systemLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()

@Composable
actual fun rememberPhotoImporter(onImage: (ByteArray) -> Unit): () -> Unit = {}

@Composable
actual fun LiveTextScanner(modifier: Modifier, onText: (RecognizedText) -> Unit, onStatus: (LiveScannerStatus) -> Unit) {
    onStatus(LiveScannerStatus.Unsupported)
}

@Composable
actual fun rememberTextSharer(): (title: String, text: String) -> Unit = { _, _ -> }

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun localTimeOffsetMillis(): Long = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
