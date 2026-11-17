package app.audioguide.feature.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.audioguide.catalog.ArtworkMatch
import app.audioguide.domain.VoiceStatus
import app.audioguide.platform.rememberPhotoImporter
import app.audioguide.resources.Res
import app.audioguide.ui.components.AppTextField
import app.audioguide.resources.favorite_add
import app.audioguide.resources.favorite_remove
import app.audioguide.resources.scan_bilingual
import app.audioguide.resources.scan_clear
import app.audioguide.resources.scan_failed_empty
import app.audioguide.resources.scan_failed_generic
import app.audioguide.resources.scan_idle
import app.audioguide.resources.scan_import
import app.audioguide.resources.scan_keep
import app.audioguide.resources.scan_kept
import app.audioguide.resources.scan_language
import app.audioguide.resources.scan_live
import app.audioguide.resources.scan_match_others
import app.audioguide.resources.scan_match_title
import app.audioguide.resources.scan_recognizing
import app.audioguide.resources.scan_result_title
import app.audioguide.resources.scan_show_clean
import app.audioguide.resources.scan_show_raw
import app.audioguide.resources.scan_text_label
import app.audioguide.resources.scan_subtitle
import app.audioguide.resources.scan_title
import app.audioguide.resources.voice_checking
import app.audioguide.resources.voice_download
import app.audioguide.resources.voice_ready
import app.audioguide.resources.voice_unsupported
import app.audioguide.ui.AppIcons
import app.audioguide.ui.components.ListenButton
import app.audioguide.resources.scan_translate
import app.audioguide.resources.scan_translate_failed
import app.audioguide.resources.scan_translate_hint
import app.audioguide.resources.scan_translating
import app.audioguide.resources.scan_translation_title
import app.audioguide.ui.components.ScreenHeader
import app.audioguide.ui.components.SectionLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Au-delà, l'œuvre est sûre : inventaire (100) plus titre ou artiste. */
private const val DECISIVE_SCORE = 150

@Composable
fun ScanScreen(
    onLiveScan: () -> Unit,
    onOpenArtwork: (museumId: String, reference: String) -> Unit,
    session: ScanSession = koinInject(),
) {
    val state by session.state.collectAsStateWithLifecycle()
    val importPhoto = rememberPhotoImporter(session::onImage)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(Res.string.scan_title), subtitle = stringResource(Res.string.scan_subtitle))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onLiveScan, modifier = Modifier.weight(1f)) {
                Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.scan_live))
            }
            FilledTonalButton(onClick = importPhoto, modifier = Modifier.weight(1f)) {
                Icon(AppIcons.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.scan_import))
            }
        }

        LanguagePicker(
            selected = state.languageTag,
            voiceStatus = state.voiceStatus,
            bilingualTags = state.versions.map { it.languageTag },
            onSelect = session::setLanguage,
        )

        when (state.step) {
            ScanStep.Idle -> Text(
                stringResource(Res.string.scan_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanStep.Recognizing -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Text(stringResource(Res.string.scan_recognizing))
            }
            ScanStep.Failed -> Text(
                text = when (state.failure) {
                    ScanFailure.NoText -> stringResource(Res.string.scan_failed_empty)
                    else -> stringResource(Res.string.scan_failed_generic, state.failureDetail.orEmpty())
                },
                color = MaterialTheme.colorScheme.error,
            )
            ScanStep.Done -> ResultSection(state, session, onOpenArtwork)
        }
    }
}

@Composable
private fun LanguagePicker(selected: String, voiceStatus: VoiceStatus, bilingualTags: List<String>, onSelect: (String) -> Unit) {
    // Un cartel bilingue ne propose que ses langues ; sinon toutes les voix.
    val languages = if (bilingualTags.isEmpty()) speechLanguages else speechLanguages.filter { it.tag in bilingualTags }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(if (bilingualTags.isEmpty()) Res.string.scan_language else Res.string.scan_bilingual))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            languages.forEach { language ->
                FilterChip(
                    selected = selected == language.tag,
                    onClick = { onSelect(language.tag) },
                    label = { Text(language.label) },
                )
            }
        }
        Text(
            text = when (voiceStatus) {
                VoiceStatus.Checking -> stringResource(Res.string.voice_checking)
                VoiceStatus.Ready -> stringResource(Res.string.voice_ready)
                VoiceStatus.NeedsDownload -> stringResource(Res.string.voice_download)
                VoiceStatus.Unsupported -> stringResource(Res.string.voice_unsupported)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (voiceStatus == VoiceStatus.Ready || voiceStatus == VoiceStatus.Checking) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun ResultSection(
    state: ScanUiState,
    session: ScanSession,
    onOpenArtwork: (museumId: String, reference: String) -> Unit,
) {
    var showRaw by remember { mutableStateOf(false) }

    state.matches.firstOrNull()?.let { best ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(Res.string.scan_match_title))
            MatchCard(
                best,
                isFavorite = state.isFavorite(best.artwork),
                onToggleFavorite = { session.toggleFavorite(best.artwork) },
                onClick = { onOpenArtwork(best.artwork.museumId, best.artwork.reference) },
            )
            // Une correspondance appuyée par l'inventaire, le titre et l'artiste se suffit à elle-même.
            val others = if (best.score >= DECISIVE_SCORE) emptyList() else state.matches.drop(1)
            if (others.isNotEmpty()) {
                Text(
                    stringResource(Res.string.scan_match_others),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                others.forEach { other ->
                    MatchCard(
                        other,
                        compact = true,
                        isFavorite = state.isFavorite(other.artwork),
                        onToggleFavorite = { session.toggleFavorite(other.artwork) },
                        onClick = { onOpenArtwork(other.artwork.museumId, other.artwork.reference) },
                    )
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(Res.string.scan_result_title))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ListenButton(
                    isSpeaking = state.isSpeaking,
                    enabled = state.voiceStatus == VoiceStatus.Ready,
                    onPlay = session::speak,
                    onStop = session::stop,
                )
                OutlinedButton(onClick = { showRaw = !showRaw }) {
                    Text(stringResource(if (showRaw) Res.string.scan_show_clean else Res.string.scan_show_raw))
                }
            }
            if (showRaw) {
                Text(text = state.rawText, style = MaterialTheme.typography.bodyLarge)
            } else {
                // Le texte lu est modifiable : un mot mal reconnu se corrige avant l'écoute. Le champ garde sa propre
                // valeur (la session la reçoit en différé) ; il repart du texte de la session quand le cartel ou la
                // langue change.
                var edited by remember(state.rawText, state.languageTag) { mutableStateOf(state.cleanedText) }
                AppTextField(
                    value = edited,
                    onValueChange = {
                        edited = it
                        session.setText(it)
                    },
                    label = stringResource(Res.string.scan_text_label),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.canTranslate) TranslationSection(state, session)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = session::saveCartel, enabled = !state.cartelSaved) {
            Icon(if (state.cartelSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (state.cartelSaved) Res.string.scan_kept else Res.string.scan_keep))
        }
        TextButton(onClick = session::clear) { Text(stringResource(Res.string.scan_clear)) }
    }
    Spacer(Modifier.height(8.dp))
}

/** Traduction du cartel en français : modèle de langue téléchargé une fois, puis hors ligne. */
@Composable
private fun TranslationSection(state: ScanUiState, session: ScanSession) {
    when (val translation = state.translation) {
        TranslationState.None, TranslationState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = session::translate) { Text(stringResource(Res.string.scan_translate)) }
            Text(
                stringResource(if (translation == TranslationState.Failed) Res.string.scan_translate_failed else Res.string.scan_translate_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TranslationState.Working -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.scan_translating), style = MaterialTheme.typography.bodyMedium)
        }
        is TranslationState.Done -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(Res.string.scan_translation_title))
                Text(translation.text, style = MaterialTheme.typography.bodyLarge)
                ListenButton(
                    isSpeaking = state.isSpeaking,
                    enabled = true,
                    onPlay = session::speakTranslation,
                    onStop = session::stop,
                )
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: ArtworkMatch,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (compact) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    match.artwork.title,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                )
                val subtitle = listOfNotNull(match.artwork.artist, match.artwork.dateText).joinToString(" · ")
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) Res.string.favorite_remove else Res.string.favorite_add),
                )
            }
            Icon(AppIcons.Chevron, contentDescription = null)
        }
    }
}
