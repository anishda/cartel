package app.audioguide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.audioguide.catalog.ArtworkImage
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.audioguide.resources.Res
import app.audioguide.resources.listen
import app.audioguide.resources.stop
import app.audioguide.ui.AppIcons
import org.jetbrains.compose.resources.stringResource

/** Bouton écouter / stop. Le libellé de lecture peut être précisé (« Écouter la fiche »). */
@Composable
fun ListenButton(
    isSpeaking: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    playLabel: String = stringResource(Res.string.listen),
) {
    if (isSpeaking) {
        OutlinedButton(onClick = onStop, modifier = modifier) {
            Icon(AppIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.stop))
        }
    } else {
        Button(onClick = onPlay, enabled = enabled, modifier = modifier) {
            Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(playLabel)
        }
    }
}

private val THUMBNAIL_SIZE = 64.dp

/** Taille de miniature standard de Wikimedia, assez fine pour une vignette de 64 dp en haute densité. */
private const val THUMBNAIL_REQUEST_WIDTH = 250

/**
 * Vignette d'une œuvre (Wikimedia Commons, en ligne puis en cache). Sans image libre, hors ligne ou pendant le
 * chargement, un cadre neutre avec [placeholder] garde l'alignement de la liste.
 */
@Composable
fun ArtworkThumbnail(image: ArtworkImage?, modifier: Modifier = Modifier, placeholder: ImageVector = AppIcons.Museum) {
    Box(
        modifier = modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
        image?.let {
            AsyncImage(
                model = it.thumbnailUrl(THUMBNAIL_REQUEST_WIDTH),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Page vide : icône, titre, explication et, si utile, une action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(20.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Petit titre de section en capitales. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/** Une ligne « libellé : valeur » d'une fiche. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Étiquette arrondie, non interactive. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** En-tête de page : titre en empattement et sous-titre. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
