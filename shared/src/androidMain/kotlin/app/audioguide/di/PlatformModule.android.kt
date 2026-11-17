package app.audioguide.di

import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.TextRecognizer
import app.audioguide.domain.TextTranslator
import app.audioguide.platform.AndroidSpeechSynthesizer
import app.audioguide.platform.AppInfo
import app.audioguide.platform.AppPaths
import app.audioguide.platform.MlKitTextRecognizer
import app.audioguide.platform.MlKitTextTranslator
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<TextRecognizer> { MlKitTextRecognizer() }
    single<TextTranslator> { MlKitTextTranslator() }
    single<SpeechSynthesizer> { AndroidSpeechSynthesizer(androidContext()) }
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
