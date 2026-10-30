package app.audioguide.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.audioguide.domain.VoiceOption
import app.audioguide.domain.VoiceQuality
import app.audioguide.feature.scan.speechLanguages
import app.audioguide.platform.systemLanguageTag
import app.audioguide.resources.Res
import app.audioguide.resources.settings_voice_auto
import app.audioguide.resources.settings_voice_hint
import app.audioguide.resources.settings_voice_listen
import app.audioguide.resources.settings_voice_network
import app.audioguide.resources.settings_voice_none
import app.audioguide.resources.settings_voice_title
import app.audioguide.resources.voice_quality_enhanced
import app.audioguide.resources.voice_quality_premium
import app.audioguide.resources.voice_quality_standard
import app.audioguide.ui.components.SectionLabel
import org.jetbrains.compose.resources.stringResource

/** Une phrase d'essai dans chaque langue : on juge une voix en l'écoutant dire autre chose que du français. */
private val voiceSamples = mapOf(
    "fr" to "Ce tableau a été peint en mille huit cent quatre-vingt-six. Il montre une femme au bord de la mer.",
    "en" to "This painting was completed in 1886. It shows a woman standing by the sea.",
    "es" to "Este cuadro fue pintado en 1886. Representa a una mujer a la orilla del mar.",
    "it" to "Questo dipinto fu realizzato nel 1886. Raffigura una donna in riva al mare.",
    "de" to "Dieses Gemälde entstand im Jahr 1886. Es zeigt eine Frau am Meer.",
    "nl" to "Dit schilderij is in 1886 voltooid. Het toont een vrouw aan zee.",
)

/** Choix de la voix par langue : « automatique », ou une des voix installées, avec leur qualité. */
@Composable
fun VoiceSettings(viewModel: SettingsViewModel, preferred: Map<String, String>) {
    val ready by viewModel.engineReady.collectAsStateWithLifecycle()
    var language by remember {
        val system = systemLanguageTag().substringBefore('-')
        mutableStateOf(speechLanguages.firstOrNull { it.tag.substringBefore('-') == system }?.tag ?: "fr-FR")
    }
    val voices = remember(ready, language) { viewModel.voices(language) }
    val selected = preferred[language]?.takeIf { id -> voices.any { it.id == id } }

    SectionLabel(stringResource(Res.string.settings_voice_title))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        speechLanguages.forEach {
            FilterChip(selected = language == it.tag, onClick = { language = it.tag }, label = { Text(it.label) })
        }
    }

    if (voices.isEmpty()) {
        Text(stringResource(Res.string.settings_voice_none), style = MaterialTheme.typography.bodyMedium)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            VoiceRow(
                title = stringResource(Res.string.settings_voice_auto),
                subtitle = null,
                selected = selected == null,
                onSelect = { viewModel.setPreferredVoice(language, null) },
                onListen = null,
            )
            voices.forEach { voice ->
                VoiceRow(
                    title = voice.name,
                    subtitle = describe(voice),
                    selected = voice.id == selected,
                    onSelect = { viewModel.setPreferredVoice(language, voice.id) },
                    onListen = { viewModel.preview(voiceSamples[language.substringBefore('-')].orEmpty(), language, voice.id) },
                )
            }
        }
    }
    Text(
        stringResource(Res.string.settings_voice_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun describe(voice: VoiceOption): String {
    val quality = stringResource(
        when (voice.quality) {
            VoiceQuality.Premium -> Res.string.voice_quality_premium
            VoiceQuality.Enhanced -> Res.string.voice_quality_enhanced
            VoiceQuality.Standard -> Res.string.voice_quality_standard
        },
    )
    return if (voice.needsNetwork) "$quality · ${stringResource(Res.string.settings_voice_network)}" else quality
}

@Composable
private fun VoiceRow(title: String, subtitle: String?, selected: Boolean, onSelect: () -> Unit, onListen: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onListen != null) OutlinedButton(onClick = onListen) { Text(stringResource(Res.string.settings_voice_listen)) }
    }
}
