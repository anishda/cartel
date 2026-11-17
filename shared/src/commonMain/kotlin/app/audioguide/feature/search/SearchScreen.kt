package app.audioguide.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.catalog.Artwork
import app.audioguide.resources.Res
import app.audioguide.ui.components.AppTextField
import app.audioguide.resources.nav_museums
import app.audioguide.resources.search_clear
import app.audioguide.resources.search_filter_all
import app.audioguide.resources.search_hint_body
import app.audioguide.resources.search_hint_title
import app.audioguide.resources.search_no_packs_action
import app.audioguide.resources.search_no_packs_body
import app.audioguide.resources.search_no_packs_title
import app.audioguide.resources.search_no_results
import app.audioguide.resources.search_placeholder
import app.audioguide.ui.AppIcons
import app.audioguide.ui.components.EmptyState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    onOpenArtwork: (museumId: String, reference: String) -> Unit,
    onBrowseMuseums: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = stringResource(Res.string.search_placeholder),
                leading = { Icon(Icons.Default.Search, contentDescription = null) },
                trailing = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.search_clear))
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.packs.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = state.museumId == null,
                        onClick = { viewModel.onMuseumSelected(null) },
                        label = { Text(stringResource(Res.string.search_filter_all)) },
                    )
                    state.packs.forEach { pack ->
                        FilterChip(
                            selected = state.museumId == pack.museumId,
                            onClick = { viewModel.onMuseumSelected(pack.museumId) },
                            label = { Text(shortMuseumName(pack.museumName)) },
                        )
                    }
                }
            }
        }

        when {
            state.packs.isEmpty() -> EmptyState(
                icon = AppIcons.Museum,
                title = stringResource(Res.string.search_no_packs_title),
                body = stringResource(Res.string.search_no_packs_body),
                actionLabel = stringResource(Res.string.search_no_packs_action),
                onAction = onBrowseMuseums,
            )
            state.query.isBlank() -> EmptyState(
                icon = Icons.Default.Search,
                title = stringResource(Res.string.search_hint_title),
                body = stringResource(Res.string.search_hint_body),
            )
            state.searching -> Row(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.results.isEmpty() -> EmptyState(
                icon = Icons.Default.Search,
                title = stringResource(Res.string.search_no_results, state.query.trim()),
                body = stringResource(Res.string.search_hint_body),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.results, key = { "${it.museumId}/${it.reference}" }) { artwork ->
                    ArtworkRow(
                        artwork = artwork,
                        museumName = state.museumNames[artwork.museumId].orEmpty(),
                        onClick = { onOpenArtwork(artwork.museumId, artwork.reference) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ArtworkRow(artwork: Artwork, museumName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artwork.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val subtitle = listOfNotNull(artwork.artist, artwork.dateText).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val caption = listOfNotNull(shortMuseumName(museumName).ifEmpty { null }, artwork.domain, artwork.inventoryNo).joinToString(" · ")
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** « musée du Louvre » → « Louvre », « musée de l'Orangerie des Tuileries » → « Orangerie des Tuileries ». */
internal fun shortMuseumName(name: String): String =
    name.removePrefix("musée ").removePrefix("du ").removePrefix("de l'").removePrefix("d'").removePrefix("de ")
        .replaceFirstChar { it.uppercaseChar() }
