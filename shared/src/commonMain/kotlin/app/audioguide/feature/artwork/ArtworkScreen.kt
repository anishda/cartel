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
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkImage
import app.audioguide.catalog.ArtworkStory
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.foundation.Image
import app.audioguide.catalog.PackInfo
import app.audioguide.feature.search.shortMuseumName
import app.audioguide.platform.systemLanguageTag
import app.audioguide.resources.Res
import app.audioguide.resources.artwork_info
import app.audioguide.resources.artwork_listen
import app.audioguide.resources.artwork_listen_full
import app.audioguide.catalog.StoryCleaner
import app.audioguide.resources.artwork_not_found
import app.audioguide.resources.artwork_provenance
import app.audioguide.resources.artwork_source
import app.audioguide.resources.artwork_image_credit
import app.audioguide.resources.artwork_image_credit_anonymous
import app.audioguide.resources.artwork_image_description
import app.audioguide.resources.artwork_image_zoom
import app.audioguide.resources.artwork_story
import app.audioguide.resources.artwork_story_open
import app.audioguide.resources.artwork_story_source
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListenButton(
                        isSpeaking = state.isSpeaking,
                        enabled = state.voiceReady,
                        onPlay = { viewModel.speak(full = false) },
                        onStop = viewModel::stop,
                        playLabel = stringResource(Res.string.artwork_listen),
                        modifier = Modifier.weight(1f),
                    )
                    if (!state.isSpeaking) {
                        TextButton(onClick = { viewModel.speak(full = true) }, enabled = state.voiceReady) {
                            Text(stringResource(Res.string.artwork_listen_full))
                        }
                    }
                }
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
        artwork.image?.let { ArtworkPhoto(it) }

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

        artwork.story?.let { StorySection(it, pack?.storyLicense) }

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

/**
 * Miniature de l'œuvre, chargée en ligne depuis Wikimedia Commons puis gardée par Coil. Rien ne s'affiche tant que
 * l'image n'est pas là (hors ligne : la fiche reste complète), et l'auteur et la licence l'accompagnent toujours.
 * Un appui l'ouvre en grand ([ImageZoomDialog]).
 */
@Composable
private fun ArtworkPhoto(image: ArtworkImage) {
    val uriHandler = LocalUriHandler.current
    var zoomed by remember { mutableStateOf(false) }
    if (zoomed) ImageZoomDialog(image, onDismiss = { zoomed = false })
    SubcomposeAsyncImage(
        model = image.thumbnailUrl(),
        contentDescription = stringResource(Res.string.artwork_image_description),
        modifier = Modifier.fillMaxWidth(),
        loading = {},
        error = {},
        success = { state ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(
                    painter = state.painter,
                    contentDescription = stringResource(Res.string.artwork_image_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(Res.string.artwork_image_zoom)) { zoomed = true },
                )
                TextButton(onClick = { runCatching { uriHandler.openUri(image.pageUrl) } }) {
                    Text(
                        image.author?.let { stringResource(Res.string.artwork_image_credit, it, image.license) }
                            ?: stringResource(Res.string.artwork_image_credit_anonymous, image.license),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Récit Wikipedia, toujours accompagné de son attribution (CC BY-SA) et d'un lien vers l'article. */
@Composable
private fun StorySection(story: ArtworkStory, license: String?) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(Res.string.artwork_story))
        StoryCleaner.forReading(story.text).split('\n').forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(Res.string.artwork_story_source, story.articleTitle, license ?: "CC BY-SA 4.0"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (story.articleUrl.isNotBlank()) {
            TextButton(onClick = { runCatching { uriHandler.openUri(story.articleUrl) } }) {
                Text(stringResource(Res.string.artwork_story_open))
            }
        }
    }
}

private fun formatCm(value: Double): String {
    if (value % 1.0 == 0.0) return value.toLong().toString()
    val text = value.toString()
    return if (systemLanguageTag().startsWith("fr")) text.replace('.', ',') else text
}
