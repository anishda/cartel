package app.audioguide.feature.favorites

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.favorites.Favorite
import app.audioguide.resources.Res
import app.audioguide.ui.components.AppTextField
import app.audioguide.resources.artwork_listen
import app.audioguide.resources.back
import app.audioguide.resources.cancel
import app.audioguide.resources.favorite_delete
import app.audioguide.resources.favorite_delete_body
import app.audioguide.resources.favorite_delete_title
import app.audioguide.resources.favorite_not_found
import app.audioguide.resources.favorite_note
import app.audioguide.resources.favorite_open
import app.audioguide.resources.favorites_cartels
import app.audioguide.resources.listen
import app.audioguide.ui.components.ListenButton
import app.audioguide.ui.components.SectionLabel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FavoriteScreen(
    favoriteId: Long,
    onBack: () -> Unit,
    onOpenArtwork: (museumId: String, reference: String) -> Unit,
    viewModel: FavoriteViewModel = koinViewModel(parameters = { parametersOf(favoriteId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorite = state.favorite
    var confirmDelete by remember { mutableStateOf(false) }
    var existed by remember { mutableStateOf(false) }

    // Quitte l'écran quand le favori est supprimé.
    LaunchedEffect(favorite) {
        if (favorite != null) existed = true else if (existed) onBack()
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
            }
            Box(modifier = Modifier.weight(1f))
            if (favorite != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.favorite_delete))
                }
            }
        }

        if (favorite == null) {
            Text(
                stringResource(Res.string.favorite_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(20.dp),
            )
        } else {
            FavoriteContent(
                favorite = favorite,
                canOpenArtwork = state.canOpenArtwork,
                onOpenArtwork = { favorite.museumId?.let { m -> favorite.reference?.let { r -> onOpenArtwork(m, r) } } },
                onNote = viewModel::saveNote,
                modifier = Modifier.weight(1f),
            )
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                ListenButton(
                    isSpeaking = state.isSpeaking,
                    enabled = state.voiceReady,
                    onPlay = viewModel::speak,
                    onStop = viewModel::stop,
                    playLabel = stringResource(if (favorite.reference != null) Res.string.artwork_listen else Res.string.listen),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.favorite_delete_title)) },
            text = { Text(stringResource(Res.string.favorite_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(Res.string.favorite_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun FavoriteContent(
    favorite: Favorite,
    canOpenArtwork: Boolean,
    onOpenArtwork: () -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // La note s'enregistre 0,5 s après la dernière frappe, et à la fermeture de l'écran.
    var note by remember(favorite.id) { mutableStateOf(favorite.note.orEmpty()) }
    var saved by remember(favorite.id) { mutableStateOf(favorite.note.orEmpty()) }
    val latestNote by rememberUpdatedState(note)
    val latestSaved by rememberUpdatedState(saved)
    LaunchedEffect(note) {
        if (note != saved) {
            delay(500)
            onNote(note)
            saved = note
        }
    }
    DisposableEffect(favorite.id) {
        onDispose { if (latestNote != latestSaved) onNote(latestNote) }
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(favorite.museumName?.replaceFirstChar { it.uppercaseChar() } ?: stringResource(Res.string.favorites_cartels))
            Text(favorite.title, style = MaterialTheme.typography.headlineMedium)
            favorite.subtitle?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canOpenArtwork) {
                OutlinedButton(onClick = onOpenArtwork) { Text(stringResource(Res.string.favorite_open)) }
            }
        }

        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(Res.string.favorite_note),
            minLines = 3,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(favorite.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
}
