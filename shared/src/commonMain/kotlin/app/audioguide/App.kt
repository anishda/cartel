package app.audioguide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.packs.PackStore
import app.audioguide.settings.SettingsStore
import app.audioguide.ui.navigation.AppNavigation
import app.audioguide.ui.theme.AudioguideTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val store = koinInject<PackStore>()
    val settings = koinInject<SettingsStore>()
    val synthesizer = koinInject<SpeechSynthesizer>()

    // Ouvre les packs déjà installés avant la première recherche.
    LaunchedEffect(store) { store.refresh() }
    // Charge les réglages, puis applique la vitesse et les voix choisies dès qu'elles changent.
    LaunchedEffect(settings) {
        settings.load()
        var applied = emptySet<String>()
        settings.settings.collect { current ->
            synthesizer.setRate(current.speechRate)
            // Une voix retirée des réglages redevient « automatique ».
            (applied - current.preferredVoices.keys).forEach { synthesizer.setPreferredVoice(it, null) }
            current.preferredVoices.forEach { (tag, id) -> synthesizer.setPreferredVoice(tag, id) }
            applied = current.preferredVoices.keys
        }
    }

    AudioguideTheme {
        AppNavigation()
    }
}
