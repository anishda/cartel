package app.audioguide.di

import app.audioguide.catalog.Catalog
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.TextTranslator
import app.audioguide.platform.AndroidSpeechSynthesizer
import app.audioguide.platform.AppInfo
import app.audioguide.platform.AppPaths
import app.audioguide.platform.MlKitTextRecognizer
import app.audioguide.platform.MlKitTextTranslator
import app.audioguide.platform.NaturalVoice
import app.audioguide.platform.PiperSpeechSynthesizer
import app.audioguide.platform.PiperVoiceModel
import app.audioguide.settings.SettingsStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TextRecognizer> { MlKitTextRecognizer() }
    single<TextTranslator> { MlKitTextTranslator() }
    single { PiperVoiceModel(androidContext().filesDir, get<HttpClient>()) }
    single<NaturalVoice> { get<PiperVoiceModel>() }
    // Voix neuronale quand elle est installée et que le pack donne les phonèmes du texte, sinon voix du système.
    single<SpeechSynthesizer> {
        val catalog = get<Catalog>()
        val settings = get<SettingsStore>()
        PiperSpeechSynthesizer(
            system = AndroidSpeechSynthesizer(androidContext()),
            model = get(),
            phonemes = catalog::phonemes,
            enabled = { settings.settings.value.naturalVoice },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single {
        val files = androidContext().filesDir
        AppPaths(
            packsDir = File(files, "packs").absolutePath,
            userDb = File(files, "user.db").absolutePath,
            settingsFile = File(files, "settings.json").absolutePath,
        )
    }
    single {
        val context = androidContext()
        AppInfo(context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
    }
    // L'adresse du serveur de packs vient de l'app (BuildConfig) : voir AudioguideApp.
}
