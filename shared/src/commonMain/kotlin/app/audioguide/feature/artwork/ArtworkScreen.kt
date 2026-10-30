package app.audioguide.feature.artwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.PackInfo
import app.audioguide.feature.search.shortMuseumName
import app.audioguide.platform.systemLanguageTag
import app.audioguide.resources.Res
import app.audioguide.resources.artwork_info
import app.audioguide.resources.artwork_listen
import app.audioguide.resources.artwork_not_found
import app.audioguide.resources.artwork_provenance
import app.audioguide.resources.artwork_source
import app.audioguide.resources.artwork_technical
import app.audioguide.resources.back
import app.audioguide.resources.favorite_add
import app.audioguide.resources.favorite_remove
import app.audioguide.resources.info_dimensions
import app.audioguide.resources.info_dimensions_value
import app.audioguide.resources.info_inventory
import app.audioguide.resources.info_location
import app.audioguide.resources.info_museum
import app.audioguide.resources.info_school
import app.audioguide.resources.info_technique
import app.audioguide.ui.AppIcons
import app.audioguide.ui.components.EmptyState
import app.audioguide.ui.components.InfoRow
import app.audioguide.ui.components.ListenButton
import app.audioguide.ui.components.Pill
import app.audioguide.ui.components.SectionLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArtworkScreen(
    museumId: String,
    reference: String,
    onBack: () -> Unit,
    viewModel: ArtworkViewModel = koinViewModel(parameters = { parametersOf(museumId, reference) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
            }
            Box(modifier = Modifier.weight(1f))
            if (state.artwork != null) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (state.isFavorite) Res.string.favorite_remove else Res.string.favorite_add),
                        tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        val artwork = state.artwork
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                artwork == null -> EmptyState(
                    icon = AppIcons.Museum,
                    title = stringResource(Res.string.artwork_not_found),
                    body = "",
                )
                else -> ArtworkContent(artwork, state.pack)
            }
        }

        if (artwork != null) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                ListenButton(
                    isSpeaking = state.isSpeaking,
                    enabled = state.voiceReady,
                    onPlay = viewModel::speak,
                    onStop = viewModel::stop,
                    playLabel = stringResource(Res.string.artwork_listen),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtworkContent(artwork: Artwork, pack: PackInfo?) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pack?.let { SectionLabel(shortMuseumName(it.museumName)) }
            Text(artwork.title, style = MaterialTheme.typography.headlineMedium)
            artwork.artist?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            artwork.dateText?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(artwork.domain, artwork.denomination).distinct().forEach { Pill(it) }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(Res.string.artwork_info))
                pack?.let { InfoRow(stringResource(Res.string.info_museum), it.museumName) }
                artwork.technique?.let { InfoRow(stringResource(Res.string.info_technique), it) }
                if (artwork.heightCm != null && artwork.widthCm != null) {
                    InfoRow(
                        stringResource(Res.string.info_dimensions),
                        stringResource(Res.string.info_dimensions_value, formatCm(artwork.heightCm!!), formatCm(artwork.widthCm!!)),
                    )
                }
                artwork.school?.let { InfoRow(stringResource(Res.string.info_school), it) }
                artwork.location?.let { InfoRow(stringResource(Res.string.info_location), it) }
                InfoRow(stringResource(Res.string.info_inventory), artwork.inventoryNo)
            }
        }

        artwork.provenance?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.artwork_provenance))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
        artwork.description?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(Res.string.artwork_technical))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        pack?.let {
            Text(
                stringResource(
                    Res.string.artwork_source,
                    it.sourceName,
                    it.license,
                    (it.sourceUpdatedAt ?: it.builtAt).take(10),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCm(value: Double): String {
    if (value % 1.0 == 0.0) return value.toLong().toString()
    val text = value.toString()
    return if (systemLanguageTag().startsWith("fr")) text.replace('.', ',') else text
}
