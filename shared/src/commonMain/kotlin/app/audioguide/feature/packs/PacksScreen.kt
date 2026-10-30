package app.audioguide.feature.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.platform.systemLanguageTag
import app.audioguide.resources.Res
import app.audioguide.resources.cancel
import app.audioguide.resources.packs_details
import app.audioguide.resources.packs_download
import app.audioguide.resources.packs_downloading
import app.audioguide.resources.packs_error
import app.audioguide.resources.packs_installed
import app.audioguide.resources.packs_installed_details
import app.audioguide.resources.packs_intro
import app.audioguide.resources.packs_loading
import app.audioguide.resources.packs_not_listed
import app.audioguide.resources.packs_offline_banner
import app.audioguide.resources.packs_refresh
import app.audioguide.resources.packs_remove
import app.audioguide.resources.packs_remove_body
import app.audioguide.resources.packs_remove_title
import app.audioguide.resources.packs_retry
import app.audioguide.resources.packs_source
import app.audioguide.resources.packs_title
import app.audioguide.resources.packs_update
import app.audioguide.ui.AppIcons
import app.audioguide.ui.components.ScreenHeader
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PacksScreen(viewModel: PacksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRemoval by remember { mutableStateOf<PackRow?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                ScreenHeader(
                    stringResource(Res.string.packs_title),
                    subtitle = stringResource(Res.string.packs_intro),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::refresh) {
                    Icon(AppIcons.Refresh, contentDescription = stringResource(Res.string.packs_refresh))
                }
            }
        }
        when (state.remote) {
            RemoteState.Loading -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.packs_loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is RemoteState.Failed -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.packs_offline_banner), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (state.remote as RemoteState.Failed).reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            is RemoteState.Loaded -> Unit
        }
        items(state.rows, key = { it.id }) { row ->
            PackCard(
                row = row,
                onDownload = { row.entry?.let(viewModel::download) },
                onRemove = { pendingRemoval = row },
            )
        }
        item {
            Text(
                stringResource(Res.string.packs_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingRemoval?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(Res.string.packs_remove_title)) },
            text = { Text("${row.name}\n\n${stringResource(Res.string.packs_remove_body)}") },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(row.id); pendingRemoval = null }) {
                    Text(stringResource(Res.string.packs_remove))
                }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun PackCard(row: PackRow, onDownload: () -> Unit, onRemove: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.name.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    row.entry != null -> stringResource(
                        Res.string.packs_details, formatCount(row.entry.recordCount), formatFileSize(row.entry.sizeBytes), row.entry.version,
                    )
                    row.installed != null -> stringResource(Res.string.packs_installed_details, formatCount(row.installed.recordCount), row.installed.version)
                    else -> ""
                }
                Text(
                    listOfNotNull(row.city, subtitle.ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (val operation = row.operation) {
                is PackOperation.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { operation.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(Res.string.packs_downloading, "${(operation.progress * 100).toInt()} %"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (operation is PackOperation.Failed) {
                        Text(
                            stringResource(Res.string.packs_error, operation.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onDownload) { Text(stringResource(Res.string.packs_retry)) }
                    } else if (row.installed == null) {
                        Button(onClick = onDownload) {
                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.packs_download))
                        }
                    } else {
                        if (row.updateAvailable) {
                            Button(onClick = onDownload) { Text(stringResource(Res.string.packs_update)) }
                        } else {
                            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(if (row.entry == null) Res.string.packs_not_listed else Res.string.packs_installed),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.packs_remove))
                        }
                    }
                }
            }
        }
    }
}

/** « 4,2 Mo » en français, « 4.2 MB » ailleurs. */
internal fun formatFileSize(bytes: Long): String {
    val french = systemLanguageTag().startsWith("fr")
    val separator = if (french) "," else "."
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1) {
        val tenths = (megabytes * 10).toInt()
        "${tenths / 10}$separator${tenths % 10} ${if (french) "Mo" else "MB"}"
    } else {
        "${(bytes / 1024).coerceAtLeast(1)} ${if (french) "Ko" else "KB"}"
    }
}

/** « 7 443 » en français, « 7,443 » ailleurs. */
internal fun formatCount(count: Int): String {
    val separator = if (systemLanguageTag().startsWith("fr")) "\u202F" else ","
    return count.toString().reversed().chunked(3).joinToString(separator).reversed()
}
