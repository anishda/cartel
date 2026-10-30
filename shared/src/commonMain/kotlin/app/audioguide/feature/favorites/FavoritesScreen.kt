package app.audioguide.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.favorites.Favorite
import app.audioguide.favorites.FavoritesExport
import app.audioguide.favorites.isoDate
import app.audioguide.platform.currentTimeMillis
import app.audioguide.platform.localTimeOffsetMillis
import app.audioguide.platform.rememberTextSharer
import app.audioguide.resources.Res
import app.audioguide.resources.favorites_cartels
import app.audioguide.resources.favorites_empty_body
import app.audioguide.resources.favorites_empty_title
import app.audioguide.resources.favorites_export_date
import app.audioguide.resources.favorites_export_title
import app.audioguide.resources.favorites_share
import app.audioguide.resources.favorites_subtitle
import app.audioguide.resources.favorites_title
import app.audioguide.ui.AppIcons
import app.audioguide.ui.components.EmptyState
import app.audioguide.ui.components.ScreenHeader
import app.audioguide.ui.components.SectionLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FavoritesScreen(
    onOpenFavorite: (favoriteId: Long) -> Unit,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val all by viewModel.favorites.collectAsStateWithLifecycle()
    val share = rememberTextSharer()
    val exportTitle = stringResource(Res.string.favorites_export_title)
    val exportedLabel = stringResource(Res.string.favorites_export_date)
    val cartelsLabel = stringResource(Res.string.favorites_cartels)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ScreenHeader(
                    stringResource(Res.string.favorites_title),
                    subtitle = stringResource(Res.string.favorites_subtitle),
                    modifier = Modifier.weight(1f),
                )
                if (all.isNotEmpty()) {
                    IconButton(onClick = {
                        share(
                            exportTitle,
                            FavoritesExport.markdown(all, isoDate(currentTimeMillis() + localTimeOffsetMillis()), exportTitle, exportedLabel, cartelsLabel),
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.favorites_share))
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Favorite,
                    title = stringResource(Res.string.favorites_empty_title),
                    body = stringResource(Res.string.favorites_empty_body),
                )
            }
        }

        groups.forEach { group ->
            item(key = "header-${group.museumName}") {
                SectionLabel(
                    group.museumName?.replaceFirstChar { it.uppercaseChar() } ?: cartelsLabel,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(group.items, key = { it.id }) { favorite ->
                FavoriteRow(favorite, onClick = { onOpenFavorite(favorite.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun FavoriteRow(favorite: Favorite, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(favorite.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            favorite.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            favorite.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
