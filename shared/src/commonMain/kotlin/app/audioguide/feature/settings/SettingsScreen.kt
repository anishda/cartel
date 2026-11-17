package app.audioguide.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.platform.systemLanguageTag
import app.audioguide.resources.Res
import app.audioguide.resources.settings_about_body
import app.audioguide.resources.settings_about_title
import app.audioguide.resources.settings_credits_data
import app.audioguide.resources.settings_credits_ocr
import app.audioguide.resources.settings_credits_title
import app.audioguide.resources.settings_credits_stories
import app.audioguide.resources.settings_credits_voice
import app.audioguide.resources.settings_preview_text
import app.audioguide.resources.settings_privacy_body
import app.audioguide.resources.settings_privacy_title
import app.audioguide.resources.settings_speech_preview
import app.audioguide.resources.settings_speech_title
import app.audioguide.resources.settings_speech_value
import app.audioguide.resources.settings_title
import app.audioguide.resources.voice_unsupported
import app.audioguide.settings.Settings
import app.audioguide.ui.components.ScreenHeader
import app.audioguide.ui.components.SectionLabel
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Pendant le glissement on suit le doigt ; la valeur n'est enregistrée qu'au relâchement.
    var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var noVoice by remember { mutableStateOf(false) }
    val french = systemLanguageTag().startsWith("fr")
    val previewText = stringResource(Res.string.settings_preview_text)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(Res.string.settings_title))

        SettingsCard {
            SectionLabel(stringResource(Res.string.settings_speech_title))
            Text(stringResource(Res.string.settings_speech_value, formatRate(rate, french)), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = rate,
                onValueChange = { rate = it },
                onValueChangeFinished = { viewModel.setSpeechRate(rate) },
                valueRange = Settings.MIN_RATE..Settings.MAX_RATE,
            )
            Button(onClick = {
                viewModel.setSpeechRate(rate)
                noVoice = !viewModel.preview(previewText, if (french) "fr-FR" else "en-US")
            }) { Text(stringResource(Res.string.settings_speech_preview)) }
            if (noVoice) {
                Text(stringResource(Res.string.voice_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        SettingsCard {
            VoiceSettings(viewModel, settings.preferredVoices)
        }

        SettingsCard {
            SectionLabel(stringResource(Res.string.settings_about_title))
            Text(stringResource(Res.string.settings_about_body, viewModel.appInfo.versionName.ifEmpty { "—" }), style = MaterialTheme.typography.bodyMedium)
        }

        SettingsCard {
            SectionLabel(stringResource(Res.string.settings_credits_title))
            Text(stringResource(Res.string.settings_credits_data), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.settings_credits_ocr), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.settings_credits_stories), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.settings_credits_voice), style = MaterialTheme.typography.bodyMedium)
        }

        SettingsCard {
            SectionLabel(stringResource(Res.string.settings_privacy_title))
            Text(stringResource(Res.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** « 1,2 » en français, « 1.2 » ailleurs. */
internal fun formatRate(rate: Float, french: Boolean): String {
    val tenths = (rate * 10).roundToInt()
    return "${tenths / 10}${if (french) "," else "."}${tenths % 10}"
}
