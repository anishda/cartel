package app.audioguide.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.platform.NaturalVoiceState
import app.audioguide.resources.Res
import app.audioguide.resources.natural_voice_delete
import app.audioguide.resources.natural_voice_download
import app.audioguide.resources.natural_voice_failed
import app.audioguide.resources.natural_voice_hint
import app.audioguide.resources.natural_voice_title
import app.audioguide.resources.natural_voice_use
import app.audioguide.ui.components.SectionLabel
import org.jetbrains.compose.resources.stringResource

/** Voix naturelle (Piper) : téléchargement du modèle, puis choix de l'utiliser ou non. */
@Composable
fun NaturalVoiceSettings(viewModel: SettingsViewModel, enabled: Boolean) {
    val state by viewModel.naturalVoice.state.collectAsStateWithLifecycle()

    SectionLabel(stringResource(Res.string.natural_voice_title))
    Text(stringResource(Res.string.natural_voice_hint), style = MaterialTheme.typography.bodyMedium)
    when (val current = state) {
        NaturalVoiceState.NotInstalled, NaturalVoiceState.Failed -> {
            Button(onClick = viewModel::downloadNaturalVoice) {
                Text(stringResource(Res.string.natural_voice_download, viewModel.naturalVoice.downloadSizeMb))
            }
            if (current == NaturalVoiceState.Failed) {
                Text(stringResource(Res.string.natural_voice_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        is NaturalVoiceState.Downloading -> LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
        NaturalVoiceState.Ready -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.natural_voice_use), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = viewModel::setNaturalVoice)
            }
            OutlinedButton(onClick = viewModel::deleteNaturalVoice) { Text(stringResource(Res.string.natural_voice_delete)) }
        }
    }
}
